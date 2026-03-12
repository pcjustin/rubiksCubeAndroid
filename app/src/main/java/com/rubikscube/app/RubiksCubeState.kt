package com.rubikscube.app

/**
 * Stores the logical state of the Rubik's cube.
 *
 * Face indices (matching min2phase convention):
 *   0 = U (Up,    +Y)
 *   1 = R (Right, +X)
 *   2 = F (Front, +Z)
 *   3 = D (Down,  -Y)
 *   4 = L (Left,  -X)
 *   5 = B (Back,  -Z)
 *
 * Sticker layout per face (row 0 = top/back, col 0 = left):
 *   [0][0] [0][1] [0][2]
 *   [1][0] [1][1] [1][2]
 *   [2][0] [2][1] [2][2]
 */
class RubiksCubeState {

    // stickers[face][row][col]
    val stickers: Array<Array<Array<StickerColor>>> =
        Array(6) { Array(3) { Array(3) { StickerColor.UNSET } } }

    fun getSticker(face: Int, row: Int, col: Int): StickerColor =
        stickers[face][row][col]

    fun setSticker(face: Int, row: Int, col: Int, color: StickerColor) {
        stickers[face][row][col] = color
    }

    /** Return a flat index 0..53 for (face, row, col). */
    fun stickerIndex(face: Int, row: Int, col: Int): Int =
        face * 9 + row * 3 + col

    /** Return (face, row, col) from flat index. */
    fun indexToFaceRowCol(index: Int): Triple<Int, Int, Int> {
        val face = index / 9
        val rem  = index % 9
        return Triple(face, rem / 3, rem % 3)
    }
}
