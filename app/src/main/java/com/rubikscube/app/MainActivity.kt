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
    private var solutionMoves: List<String> = emptyList()
    private var currentStepIndex: Int = 0
    private var isAnimatingStep: Boolean = false

    // ── Lifecycle ─────────────────────────────────────────────────────────────

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // Initialize min2phase tables in the background (~200 ms)
        Thread { RubiksSolver.init() }.start()

        setupColorButtons()
        setupSolveButton()
        setupStepButtons()
        setupScene()
    }

    // ── 3D scene ──────────────────────────────────────────────────────────────

    private fun setupScene() {
        binding.sceneView.post {
            cubeScene.setup(binding.sceneView)

            binding.sceneView.setOnGestureListener(
                onSingleTapConfirmed = { _, node ->
                    if (isAnimatingStep) return@setOnGestureListener
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
            if (isAnimatingStep) return@setOnClickListener
            startSolving()
        }
    }

    private fun setupStepButtons() {
        binding.btnPrevStep.setOnClickListener { stepBackward() }
        binding.btnNextStep.setOnClickListener { stepForward() }
        refreshStepControls()
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
        solutionMoves = moves.split(Regex("\\s+")).filter { it.isNotBlank() }
        currentStepIndex = 0
        binding.tvSolution.text = "（$count 步）$moves"
        binding.solutionScroll.visibility = View.VISIBLE
        binding.stepControls.visibility = View.VISIBLE
        refreshStepControls()
    }

    private fun hideSolution() {
        solutionMoves = emptyList()
        currentStepIndex = 0
        isAnimatingStep = false
        binding.solutionScroll.visibility = View.GONE
        binding.stepControls.visibility = View.GONE
        refreshStepControls()
    }

    private fun stepForward() {
        if (isAnimatingStep || currentStepIndex >= solutionMoves.size) return
        val move = solutionMoves[currentStepIndex]
        runStep(move) {
            currentStepIndex += 1
            refreshStepControls()
        }
    }

    private fun stepBackward() {
        if (isAnimatingStep || currentStepIndex <= 0) return
        val move = RubiksCubeState.inverseMove(solutionMoves[currentStepIndex - 1])
        runStep(move) {
            currentStepIndex -= 1
            refreshStepControls()
        }
    }

    private fun runStep(move: String, onFinished: () -> Unit) {
        isAnimatingStep = true
        refreshStepControls()
        cubeScene.animateMove(binding.sceneView, move) {
            isAnimatingStep = false
            onFinished()
        }
    }

    private fun refreshStepControls() {
        binding.btnPrevStep.isEnabled = !isAnimatingStep && currentStepIndex > 0
        binding.btnNextStep.isEnabled = !isAnimatingStep && currentStepIndex < solutionMoves.size
        val currentMove = solutionMoves.getOrNull(currentStepIndex)
        binding.tvStepIndicator.text = if (solutionMoves.isEmpty()) {
            "步驟 0 / 0"
        } else {
            "步驟 $currentStepIndex / ${solutionMoves.size}" +
                if (currentMove != null) "  ·  下一步 $currentMove" else "  ·  已完成"
        }
    }

    private fun updateStatus(text: String) {
        binding.tvStatus.text = text
    }
}
