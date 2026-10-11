package com.jiguang.vpn.ui

import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.RippleDrawable
import kotlin.math.roundToInt

/**
 * 深色玻璃拟态主题 v2(首页与线路子页共用)。
 * 设计语言:近黑底 + 大圆角卡片 + 弱描边分层 + 单一品牌色点缀,
 * 状态色只用于语义(成功/警告/错误),不参与装饰。
 */
object Theme {

    // 背景:两层近黑,卡片浮于其上
    val BG = Color.rgb(0x07, 0x09, 0x12)
    val BG_ELEVATED = Color.rgb(0x0C, 0x10, 0x1C)
    val CARD = Color.rgb(0x12, 0x17, 0x26)
    val CARD2 = Color.rgb(0x1A, 0x21, 0x33)
    val CARD_STROKE = Color.argb(0x26, 0x8A, 0x9A, 0xC0)   // 弱描边
    val SELECTED_BG = Color.argb(0x2E, 0x5B, 0x8A, 0xFF)
    val DIVIDER = Color.argb(0x1E, 0x8A, 0x94, 0xA8)

    // 品牌色
    val PRIMARY = Color.rgb(0x53, 0x82, 0xFD)
    val PRIMARY_DARK = Color.rgb(0x2E, 0x5A, 0xD8)
    val ACCENT = Color.rgb(0x8B, 0x5C, 0xF6)

    // 状态色
    val GREEN = Color.rgb(0x34, 0xD3, 0x99)
    val YELLOW = Color.rgb(0xFB, 0xBF, 0x24)
    val RED = Color.rgb(0xF8, 0x71, 0x71)
    val ORANGE = Color.rgb(0xFB, 0x92, 0x3C)

    // 文本 / 徽章
    val TEXT = Color.rgb(0xF3, 0xF6, 0xFC)
    val SUBTEXT = Color.rgb(0x8C, 0x96, 0xAD)
    val FAINT = Color.rgb(0x5A, 0x63, 0x7A)
    val BADGE = Color.rgb(0x2A, 0x33, 0x4C)

    // 品牌渐变(按钮/英雄区)
    val GRADIENT_MAIN = intArrayOf(Color.rgb(0x53, 0x82, 0xFD), Color.rgb(0x8B, 0x5C, 0xF6))
    val GRADIENT_HERO = intArrayOf(Color.rgb(0x0F, 0x1A, 0x3C), Color.rgb(0x1A, 0x12, 0x38))
    val GRADIENT_STOP = intArrayOf(Color.rgb(0xE5, 0x48, 0x4D), Color.rgb(0xC1, 0x37, 0x3B))

    fun dp(value: Int, density: Float): Int = (value * density).roundToInt()

    fun rounded(color: Int, radiusDp: Int, density: Float): GradientDrawable =
        GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            cornerRadius = dp(radiusDp, density).toFloat()
            setColor(color)
        }

    /** 卡片:圆角 + 弱描边(分层感的关键)。 */
    fun card(radiusDp: Int, density: Float): GradientDrawable =
        rounded(CARD, radiusDp, density).apply {
            setStroke(dp(1, density), CARD_STROKE)
        }

    fun cardBg(color: Int, radiusDp: Int, density: Float): GradientDrawable =
        rounded(color, radiusDp, density).apply {
            setStroke(dp(1, density), CARD_STROKE)
        }

    /** 带描边的圆角。 */
    fun strokeRounded(color: Int, radiusDp: Int, strokeDp: Int, strokeColor: Int, density: Float): GradientDrawable =
        rounded(color, radiusDp, density).apply {
            setStroke(dp(strokeDp, density), strokeColor)
        }

    fun pill(color: Int, density: Float): GradientDrawable = rounded(color, 22, density)

    fun oval(color: Int): GradientDrawable = GradientDrawable().apply {
        shape = GradientDrawable.OVAL
        setColor(color)
    }

    /** 环形描边(用于连接按钮外圈)。 */
    fun ring(strokeDp: Int, color: Int, density: Float): GradientDrawable = GradientDrawable().apply {
        shape = GradientDrawable.OVAL
        setColor(Color.TRANSPARENT)
        setStroke(dp(strokeDp, density), color)
    }

    /** 线性渐变圆角背景。 */
    fun gradient(colors: IntArray, radiusDp: Int, density: Float): GradientDrawable = GradientDrawable(
        GradientDrawable.Orientation.TL_BR, colors
    ).apply {
        shape = GradientDrawable.RECTANGLE
        cornerRadius = dp(radiusDp, density).toFloat()
    }

    /** 线性渐变圆(连接按钮)。 */
    fun gradientOval(colors: IntArray): GradientDrawable = GradientDrawable(
        GradientDrawable.Orientation.TOP_BOTTOM, colors
    ).apply {
        shape = GradientDrawable.OVAL
    }

    fun ripple(background: GradientDrawable): RippleDrawable =
        RippleDrawable(ColorStateList.valueOf(Color.argb(30, 255, 255, 255)), background, null)
}
