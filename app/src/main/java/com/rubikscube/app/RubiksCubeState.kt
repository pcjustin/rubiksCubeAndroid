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

    fun copy(): RubiksCubeState {
        val clone = RubiksCubeState()
        for (face in 0..5) for (row in 0..2) for (col in 0..2) {
            clone.stickers[face][row][col] = stickers[face][row][col]
        }
        return clone
    }

    fun overwriteFrom(other: RubiksCubeState) {
        for (face in 0..5) for (row in 0..2) for (col in 0..2) {
            stickers[face][row][col] = other.stickers[face][row][col]
        }
    }

    fun applyMove(move: String) {
        val parsed = parseMove(move) ?: return
        repeat(parsed.turns) { rotateClockwise(parsed.face) }
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

    data class Move(val face: Int, val turns: Int)

    private data class StickerVector(val x: Int, val y: Int, val z: Int, val nx: Int, val ny: Int, val nz: Int)

    private fun rotateClockwise(face: Int) {
        val next = Array(6) { Array(3) { Array(3) { StickerColor.UNSET } } }
        for (srcFace in 0..5) for (row in 0..2) for (col in 0..2) {
            val vector = stickerVector(srcFace, row, col)
            val rotated = if (isOnFaceLayer(vector, face)) rotateVectorForFace(vector, face) else vector
            val (dstFace, dstRow, dstCol) = vectorToFaceRowCol(rotated)
            next[dstFace][dstRow][dstCol] = stickers[srcFace][row][col]
        }
        for (dstFace in 0..5) for (row in 0..2) for (col in 0..2) {
            stickers[dstFace][row][col] = next[dstFace][row][col]
        }
    }

    private fun isOnFaceLayer(vector: StickerVector, face: Int): Boolean =
        when (face) {
            0 -> vector.y == 1
            1 -> vector.x == 1
            2 -> vector.z == 1
            3 -> vector.y == -1
            4 -> vector.x == -1
            5 -> vector.z == -1
            else -> false
        }

    private fun rotateVectorForFace(vector: StickerVector, face: Int): StickerVector =
        when (face) {
            0 -> rotateAroundY(vector, -1)
            1 -> rotateAroundX(vector, -1)
            2 -> rotateAroundZ(vector, 1)
            3 -> rotateAroundY(vector, 1)
            4 -> rotateAroundX(vector, 1)
            5 -> rotateAroundZ(vector, -1)
            else -> vector
        }

    private fun rotateAroundX(vector: StickerVector, quarterTurns: Int): StickerVector {
        var x = vector.x
        var y = vector.y
        var z = vector.z
        var nx = vector.nx
        var ny = vector.ny
        var nz = vector.nz
        repeat(normalizeQuarterTurns(quarterTurns)) {
            val oldY = y
            val oldNy = ny
            y = -z
            z = oldY
            ny = -nz
            nz = oldNy
        }
        return StickerVector(x, y, z, nx, ny, nz)
    }

    private fun rotateAroundY(vector: StickerVector, quarterTurns: Int): StickerVector {
        var x = vector.x
        var y = vector.y
        var z = vector.z
        var nx = vector.nx
        var ny = vector.ny
        var nz = vector.nz
        repeat(normalizeQuarterTurns(quarterTurns)) {
            val oldX = x
            val oldNx = nx
            x = z
            z = -oldX
            nx = nz
            nz = -oldNx
        }
        return StickerVector(x, y, z, nx, ny, nz)
    }

    private fun rotateAroundZ(vector: StickerVector, quarterTurns: Int): StickerVector {
        var x = vector.x
        var y = vector.y
        var z = vector.z
        var nx = vector.nx
        var ny = vector.ny
        var nz = vector.nz
        repeat(normalizeQuarterTurns(quarterTurns)) {
            val oldX = x
            val oldNx = nx
            x = y
            y = -oldX
            nx = ny
            ny = -oldNx
        }
        return StickerVector(x, y, z, nx, ny, nz)
    }

    private fun normalizeQuarterTurns(quarterTurns: Int): Int = ((quarterTurns % 4) + 4) % 4

    private fun stickerVector(face: Int, row: Int, col: Int): StickerVector =
        when (face) {
            0 -> StickerVector(col - 1, 1, row - 1, 0, 1, 0)
            1 -> StickerVector(1, 1 - row, 1 - col, 1, 0, 0)
            2 -> StickerVector(col - 1, 1 - row, 1, 0, 0, 1)
            3 -> StickerVector(col - 1, -1, 1 - row, 0, -1, 0)
            4 -> StickerVector(-1, 1 - row, col - 1, -1, 0, 0)
            5 -> StickerVector(1 - col, 1 - row, -1, 0, 0, -1)
            else -> error("Invalid face $face")
        }

    private fun vectorToFaceRowCol(vector: StickerVector): Triple<Int, Int, Int> =
        when {
            vector.ny == 1 -> Triple(0, vector.z + 1, vector.x + 1)
            vector.nx == 1 -> Triple(1, 1 - vector.y, 1 - vector.z)
            vector.nz == 1 -> Triple(2, 1 - vector.y, vector.x + 1)
            vector.ny == -1 -> Triple(3, 1 - vector.z, vector.x + 1)
            vector.nx == -1 -> Triple(4, 1 - vector.y, vector.z + 1)
            vector.nz == -1 -> Triple(5, 1 - vector.y, 1 - vector.x)
            else -> error("Invalid sticker vector $vector")
        }

    companion object {
        fun parseMove(move: String): Move? {
            if (move.isBlank()) return null
            val face = when (move[0]) {
                'U' -> 0
                'R' -> 1
                'F' -> 2
                'D' -> 3
                'L' -> 4
                'B' -> 5
                else -> return null
            }
            val turns = when {
                move.endsWith("2") -> 2
                move.endsWith("'") -> 3
                else -> 1
            }
            return Move(face, turns)
        }

        fun inverseMove(move: String): String {
            val trimmed = move.trim()
            return when {
                trimmed.endsWith("2") -> trimmed
                trimmed.endsWith("'") -> trimmed.dropLast(1)
                trimmed.isNotEmpty() -> "$trimmed'"
                else -> trimmed
            }
        }
    }
}
