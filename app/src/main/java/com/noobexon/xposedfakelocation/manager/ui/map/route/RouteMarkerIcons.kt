package com.noobexon.xposedfakelocation.manager.ui.map.route

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.Typeface
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import kotlin.math.roundToInt

/** Bitmap icons for route markers; drawn by hand so they need no drawable resources. */
internal object RouteMarkerIcons {
    fun numbered(context: Context, number: Int, fill: Int, textColor: Int): Drawable {
        val density = context.resources.displayMetrics.density
        val size = (28 * density).roundToInt()
        val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        val center = size / 2f
        val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        paint.color = fill
        canvas.drawCircle(center, center, center, paint)
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = 2 * density
        paint.color = Color.WHITE
        canvas.drawCircle(center, center, center - density, paint)
        paint.style = Paint.Style.FILL
        paint.color = textColor
        paint.textSize = 13 * density
        paint.textAlign = Paint.Align.CENTER
        paint.typeface = Typeface.DEFAULT_BOLD
        canvas.drawText(number.toString(), center, center - (paint.descent() + paint.ascent()) / 2, paint)
        return BitmapDrawable(context.resources, bitmap)
    }

    /** A triangle pointing along [bearingDegrees] (0 = up/north, clockwise), for a north-up map. */
    fun arrow(context: Context, bearingDegrees: Float, color: Int): Drawable {
        val density = context.resources.displayMetrics.density
        val size = (18 * density).roundToInt()
        val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        canvas.rotate(bearingDegrees, size / 2f, size / 2f)
        val inset = 3 * density
        val path = Path().apply {
            moveTo(size / 2f, inset)
            lineTo(size - inset, size - inset)
            lineTo(size / 2f, size * 0.7f)
            lineTo(inset, size - inset)
            close()
        }
        canvas.drawPath(path, Paint(Paint.ANTI_ALIAS_FLAG).apply { this.color = color })
        return BitmapDrawable(context.resources, bitmap)
    }
}
