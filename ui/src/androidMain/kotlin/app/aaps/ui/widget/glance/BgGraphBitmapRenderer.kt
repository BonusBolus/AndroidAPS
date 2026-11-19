package app.aaps.ui.widget.glance

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.DashPathEffect
import android.graphics.Paint
import app.aaps.core.interfaces.overview.graph.BgDataPoint
import app.aaps.core.interfaces.overview.graph.BgRange
import app.aaps.core.interfaces.overview.graph.BgType
import app.aaps.core.interfaces.overview.graph.GraphDataPoint

/**
 * Inputs for [BgGraphBitmapRenderer]. All BG values are in user units
 * (mg/dL or mmol/L), matching [BgDataPoint.value].
 */
data class BgGraphInput(
    val bucketed: List<BgDataPoint>,
    val fromTimeMs: Long,
    val toTimeMs: Long,
    val yMinUserUnits: Double,
    val yMaxUserUnits: Double,
    /** Low and high limit lines. Not drawn when null. */
    val lowUserUnits: Double? = null,
    val highUserUnits: Double? = null,
    /** Target as a step function: each point holds its value until the next one. Not drawn when empty. */
    val targets: List<GraphDataPoint> = emptyList(),
    /** The part of the target line that belongs to an active temp target, drawn in its own colour. */
    val tempTargetFromMs: Long? = null,
    val tempTargetToMs: Long? = null
)

data class BgGraphColors(
    val low: Int,
    val inRange: Int,
    val high: Int,
    /** Only used when the input has limit lines, a target or a temp target. */
    val lowLine: Int = 0xFFC62828.toInt(),
    val highLine: Int = 0xFFFFB300.toInt(),
    val target: Int = 0x66FFFFFF,
    val tempTarget: Int = 0xFF6BD16B.toInt()
) {

    companion object {

        /**
         * The colors a widget draws BG in. They are the same values as the `widget_low`,
         * `widget_inrange` and `widget_high` resources, which have no `values-night` variant, so
         * reading them through resources could only ever return these constants.
         *
         * They are deliberately not the [app.aaps.core.ui.compose.AapsTheme] BG colors: a widget sits
         * on the launcher over unknown wallpaper, not inside the app, so it keeps one high-contrast
         * set instead of following the app's light and dark themes.
         */
        val WIDGET = BgGraphColors(
            low = 0xFFFF0000.toInt(),
            inRange = 0xFF00FF00.toInt(),
            high = 0xFFFFFF00.toInt()
        )
    }
}

/**
 * Minimal Canvas BG-graph renderer producing a transparent bitmap.
 * Draws bucketed BG dots colored by [BgRange], and, when the input has them, the low and high lines and the target.
 */
class BgGraphBitmapRenderer {

    /** @param dotRadiusPx radius of one BG dot */
    fun render(widthPx: Int, heightPx: Int, input: BgGraphInput, colors: BgGraphColors, dotRadiusPx: Float = BG_RADIUS_PX): Bitmap {
        val bitmap = Bitmap.createBitmap(widthPx.coerceAtLeast(1), heightPx.coerceAtLeast(1), Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)

        val left = PADDING_PX
        val right = widthPx - PADDING_PX
        val top = PADDING_PX
        val bottom = heightPx - PADDING_PX
        if (right <= left || bottom <= top) return bitmap
        if (input.bucketed.isEmpty()) return bitmap

        val xSpan = (input.toTimeMs - input.fromTimeMs).coerceAtLeast(1L).toFloat()
        val ySpan = (input.yMaxUserUnits - input.yMinUserUnits).coerceAtLeast(0.001).toFloat()

        val paint = Paint().apply {
            isAntiAlias = true
            style = Paint.Style.FILL
        }

        fun yOf(value: Double): Float = bottom - ((value - input.yMinUserUnits).toFloat() / ySpan) * (bottom - top)
        fun xOf(timeMs: Long): Float = left + (timeMs - input.fromTimeMs).toFloat() / xSpan * (right - left)
        drawLimitLines(canvas, input, colors, left.toFloat(), right.toFloat(), ::yOf)
        drawTarget(canvas, input, colors, ::xOf, ::yOf)

        for (p in input.bucketed) {
            if (p.type != BgType.BUCKETED) continue
            if (p.timestamp !in input.fromTimeMs..input.toTimeMs) continue
            val x = left + (p.timestamp - input.fromTimeMs).toFloat() / xSpan * (right - left)
            val y = bottom - ((p.value - input.yMinUserUnits).toFloat() / ySpan) * (bottom - top)
            paint.color = colorForRange(p.range, colors)
            paint.alpha = if (p.filledGap) GAP_ALPHA else 255
            canvas.drawCircle(x, y, dotRadiusPx, paint)
        }

        return bitmap
    }

    private fun drawLimitLines(canvas: Canvas, input: BgGraphInput, colors: BgGraphColors, left: Float, right: Float, yOf: (Double) -> Float) {
        val paint = Paint().apply {
            isAntiAlias = true
            style = Paint.Style.STROKE
            strokeWidth = LIMIT_STROKE_PX
        }
        fun line(value: Double?, color: Int) {
            if (value == null || value < input.yMinUserUnits || value > input.yMaxUserUnits) return
            paint.color = color
            canvas.drawLine(left, yOf(value), right, yOf(value), paint)
        }
        line(input.lowUserUnits, colors.lowLine)
        line(input.highUserUnits, colors.highLine)
    }

    /**
     * Draws the target step line: faint and dashed, but solid, thicker and in the temp target colour where a
     * temp target is active.
     */
    private fun drawTarget(canvas: Canvas, input: BgGraphInput, colors: BgGraphColors, xOf: (Long) -> Float, yOf: (Double) -> Float) {
        if (input.targets.isEmpty()) return
        val paint = Paint().apply {
            isAntiAlias = true
            style = Paint.Style.STROKE
        }
        val dash = DashPathEffect(floatArrayOf(DASH_PX, DASH_PX), 0f)
        val ttFrom = input.tempTargetFromMs
        val ttTo = input.tempTargetToMs
        val sorted = input.targets.sortedBy { it.timestamp }
        sorted.forEachIndexed { i, point ->
            val segFrom = maxOf(point.timestamp, input.fromTimeMs)
            val segTo = minOf(sorted.getOrNull(i + 1)?.timestamp ?: input.toTimeMs, input.toTimeMs)
            if (segTo <= segFrom || point.value < input.yMinUserUnits || point.value > input.yMaxUserUnits) return@forEachIndexed
            val y = yOf(point.value)
            // Split the segment at the temp target window, so only the overlapping part is highlighted.
            val ttStart = if (ttFrom != null && ttTo != null) maxOf(segFrom, ttFrom) else segTo
            val ttEnd = if (ttFrom != null && ttTo != null) minOf(segTo, ttTo) else segTo
            val hasTt = ttEnd > ttStart
            fun line(from: Long, to: Long, isTemp: Boolean) {
                if (to <= from) return
                paint.color = if (isTemp) colors.tempTarget else colors.target
                paint.strokeWidth = if (isTemp) TEMP_TARGET_STROKE_PX else TARGET_STROKE_PX
                paint.pathEffect = if (isTemp) null else dash
                canvas.drawLine(xOf(from), y, xOf(to), y, paint)
            }
            if (hasTt) {
                line(segFrom, ttStart, false)
                line(ttStart, ttEnd, true)
                line(ttEnd, segTo, false)
            } else {
                line(segFrom, segTo, false)
            }
        }
    }

    private fun colorForRange(range: BgRange, colors: BgGraphColors): Int = when (range) {
        BgRange.LOW      -> colors.low
        BgRange.IN_RANGE -> colors.inRange
        BgRange.HIGH     -> colors.high
    }

    companion object {

        private const val PADDING_PX = 4
        private const val BG_RADIUS_PX = 6.0f
        private const val GAP_ALPHA = 110
        private const val LIMIT_STROKE_PX = 3.0f
        private const val DASH_PX = 10.0f
        private const val TARGET_STROKE_PX = 2.0f
        private const val TEMP_TARGET_STROKE_PX = 7.0f
    }
}
