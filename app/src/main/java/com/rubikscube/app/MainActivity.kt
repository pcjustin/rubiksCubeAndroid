package com.rubikscube.app

import android.os.Bundle
import android.view.View
import android.widget.FrameLayout
import androidx.appcompat.app.AppCompatActivity
import com.rubikscube.app.databinding.ActivityMainBinding

/**
 * Main Activity.
 *
 * Interaction:
 *   • Drag 3D view  → orbit camera
 *   • Tap color btn → select color
 *   • Tap sticker   → paint with selected color
 *   • Tap 求解      → run min2phase solver on background thread
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

        // Initialize min2phase tables in the background (~200 ms)
        Thread { RubiksSolver.init() }.start()

        setupColorButtons()
        setupSolveButton()
        setupScene()
    }

    // ── 3D scene ──────────────────────────────────────────────────────────────

    private fun setupScene() {
        binding.sceneView.post {
            cubeScene.setup(binding.sceneView)

            binding.sceneView.setOnGestureListener(
                onSingleTapConfirmed = { _, node ->
                    node?.let { tapped ->
                        val idx = cubeScene.stickerNodeMap[tapped]
                        if (idx != null) {
                            cubeScene.updateStickerColor(
                                idx, selectedColor, binding.sceneView.engine
                            )
                            // Clear previous solution whenever cube changes
                            hideSolution()
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
        selectColor(StickerColor.WHITE)
    }

    private fun selectColor(color: StickerColor) {
        selectedColor = color
        for ((button, btnColor) in colorButtons) {
            button.isSelected = (btnColor == color)
        }
        updateStatus("已選: ${color.displayName}")
    }

    // ── Solve ─────────────────────────────────────────────────────────────────

    private fun setupSolveButton() {
        binding.btnSolve.setOnClickListener {
            startSolving()
        }
    }

    private fun startSolving() {
        binding.btnSolve.isEnabled = false
        updateStatus("求解中…")
        hideSolution()

        Thread {
            val result = RubiksSolver.solve(cubeState)
            runOnUiThread {
                binding.btnSolve.isEnabled = true
                when (result) {
                    is RubiksSolver.Result.Success -> {
                        showSolution(result.moves, result.count)
                        updateStatus("已選: ${selectedColor.displayName}  ─  共 ${result.count} 步")
                    }
                    is RubiksSolver.Result.Invalid -> {
                        updateStatus("⚠ ${result.reason}")
                    }
                    RubiksSolver.Result.NotReady -> {
                        updateStatus("⚠ 求解器初始化中，請稍後再試")
                    }
                }
            }
        }.start()
    }

    private fun showSolution(moves: String, count: Int) {
        binding.tvSolution.text = "（$count 步）$moves"
        binding.solutionScroll.visibility = View.VISIBLE
    }

    private fun hideSolution() {
        binding.solutionScroll.visibility = View.GONE
    }

    private fun updateStatus(text: String) {
        binding.tvStatus.text = text
    }
}
