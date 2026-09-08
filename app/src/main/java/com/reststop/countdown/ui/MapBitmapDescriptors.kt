package com.reststop.countdown.ui

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Typeface
import androidx.annotation.DrawableRes
import androidx.compose.ui.graphics.toArgb
import androidx.core.content.ContextCompat
import com.google.android.gms.maps.model.BitmapDescriptor
import com.google.android.gms.maps.model.BitmapDescriptorFactory

/**
 * [BitmapDescriptorFactory.fromResource] decodes the resource with `BitmapFactory.decodeResource`
 * under the hood, which only understands raster images (PNG/JPG) - handed a vector `<vector>`
 * drawable, it silently produces a null Bitmap and the Maps SDK crashes wrapping it. This draws
 * the vector drawable onto a real Bitmap first, the standard workaround for vector map markers.
 */
fun vectorBitmapDescriptor(context: Context, @DrawableRes resId: Int): BitmapDescriptor {
    val drawable = requireNotNull(ContextCompat.getDrawable(context, resId)) { "Drawable resource not found: $resId" }
    val width = drawable.intrinsicWidth.coerceAtLeast(1)
    val height = drawable.intrinsicHeight.coerceAtLeast(1)
    val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(bitmap)
    drawable.setBounds(0, 0, width, height)
    drawable.draw(canvas)
    return BitmapDescriptorFactory.fromBitmap(bitmap)
}

/** A small filled circle with a bold white number in the center, for labeling route alternatives on the map. */
fun numberedBitmapDescriptor(number: Int, fillColor: androidx.compose.ui.graphics.Color, diameterPx: Int = 72): BitmapDescriptor {
    val bitmap = Bitmap.createBitmap(diameterPx, diameterPx, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(bitmap)
    val radius = diameterPx / 2f

    val fillColorArgb = fillColor.toArgb()
    val circlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = fillColorArgb }
    canvas.drawCircle(radius, radius, radius, circlePaint)

    val borderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        this.color = android.graphics.Color.WHITE
        style = Paint.Style.STROKE
        strokeWidth = diameterPx / 12f
    }
    canvas.drawCircle(radius, radius, radius - borderPaint.strokeWidth / 2, borderPaint)

    val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        this.color = android.graphics.Color.WHITE
        textSize = diameterPx * 0.5f
        textAlign = Paint.Align.CENTER
        typeface = Typeface.DEFAULT_BOLD
    }
    val text = number.toString()
    val textY = radius - (textPaint.descent() + textPaint.ascent()) / 2
    canvas.drawText(text, radius, textY, textPaint)

    return BitmapDescriptorFactory.fromBitmap(bitmap)
}
