package com.retroccd.app

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RadialGradient
import android.graphics.RectF
import android.graphics.Shader
import android.util.AttributeSet
import android.view.View
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.random.Random

class RetroOverlayView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : View(context, attrs) {
    private var preset: CameraPreset = CameraPresets.all.first()
    private var grid = false
    private var stamp = true
    private var recording = false
    private val linePaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG)

    fun setCameraPreset(value: CameraPreset) {
        preset = value
        invalidate()
    }

    fun setGridEnabled(enabled: Boolean) {
        grid = enabled
        invalidate()
    }

    fun setStampEnabled(enabled: Boolean) {
        stamp = enabled
        invalidate()
    }

    fun setRecording(enabled: Boolean) {
        recording = enabled
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val w = width.toFloat()
        val h = height.toFloat()
        if (w <= 0f || h <= 0f) return

        // A restrained live tint helps the preview read like the selected physical camera.
        val tintColor = preset.tint
        fillPaint.color = Color.argb(17, Color.red(tintColor), Color.green(tintColor), Color.blue(tintColor))
        canvas.drawRect(0f, 0f, w, h, fillPaint)

        if (preset.vignette > 0f) {
            fillPaint.shader = RadialGradient(
                w / 2f, h / 2f, maxOf(w, h) * 0.78f,
                intArrayOf(Color.TRANSPARENT, Color.argb((30 + preset.vignette * 90).toInt(), 0, 0, 0)),
                floatArrayOf(0.48f, 1f), Shader.TileMode.CLAMP
            )
            canvas.drawRect(0f, 0f, w, h, fillPaint)
            fillPaint.shader = null
        }

        if (preset.scanlines > 0f) {
            linePaint.color = Color.argb((22 + preset.scanlines * 90).toInt(), 0, 0, 0)
            linePaint.strokeWidth = 1.1f
            var y = 0f
            while (y < h) {
                canvas.drawLine(0f, y, w, y, linePaint)
                y += if (preset.id == "DV_99") 4.5f else 6.5f
            }
        }

        if (preset.grain > 0.06f) {
            val random = Random(2003 + (System.currentTimeMillis() / 130L).toInt())
            linePaint.color = Color.argb((12 + preset.grain * 54).toInt(), 255, 255, 255)
            linePaint.strokeWidth = 1f
            repeat((w * h / 11500f).toInt().coerceIn(80, 420)) {
                val x = random.nextFloat() * w
                val y = random.nextFloat() * h
                canvas.drawPoint(x, y, linePaint)
            }
        }

        if (grid) {
            linePaint.color = Color.argb(120, 255, 255, 255)
            linePaint.strokeWidth = 1.2f
            canvas.drawLine(w / 3f, 0f, w / 3f, h, linePaint)
            canvas.drawLine(w * 2f / 3f, 0f, w * 2f / 3f, h, linePaint)
            canvas.drawLine(0f, h / 3f, w, h / 3f, linePaint)
            canvas.drawLine(0f, h * 2f / 3f, w, h * 2f / 3f, linePaint)
        }

        if (preset.id == "POP_INSTANT") {
            linePaint.color = Color.argb(225, 255, 255, 255)
            linePaint.style = Paint.Style.STROKE
            linePaint.strokeWidth = 5f
            canvas.drawRect(RectF(8f, 8f, w - 8f, h - 8f), linePaint)
            linePaint.style = Paint.Style.FILL
        }

        if (stamp) {
            val text = SimpleDateFormat("dd.MM.yyyy", Locale.US).format(Date())
            val stampH = (w * 0.043f).coerceIn(17f, 38f)
            val digitW = stampH * 0.53f
            val thickness = stampH * 0.105f
            val fullWidth = text.sumOf { if (it == '.') (stampH * 0.20f).toDouble() else (digitW + thickness * 0.44f).toDouble() }.toFloat()
            val x = (w - fullWidth - w * 0.045f).coerceAtLeast(10f)
            val y = h - stampH - h * 0.04f
            fillPaint.color = Color.argb(58, 0, 0, 0)
            canvas.drawRoundRect(RectF(x - 5f, y - 3f, w - w * 0.025f, y + stampH + 4f), 3f, 3f, fillPaint)
            RetroEffectProcessor.drawSevenSegmentText(canvas, text, x, y, stampH, digitW, thickness, Color.rgb(255, 145, 55))
        }

        if (recording) {
            fillPaint.color = Color.rgb(248, 54, 56)
            canvas.drawCircle(w - 28f, 32f, 5f, fillPaint)
            linePaint.color = Color.WHITE
            linePaint.textSize = 12f
            linePaint.typeface = android.graphics.Typeface.create("sans-serif-condensed", android.graphics.Typeface.BOLD)
            canvas.drawText("REC", w - 74f, 36f, linePaint)
        }

        if (preset.id == "DV_99") {
            linePaint.color = Color.argb(35, 255, 255, 255)
            linePaint.strokeWidth = 1f
            val trackingY = h * 0.69f + ((System.currentTimeMillis() / 20L) % 13L).toFloat()
            canvas.drawRect(0f, trackingY, w, trackingY + 2.2f, linePaint)
        }

        // Keep the tape texture alive without running a permanent animation loop.
        if (preset.grain > 0.06f && isAttachedToWindow) postInvalidateDelayed(140L)
    }
}
