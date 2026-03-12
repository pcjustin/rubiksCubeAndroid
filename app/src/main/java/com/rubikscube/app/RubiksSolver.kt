package com.rubikscube.app

import cs.min2phase.Search
import cs.min2phase.Tools

/**
 * Kotlin wrapper for the min2phase two-phase Rubik's cube solver.
 *
 * Usage:
 *   1. Call [init] once on a background thread at app start (~200 ms).
 *   2. Call [solve] on a background thread when the user requests a solution.
 */
object RubiksSolver {

    @Volatile private var initialized = false

    /** Initialize lookup tables. Must be called before [solve]. Takes ~200 ms. */
    fun init() {
        if (!initialized) {
            Search.init()
            initialized = true
        }
    }

    // ── Result type ───────────────────────────────────────────────────────────

    sealed class Result {
        /** Solution found. [moves] is e.g. "U R2 F' B …", [count] is the move count. */
        data class Success(val moves: String, val count: Int) : Result()
        /** The cube state is logically invalid. [reason] describes the problem. */
        data class Invalid(val reason: String) : Result()
        /** Solver not yet initialized. */
        object NotReady : Result()
    }

    // ── Public API ────────────────────────────────────────────────────────────

    /**
     * Attempt to solve [cubeState].
     * This call is blocking and can take up to several seconds for the first call
     * while lookup tables finish initializing.
     */
    fun solve(cubeState: RubiksCubeState): Result {
        if (!initialized) return Result.NotReady

        // Check for un-filled stickers
        for (face in 0..5) for (row in 0..2) for (col in 0..2) {
            if (cubeState.stickers[face][row][col] == StickerColor.UNSET) {
                return Result.Invalid("請先填入所有 54 個格子的顏色")
            }
        }

        val facelets = toFaceletString(cubeState)
            ?: return Result.Invalid("顏色分配無效：六個面的中心必須各是不同顏色")

        when (val code = Tools.verify(facelets)) {
            0  -> { /* valid */ }
            -1 -> return Result.Invalid("每種顏色必須恰好出現 9 次")
            -2 -> return Result.Invalid("邊塊配置錯誤（某條邊不存在）")
            -3 -> return Result.Invalid("邊塊翻轉錯誤")
            -4 -> return Result.Invalid("角塊配置錯誤（某個角不存在）")
            -5 -> return Result.Invalid("角塊扭轉錯誤")
            -6 -> return Result.Invalid("奇偶性錯誤（兩個角或兩條邊需要互換）")
            else -> return Result.Invalid("魔方狀態無效（錯誤碼 $code）")
        }

        val raw = Search().solution(facelets, 21, 500_000L, 0, 0)

        return if (raw.isNotEmpty() && raw[0].isLetter()) {
            val moves = raw.trim()
            val count = moves.split(" ").count { it.isNotBlank() }
            Result.Success(moves, count)
        } else {
            Result.Invalid("求解失敗（min2phase 回傳：$raw）")
        }
    }

    // ── Internal: state → facelet string ─────────────────────────────────────

    /**
     * Converts [state] to min2phase's 54-char facelet string.
     *
     * Format: UUUUUUUUU RRRRRRRRR FFFFFFFFF DDDDDDDDD LLLLLLLLL BBBBBBBBB
     * (spaces added for readability; actual string has none)
     *
     * Each character is the face-letter of the face whose **center** shares
     * the same color as that sticker.
     *
     * Our face index order (0=U,1=R,2=F,3=D,4=L,5=B) matches min2phase exactly,
     * and sticker order within each face is row-major (row 0 left→right, then row 1…).
     *
     * Returns null if centers are not all distinct non-UNSET colors.
     */
    fun toFaceletString(state: RubiksCubeState): String? {
        val letters = charArrayOf('U', 'R', 'F', 'D', 'L', 'B')

        // Build StickerColor → face letter map from the 6 center stickers
        val colorToLetter = HashMap<StickerColor, Char>(7)
        for (face in 0..5) {
            val center = state.stickers[face][1][1]
            if (center == StickerColor.UNSET) return null
            if (colorToLetter.containsKey(center)) return null  // two centers same color
            colorToLetter[center] = letters[face]
        }

        // Assemble the 54-char string
        val sb = StringBuilder(54)
        for (face in 0..5) {
            for (row in 0..2) {
                for (col in 0..2) {
                    sb.append(colorToLetter[state.stickers[face][row][col]] ?: return null)
                }
            }
        }
        return sb.toString()
    }
}
