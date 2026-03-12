package com.rubikscube.app

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.ValueAnimator
import android.graphics.Color
import android.view.animation.LinearInterpolator
import com.google.android.filament.MaterialInstance
import com.google.android.filament.utils.Manipulator
import dev.romainguy.kotlin.math.Float3
import dev.romainguy.kotlin.math.Quaternion
import io.github.sceneview.SceneView
import io.github.sceneview.node.CubeNode
import io.github.sceneview.node.Node
import kotlin.math.cos
import kotlin.math.sin

/**
 * Manages the 3D Rubik's cube scene.
 *
 * Scene objects:
 *   - 26 black cubie boxes (structural core)
 *   - 54 thin colored sticker nodes (one per sticker)
 *
 * Coordinate system (Y-up, Z-forward):
 *   - Cube centered at origin
 *   - Each cubie center spaced GRID apart along each axis
 *
 * Face index → direction:
 *   0=U(+Y)  1=R(+X)  2=F(+Z)  3=D(-Y)  4=L(-X)  5=B(-Z)
 */
class RubiksCubeScene(private val cubeState: RubiksCubeState) {

    companion object {
        const val GRID       = 0.10f   // cubie center spacing   (10 cm)
        const val CUBIE_SIZE = 0.088f  // cubie edge length       (8.8 cm)
        const val STK_SIZE   = 0.080f  // sticker face size       (8.0 cm)
        const val STK_DEPTH  = 0.008f  // sticker thickness       (0.8 cm)

        /** Distance from cubie center to the outer sticker face. */
        val STK_OFFSET = CUBIE_SIZE / 2f + STK_DEPTH / 2f  // ≈ 0.048 m

        const val FACE_U = 0
        const val FACE_R = 1
        const val FACE_F = 2
        const val FACE_D = 3
        const val FACE_L = 4
        const val FACE_B = 5
    }

    /** Node → flat sticker index (face*9 + row*3 + col), used for tap detection. */
    val stickerNodeMap = HashMap<Node, Int>()

    private val stickerNodes = arrayOfNulls<CubeNode>(54)
    private val colorMaterials = HashMap<StickerColor, MaterialInstance>()
    private var activeAnimator: ValueAnimator? = null

    // ── Public API ────────────────────────────────────────────────────────────

    /**
     * Build the scene. Call from Activity.onCreate after the view is posted
     * (or from SceneView's onLayout callback).
     */
    fun setup(sceneView: SceneView) {
        val engine         = sceneView.engine
        val materialLoader = sceneView.materialLoader

        // Pre-create one MaterialInstance per StickerColor
        // Use high roughness to reduce specular reflection and make colors uniform
        for (sc in StickerColor.entries) {
            colorMaterials[sc] = materialLoader.createColorInstance(
                sc.androidColor,
                metallic = 0.0f,
                roughness = 0.9f
            )
        }
        val blackMat = materialLoader.createColorInstance(Color.parseColor("#111111"))

        // ── 26 cubie bodies ───────────────────────────────────────────────────
        for (cx in -1..1) for (cy in -1..1) for (cz in -1..1) {
            if (cx == 0 && cy == 0 && cz == 0) continue  // hidden center, skip
            val node = CubeNode(
                engine           = engine,
                size             = Float3(CUBIE_SIZE, CUBIE_SIZE, CUBIE_SIZE),
                center           = Float3(0f, 0f, 0f),
                materialInstance = blackMat
            ).apply {
                position = Float3(cx * GRID, cy * GRID, cz * GRID)
            }
            sceneView.addChildNode(node)
        }

        // ── 54 sticker nodes ──────────────────────────────────────────────────
        for (face in 0..5) {
            for (row in 0..2) {
                for (col in 0..2) {
                    val idx   = face * 9 + row * 3 + col
                    val color = cubeState.getSticker(face, row, col)
                    val mat   = colorMaterials[color]!!

                    val (pos, quat) = stickerTransform(face, row, col)

                    val node = CubeNode(
                        engine           = engine,
                        size             = Float3(STK_SIZE, STK_SIZE, STK_DEPTH),
                        center           = Float3(0f, 0f, 0f),
                        materialInstance = mat
                    ).apply {
                        position  = pos
                        quaternion = quat
                    }

                    stickerNodes[idx]  = node
                    stickerNodeMap[node] = idx
                    sceneView.addChildNode(node)
                }
            }
        }

        setupCamera(sceneView)
    }

    /**
     * Change a sticker's color both in the logical state and in the 3D scene.
     * Uses Filament's RenderableManager to swap the material instance at runtime.
     */
    fun updateStickerColor(stickerIndex: Int, color: StickerColor, engine: com.google.android.filament.Engine) {
        val (face, row, col) = cubeState.indexToFaceRowCol(stickerIndex)
        cubeState.setSticker(face, row, col, color)

        val node   = stickerNodes[stickerIndex] ?: return
        val newMat = colorMaterials[color]      ?: return

        val rm = engine.renderableManager
        val ri = rm.getInstance(node.entity)
        if (ri != 0) {
            rm.setMaterialInstanceAt(ri, 0, newMat)
        }
    }

    fun syncWithState(engine: com.google.android.filament.Engine) {
        val rm = engine.renderableManager
        for (idx in 0 until 54) {
            val node = stickerNodes[idx] ?: continue
            val (face, row, col) = cubeState.indexToFaceRowCol(idx)
            val color = cubeState.getSticker(face, row, col)
            val mat = colorMaterials[color] ?: continue
            val ri = rm.getInstance(node.entity)
            if (ri != 0) {
                rm.setMaterialInstanceAt(ri, 0, mat)
            }
            val (position, quaternion) = stickerTransform(face, row, col)
            node.position = position
            node.quaternion = quaternion
        }
    }

    fun animateMove(
        sceneView: SceneView,
        move: String,
        durationMs: Long = 280L,
        onFinished: () -> Unit
    ) {
        activeAnimator?.cancel()
        val parsed = RubiksCubeState.parseMove(move)
        if (parsed == null) {
            onFinished()
            return
        }

        val rotating = mutableListOf<AnimatedSticker>()
        for (idx in 0 until 54) {
            val (face, row, col) = cubeState.indexToFaceRowCol(idx)
            val vector = stickerVector(face, row, col)
            if (isOnMoveLayer(vector, parsed.face)) {
                val node = stickerNodes[idx] ?: continue
                rotating += AnimatedSticker(node, node.position, node.quaternion)
            }
        }

        if (rotating.isEmpty()) {
            cubeState.applyMove(move)
            syncWithState(sceneView.engine)
            onFinished()
            return
        }

        val axis = rotationAxis(parsed.face)
        val totalAngle = rotationAngleDegrees(move)

        activeAnimator = ValueAnimator.ofFloat(0f, 1f).apply {
            duration = durationMs
            interpolator = LinearInterpolator()
            addUpdateListener { animator ->
                val fraction = animator.animatedFraction
                val stepRotation = axisAngle(axis.x, axis.y, axis.z, totalAngle * fraction)
                for (item in rotating) {
                    item.node.position = rotatePoint(item.startPosition, axis, totalAngle * fraction)
                    item.node.quaternion = normalizedLerp(item.startQuaternion, multiply(stepRotation, item.startQuaternion), fraction)
                }
            }
            var wasCanceled = false
            addListener(object : AnimatorListenerAdapter() {
                override fun onAnimationEnd(animation: Animator) {
                    activeAnimator = null
                    if (!wasCanceled) {
                        cubeState.applyMove(move)
                        syncWithState(sceneView.engine)
                        onFinished()
                    }
                }

                override fun onAnimationCancel(animation: Animator) {
                    wasCanceled = true
                    activeAnimator = null
                    syncWithState(sceneView.engine)
                }
            })
            start()
        }
    }

    // ── Camera setup ──────────────────────────────────────────────────────────

    private fun setupCamera(sceneView: SceneView) {
        // Replace the default (far-away) camera with one close to the small cube.
        // viewport() must match the current view size so orbit gestures scale correctly.
        // Use symmetric position for more uniform lighting on all faces
        sceneView.cameraManipulator = Manipulator.Builder()
            .targetPosition(0f, 0f, 0f)
            .viewport(maxOf(sceneView.width, 1), maxOf(sceneView.height, 1))
            .orbitHomePosition(0.60f, 0.60f, 0.60f)
            .zoomSpeed(0f)
            .build(Manipulator.Mode.ORBIT)
    }

    // ── Sticker 3D transform ──────────────────────────────────────────────────

    /**
     * Returns (position, quaternion) for the sticker at [face][row][col].
     *
     * Base geometry: slab of dimensions (STK_SIZE × STK_SIZE × STK_DEPTH)
     * whose broad face points in the local +Z direction.
     *
     * Rotation applied per face so the broad face points outward:
     *   F (+Z) → identity
     *   B (-Z) → +180° around Y
     *   U (+Y) →  -90° around X
     *   D (-Y) →  +90° around X
     *   R (+X) →  +90° around Y
     *   L (-X) →  -90° around Y
     */
    private fun stickerTransform(face: Int, row: Int, col: Int): Pair<Float3, Quaternion> {
        val g = GRID
        val o = STK_OFFSET

        return when (face) {
            FACE_F -> Pair(
                Float3((col - 1) * g, (1 - row) * g, g + o),
                identity()
            )
            FACE_B -> Pair(
                Float3((1 - col) * g, (1 - row) * g, -(g + o)),
                axisAngle(0f, 1f, 0f, 180f)
            )
            FACE_U -> Pair(
                Float3((col - 1) * g, g + o, (row - 1) * g),
                axisAngle(1f, 0f, 0f, -90f)
            )
            FACE_D -> Pair(
                Float3((col - 1) * g, -(g + o), (1 - row) * g),
                axisAngle(1f, 0f, 0f, 90f)
            )
            FACE_R -> Pair(
                Float3(g + o, (1 - row) * g, (1 - col) * g),
                axisAngle(0f, 1f, 0f, 90f)
            )
            FACE_L -> Pair(
                Float3(-(g + o), (1 - row) * g, (col - 1) * g),
                axisAngle(0f, 1f, 0f, -90f)
            )
            else -> Pair(Float3(0f, 0f, 0f), identity())
        }
    }

    // ── Quaternion helpers ────────────────────────────────────────────────────

    private fun identity() = Quaternion(0f, 0f, 0f, 1f)

    /** Build a unit quaternion from axis (ax,ay,az) and angle in degrees. */
    private fun axisAngle(ax: Float, ay: Float, az: Float, deg: Float): Quaternion {
        val rad = Math.toRadians(deg.toDouble()).toFloat()
        val s   = sin(rad / 2f)
        val c   = cos(rad / 2f)
        return Quaternion(ax * s, ay * s, az * s, c)
    }

    private fun rotationAxis(face: Int): Float3 =
        when (face) {
            FACE_U -> Float3(0f, 1f, 0f)
            FACE_R -> Float3(1f, 0f, 0f)
            FACE_F -> Float3(0f, 0f, 1f)
            FACE_D -> Float3(0f, -1f, 0f)
            FACE_L -> Float3(-1f, 0f, 0f)
            FACE_B -> Float3(0f, 0f, -1f)
            else -> Float3(0f, 0f, 1f)
        }

    private fun rotationAngleDegrees(move: String): Float {
        val base = if (move.endsWith("'")) 90f else -90f
        return if (move.endsWith("2")) 180f else base
    }

    private fun isOnMoveLayer(vector: StickerVector, face: Int): Boolean =
        when (face) {
            FACE_U -> vector.y == 1
            FACE_R -> vector.x == 1
            FACE_F -> vector.z == 1
            FACE_D -> vector.y == -1
            FACE_L -> vector.x == -1
            FACE_B -> vector.z == -1
            else -> false
        }

    private fun stickerVector(face: Int, row: Int, col: Int): StickerVector =
        when (face) {
            FACE_U -> StickerVector(col - 1, 1, row - 1)
            FACE_R -> StickerVector(1, 1 - row, 1 - col)
            FACE_F -> StickerVector(col - 1, 1 - row, 1)
            FACE_D -> StickerVector(col - 1, -1, 1 - row)
            FACE_L -> StickerVector(-1, 1 - row, col - 1)
            FACE_B -> StickerVector(1 - col, 1 - row, -1)
            else -> StickerVector(0, 0, 0)
        }

    private fun rotatePoint(point: Float3, axis: Float3, angleDeg: Float): Float3 {
        val q = axisAngle(axis.x, axis.y, axis.z, angleDeg)
        return rotate(point, q)
    }

    private fun rotate(point: Float3, rotation: Quaternion): Float3 {
        val x = rotation.x
        val y = rotation.y
        val z = rotation.z
        val w = rotation.w

        val ix =  w * point.x + y * point.z - z * point.y
        val iy =  w * point.y + z * point.x - x * point.z
        val iz =  w * point.z + x * point.y - y * point.x
        val iw = -x * point.x - y * point.y - z * point.z

        return Float3(
            ix * w + iw * -x + iy * -z - iz * -y,
            iy * w + iw * -y + iz * -x - ix * -z,
            iz * w + iw * -z + ix * -y - iy * -x
        )
    }

    private fun multiply(a: Quaternion, b: Quaternion): Quaternion =
        Quaternion(
            a.w * b.x + a.x * b.w + a.y * b.z - a.z * b.y,
            a.w * b.y - a.x * b.z + a.y * b.w + a.z * b.x,
            a.w * b.z + a.x * b.y - a.y * b.x + a.z * b.w,
            a.w * b.w - a.x * b.x - a.y * b.y - a.z * b.z
        )

    private fun normalizedLerp(from: Quaternion, to: Quaternion, fraction: Float): Quaternion {
        val adjustedTo = if (dot(from, to) < 0f) {
            Quaternion(-to.x, -to.y, -to.z, -to.w)
        } else {
            to
        }
        val x = from.x + (adjustedTo.x - from.x) * fraction
        val y = from.y + (adjustedTo.y - from.y) * fraction
        val z = from.z + (adjustedTo.z - from.z) * fraction
        val w = from.w + (adjustedTo.w - from.w) * fraction
        val len = kotlin.math.sqrt(x * x + y * y + z * z + w * w).coerceAtLeast(1e-6f)
        return Quaternion(x / len, y / len, z / len, w / len)
    }

    private fun dot(a: Quaternion, b: Quaternion): Float =
        a.x * b.x + a.y * b.y + a.z * b.z + a.w * b.w

    private data class AnimatedSticker(
        val node: CubeNode,
        val startPosition: Float3,
        val startQuaternion: Quaternion
    )

    private data class StickerVector(val x: Int, val y: Int, val z: Int)
}
