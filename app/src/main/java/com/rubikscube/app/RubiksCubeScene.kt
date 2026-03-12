package com.rubikscube.app

import android.graphics.Color
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

    // ── Public API ────────────────────────────────────────────────────────────

    /**
     * Build the scene. Call from Activity.onCreate after the view is posted
     * (or from SceneView's onLayout callback).
     */
    fun setup(sceneView: SceneView) {
        val engine         = sceneView.engine
        val materialLoader = sceneView.materialLoader

        // Pre-create one MaterialInstance per StickerColor
        for (sc in StickerColor.entries) {
            colorMaterials[sc] = materialLoader.createColorInstance(sc.androidColor)
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

    // ── Camera setup ──────────────────────────────────────────────────────────

    private fun setupCamera(sceneView: SceneView) {
        // Replace the default (far-away) camera with one close to the small cube.
        // viewport() must match the current view size so orbit gestures scale correctly.
        sceneView.cameraManipulator = Manipulator.Builder()
            .targetPosition(0f, 0f, 0f)
            .viewport(maxOf(sceneView.width, 1), maxOf(sceneView.height, 1))
            .orbitHomePosition(0.28f, 0.22f, 0.40f)
            .zoomSpeed(0.004f)
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
}
