package com.noobexon.xposedfakelocation.manager.ui.routes

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.drawable.BitmapDrawable

private const val MARKER_SIZE_DP = 40
private const val MARKER_SIZE_SMALL_DP = 28

fun createNumberedMarkerDrawable(context: Context, number: Int, small: Boolean = false): BitmapDrawable {
    val sizeDp = if (small) MARKER_SIZE_SMALL_DP else MARKER_SIZE_DP
    val sizePx = (sizeDp * context.resources.displayMetrics.density).toInt()
    return createNumberedBitmap(context, number, sizePx)
}

fun createCurrentPositionDrawable(context: Context): BitmapDrawable {
    val sizePx = (24 * context.resources.displayMetrics.density).toInt()
    val bitmap = Bitmap.createBitmap(sizePx, sizePx, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(bitmap)
    val paint = Paint(Paint.ANTI_ALIAS_FLAG)

    val cx = sizePx / 2f
    val cy = sizePx / 2f

    paint.color = android.graphics.Color.rgb(76, 175, 80)
    paint.style = Paint.Style.FILL
    canvas.drawCircle(cx, cy, sizePx / 2f - 1, paint)

    paint.color = android.graphics.Color.WHITE
    paint.style = Paint.Style.STROKE
    paint.strokeWidth = 2f
    canvas.drawCircle(cx, cy, sizePx / 2f - 2, paint)

    paint.style = Paint.Style.FILL
    canvas.drawCircle(cx, cy, sizePx / 4f, paint)

    return BitmapDrawable(context.resources, bitmap)
}

private fun createNumberedBitmap(context: Context, number: Int, sizePx: Int): BitmapDrawable {
    val bitmap = Bitmap.createBitmap(sizePx, sizePx, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(bitmap)
    val paint = Paint(Paint.ANTI_ALIAS_FLAG)

    val cx = sizePx / 2f
    val cy = sizePx / 2f
    val radius = sizePx / 2f - 2

    paint.color = android.graphics.Color.rgb(33, 150, 243)
    paint.style = Paint.Style.FILL
    canvas.drawCircle(cx, cy, radius, paint)

    paint.color = android.graphics.Color.WHITE
    paint.style = Paint.Style.STROKE
    paint.strokeWidth = 2f
    canvas.drawCircle(cx, cy, radius - 1, paint)

    paint.color = android.graphics.Color.WHITE
    paint.style = Paint.Style.FILL
    paint.isAntiAlias = true
    paint.textSize = sizePx * 0.5f
    paint.textAlign = Paint.Align.CENTER
    val yOffset = -(paint.descent() + paint.ascent()) / 2f
    canvas.drawText(number.toString(), cx, cy + yOffset, paint)

    return BitmapDrawable(context.resources, bitmap)
}
