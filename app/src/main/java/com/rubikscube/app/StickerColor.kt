package com.rubikscube.app

import android.graphics.Color

/**
 * The 7 possible sticker colors (6 face colors + unset/gray).
 * androidColor is used for the UI palette.
 * The name is shown in the selected-color label.
 */
enum class StickerColor(
    val displayName: String,
    val androidColor: Int
) {
    WHITE("白色", Color.WHITE),
    YELLOW("黃色", Color.parseColor("#FFD700")),
    RED("紅色", Color.parseColor("#CC0000")),
    ORANGE("橙色", Color.parseColor("#FF8C00")),
    BLUE("藍色", Color.parseColor("#0044CC")),
    GREEN("綠色", Color.parseColor("#009900")),
    UNSET("未設定", Color.parseColor("#808080"));
}
