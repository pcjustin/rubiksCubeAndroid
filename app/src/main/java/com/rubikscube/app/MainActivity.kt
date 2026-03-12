package com.rubikscube.app

import android.os.Bundle
import android.widget.FrameLayout
import androidx.appcompat.app.AppCompatActivity
import com.rubikscube.app.databinding.ActivityMainBinding

/**
 * Main Activity: hosts the SceneView and the color-selection palette.
 *
 * Interaction:
 *   • Drag on the 3D view → orbit the camera around the cube
 *   • Tap a color button  → select that color
 *   • Tap a sticker       → paint it with the selected color
 */
class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding

    private val cubeState = RubiksCubeState()
    private val cubeScene = RubiksCubeScene(cubeState)

    private var selectedColor: StickerColor = StickerColor.WHITE

    // ── Lifecycle ─────────────────────────────────────────────────────────────

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupColorButtons()
        setupScene()
    }

    // ── Scene setup ───────────────────────────────────────────────────────────

    private fun setupScene() {
        // SceneView is a SurfaceView; wait for it to be attached before building geometry
        binding.sceneView.post {
            cubeScene.setup(binding.sceneView)

            // Handle single-tap: color the tapped sticker
            binding.sceneView.setOnGestureListener(
                onSingleTapConfirmed = { _, node ->
                    node?.let { tapped ->
                        val idx = cubeScene.stickerNodeMap[tapped]
                        if (idx != null) {
                            cubeScene.updateStickerColor(idx, selectedColor, binding.sceneView.engine)
                        }
                    }
                }
            )
        }
    }

    // ── Color palette ─────────────────────────────────────────────────────────

    private val colorButtons: List<Pair<FrameLayout, StickerColor>> by lazy {
        listOf(
            binding.btnWhite  to StickerColor.WHITE,
            binding.btnYellow to StickerColor.YELLOW,
            binding.btnRed    to StickerColor.RED,
            binding.btnOrange to StickerColor.ORANGE,
            binding.btnBlue   to StickerColor.BLUE,
            binding.btnGreen  to StickerColor.GREEN,
            binding.btnGray   to StickerColor.UNSET
        )
    }

    private fun setupColorButtons() {
        for ((button, color) in colorButtons) {
            button.setOnClickListener { selectColor(color) }
        }
        // Default selection
        selectColor(StickerColor.WHITE)
    }

    private fun selectColor(color: StickerColor) {
        selectedColor = color
        // Update visual selection state on buttons
        for ((button, btnColor) in colorButtons) {
            button.isSelected = (btnColor == color)
        }
        binding.tvSelectedColor.text = "已選: ${color.displayName}"
    }
}
