package com.retroccd.app

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.Typeface
import android.media.ExifInterface
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow

object RetroEffectProcessor {
    /** Decode, orient, crop and render a still using the selected camera profile. */
    fun render(
        source: File,
        preset: CameraPreset,
        ratio: String,
        mirrorFrontCamera: Boolean,
        stampDate: Boolean = true
    ): Bitmap {
        val orientation = runCatching {
            ExifInterface(source.absolutePath).getAttributeInt(
                ExifInterface.TAG_ORIENTATION,
                ExifInterface.ORIENTATION_NORMAL
            )
        }.getOrDefault(ExifInterface.ORIENTATION_NORMAL)

        val options = BitmapFactoryOptions.sampled(source, 2048)
        var bitmap = android.graphics.BitmapFactory.decodeFile(source.absolutePath, options)
            ?: error("Не удалось декодировать снимок")

        bitmap = orient(bitmap, orientation, mirrorFrontCamera)
        bitmap = cropToRatio(bitmap, ratio)

        if (preset.lowResolution > 0) {
            val originalWidth = bitmap.width
            val originalHeight = bitmap.height
            val targetWidth = min(preset.lowResolution, originalWidth).coerceAtLeast(160)
            if (targetWidth < originalWidth) {
                val targetHeight = max(1, (targetWidth * originalHeight.toFloat() / originalWidth).toInt())
                val small = Bitmap.createScaledBitmap(bitmap, targetWidth, targetHeight, false)
                if (small !== bitmap) bitmap.recycle()
                val blocky = Bitmap.createScaledBitmap(small, originalWidth, originalHeight, false)
                if (blocky !== small) small.recycle()
                bitmap = blocky
            }
        }

        bitmap = applyColorAndTexture(bitmap, preset)
        if (stampDate) drawLedDate(bitmap, Date())
        drawCameraFrame(bitmap, preset)
        return bitmap
    }

    private object BitmapFactoryOptions {
        fun sampled(file: File, maxEdge: Int): android.graphics.BitmapFactory.Options {
            val bounds = android.graphics.BitmapFactory.Options().apply { inJustDecodeBounds = true }
            android.graphics.BitmapFactory.decodeFile(file.absolutePath, bounds)
            var sample = 1
            while (bounds.outWidth / sample > maxEdge * 1.5 || bounds.outHeight / sample > maxEdge * 1.5) {
                sample *= 2
            }
            return android.graphics.BitmapFactory.Options().apply {
                inSampleSize = sample
                inPreferredConfig = Bitmap.Config.ARGB_8888
            }
        }
    }

    private fun orient(source: Bitmap, exifOrientation: Int, mirror: Boolean): Bitmap {
        val matrix = android.graphics.Matrix()
        when (exifOrientation) {
            ExifInterface.ORIENTATION_ROTATE_90 -> matrix.postRotate(90f)
            ExifInterface.ORIENTATION_ROTATE_180 -> matrix.postRotate(180f)
            ExifInterface.ORIENTATION_ROTATE_270 -> matrix.postRotate(270f)
            ExifInterface.ORIENTATION_FLIP_HORIZONTAL -> matrix.postScale(-1f, 1f)
            ExifInterface.ORIENTATION_FLIP_VERTICAL -> matrix.postScale(1f, -1f)
            ExifInterface.ORIENTATION_TRANSPOSE -> { matrix.postRotate(90f); matrix.postScale(-1f, 1f) }
            ExifInterface.ORIENTATION_TRANSVERSE -> { matrix.postRotate(270f); matrix.postScale(-1f, 1f) }
        }
        if (mirror) matrix.postScale(-1f, 1f)
        if (matrix.isIdentity) return source
        val result = Bitmap.createBitmap(source, 0, 0, source.width, source.height, matrix, true)
        if (result !== source) source.recycle()
        return result
    }

    private fun cropToRatio(source: Bitmap, ratio: String): Bitmap {
        val target = when (ratio) {
            "1:1" -> 1f
            "3:4" -> 0.75f
            "16:9" -> 16f / 9f
            "9:16" -> 9f / 16f
            else -> 4f / 3f
        }
        val current = source.width.toFloat() / source.height
        val cropWidth: Int
        val cropHeight: Int
        if (current > target) {
            cropHeight = source.height
            cropWidth = (cropHeight * target).toInt().coerceAtLeast(1)
        } else {
            cropWidth = source.width
            cropHeight = (cropWidth / target).toInt().coerceAtLeast(1)
        }
        val x = (source.width - cropWidth) / 2
        val y = (source.height - cropHeight) / 2
        if (cropWidth == source.width && cropHeight == source.height) return source
        val cropped = Bitmap.createBitmap(source, x, y, cropWidth, cropHeight)
        source.recycle()
        return cropped
    }

    private fun applyColorAndTexture(source: Bitmap, preset: CameraPreset): Bitmap {
        val width = source.width
        val height = source.height
        val pixels = IntArray(width * height)
        source.getPixels(pixels, 0, width, 0, 0, width, height)
        val centerX = width / 2f
        val centerY = height / 2f
        val maxDistance = (centerX * centerX + centerY * centerY).pow(0.5f).coerceAtLeast(1f)
        var seed = 0x5EED1234L

        for (y in 0 until height) {
            for (x in 0 until width) {
                val index = y * width + x
                val color = pixels[index]
                var r = Color.red(color).toFloat()
                var g = Color.green(color).toFloat()
                var b = Color.blue(color).toFloat()

                if (preset.monochrome) {
                    val luma = 0.299f * r + 0.587f * g + 0.114f * b
                    r = luma; g = luma; b = luma
                } else {
                    val luma = 0.299f * r + 0.587f * g + 0.114f * b
                    r = luma + (r - luma) * preset.saturation
                    g = luma + (g - luma) * preset.saturation
                    b = luma + (b - luma) * preset.saturation
                }

                r = (r - 128f) * preset.contrast + 128f
                g = (g - 128f) * preset.contrast + 128f
                b = (b - 128f) * preset.contrast + 128f

                r += preset.warmth * 255f
                g += preset.warmth * 55f + preset.greenShift * 255f
                b -= preset.warmth * 190f
                if (preset.bloom > 0f) {
                    val brightness = max(r, max(g, b)) / 255f
                    val glow = max(0f, brightness - 0.68f) * preset.bloom * 90f
                    r += glow * 1.04f
                    g += glow * 0.92f
                    b += glow * 0.75f
                }

                if (preset.scanlines > 0f && y % 4 == 0) {
                    val factor = 1f - preset.scanlines
                    r *= factor; g *= factor; b *= factor
                }

                val dx = (x - centerX) / maxDistance
                val dy = (y - centerY) / maxDistance
                val radial = (dx * dx + dy * dy).coerceAtMost(1f)
                val vignette = 1f - preset.vignette * radial * 1.35f
                r *= vignette; g *= vignette; b *= vignette

                if (preset.fade > 0f) {
                    val fadeAmount = preset.fade * 255f
                    r = r * (1f - preset.fade) + fadeAmount
                    g = g * (1f - preset.fade) + fadeAmount * 0.98f
                    b = b * (1f - preset.fade) + fadeAmount * 0.92f
                }

                // Fast deterministic per-pixel noise; blue channel carries a little extra chroma noise.
                seed = seed xor (seed shl 13)
                seed = seed xor (seed ushr 7)
                seed = seed xor (seed shl 17)
                val unitNoise = (((seed ushr 24) and 0xFF).toInt() - 128) / 128f
                val grain = unitNoise * preset.grain * 54f
                r += grain
                g += grain * 0.86f
                b += grain * 1.28f

                pixels[index] = Color.rgb(r.toInt().coerceIn(0, 255), g.toInt().coerceIn(0, 255), b.toInt().coerceIn(0, 255))
            }
        }
        val output = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        output.setPixels(pixels, 0, width, 0, 0, width, height)
        source.recycle()

        if (preset.jpegQuality < 90) {
            val compressed = ByteArrayOutputStream()
            output.compress(Bitmap.CompressFormat.JPEG, preset.jpegQuality, compressed)
            val crunchy = BitmapFactoryDecode.decode(compressed.toByteArray())
            output.recycle()
            return crunchy ?: error("Ошибка JPEG-обработки")
        }
        return output
    }

    private object BitmapFactoryDecode {
        fun decode(bytes: ByteArray): Bitmap? = android.graphics.BitmapFactory.decodeStream(ByteArrayInputStream(bytes))
    }

    fun drawLedDate(bitmap: Bitmap, date: Date) {
        val canvas = Canvas(bitmap)
        val text = SimpleDateFormat("dd.MM.yyyy", Locale.US).format(date)
        val height = (bitmap.width * 0.026f).coerceIn(18f, 72f)
        val digitWidth = height * 0.53f
        val thickness = height * 0.105f
        val gap = height * 0.15f
        val totalWidth = text.sumOf { if (it == '.') (height * 0.20f).toDouble() else (digitWidth + gap).toDouble() }.toFloat()
        val margin = bitmap.width * 0.035f
        val startX = (bitmap.width - margin - totalWidth).coerceAtLeast(margin)
        val top = bitmap.height - margin - height
        val bg = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.argb(68, 0, 0, 0) }
        canvas.drawRoundRect(RectF(startX - height * 0.18f, top - height * 0.13f, bitmap.width - margin * 0.56f, top + height * 1.15f), height * 0.08f, height * 0.08f, bg)
        drawSevenSegmentText(canvas, text, startX, top, height, digitWidth, thickness, Color.rgb(255, 145, 55))
    }

    fun drawSevenSegmentText(canvas: Canvas, text: String, x: Float, y: Float, h: Float, w: Float, t: Float, color: Int) {
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.FILL
            this.color = color
            setShadowLayer(h * 0.18f, 0f, 0f, Color.argb(170, 255, 103, 24))
        }
        var cursor = x
        val segmentMap = mapOf(
            '0' to intArrayOf(1, 1, 1, 1, 1, 1, 0),
            '1' to intArrayOf(0, 1, 1, 0, 0, 0, 0),
            '2' to intArrayOf(1, 1, 0, 1, 1, 0, 1),
            '3' to intArrayOf(1, 1, 1, 1, 0, 0, 1),
            '4' to intArrayOf(0, 1, 1, 0, 0, 1, 1),
            '5' to intArrayOf(1, 0, 1, 1, 0, 1, 1),
            '6' to intArrayOf(1, 0, 1, 1, 1, 1, 1),
            '7' to intArrayOf(1, 1, 1, 0, 0, 0, 0),
            '8' to intArrayOf(1, 1, 1, 1, 1, 1, 1),
            '9' to intArrayOf(1, 1, 1, 1, 0, 1, 1)
        )
        for (ch in text) {
            if (ch == '.') {
                canvas.drawCircle(cursor + t * 0.5f, y + h - t * 0.2f, t * 0.48f, paint)
                cursor += h * 0.20f
                continue
            }
            val segments = segmentMap[ch] ?: intArrayOf(0, 0, 0, 0, 0, 0, 0)
            val mid = y + h * 0.5f
            val rectangles = arrayOf(
                RectF(cursor + t, y, cursor + w - t, y + t),
                RectF(cursor + w - t, y + t, cursor + w, mid - t * 0.2f),
                RectF(cursor + w - t, mid + t * 0.2f, cursor + w, y + h - t),
                RectF(cursor + t, y + h - t, cursor + w - t, y + h),
                RectF(cursor, mid + t * 0.2f, cursor + t, y + h - t),
                RectF(cursor, y + t, cursor + t, mid - t * 0.2f),
                RectF(cursor + t, mid - t * 0.5f, cursor + w - t, mid + t * 0.5f)
            )
            for (i in segments.indices) if (segments[i] == 1) canvas.drawRoundRect(rectangles[i], t * 0.25f, t * 0.25f, paint)
            cursor += w + t * 0.44f
        }
        paint.clearShadowLayer()
    }

    private fun drawCameraFrame(bitmap: Bitmap, preset: CameraPreset) {
        val canvas = Canvas(bitmap)
        val p = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE }
        val edge = bitmap.width * 0.012f
        when (preset.id) {
            "POP_INSTANT" -> {
                p.color = Color.WHITE
                p.strokeWidth = edge * 3.0f
                canvas.drawRect(edge, edge, bitmap.width - edge, bitmap.height - edge, p)
                p.style = Paint.Style.FILL
                p.color = Color.argb(25, 255, 255, 255)
                canvas.drawRect(edge, bitmap.height - edge * 5.5f, bitmap.width - edge, bitmap.height - edge, p)
            }
            "PAPER_27" -> {
                p.color = Color.argb(170, 239, 220, 188)
                p.strokeWidth = edge
                canvas.drawRect(edge, edge, bitmap.width - edge, bitmap.height - edge, p)
            }
        }
    }

    fun contactSheet(frames: List<Bitmap>, columns: Int, rows: Int): Bitmap {
        require(frames.isNotEmpty())
        val cellW = frames.maxOf { it.width }.coerceAtMost(720)
        val cellH = frames.maxOf { it.height }.coerceAtMost(720)
        val gap = max(8, cellW / 80)
        val sheet = Bitmap.createBitmap(columns * cellW + (columns + 1) * gap, rows * cellH + (rows + 1) * gap, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(sheet)
        canvas.drawColor(Color.rgb(16, 17, 21))
        val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
        frames.forEachIndexed { index, bitmap ->
            val col = index % columns
            val row = index / columns
            if (row >= rows) return@forEachIndexed
            val left = gap + col * (cellW + gap)
            val top = gap + row * (cellH + gap)
            val scale = min(cellW.toFloat() / bitmap.width, cellH.toFloat() / bitmap.height)
            val dw = (bitmap.width * scale).toInt()
            val dh = (bitmap.height * scale).toInt()
            val dst = Rect(left + (cellW - dw) / 2, top + (cellH - dh) / 2, left + (cellW + dw) / 2, top + (cellH + dh) / 2)
            canvas.drawBitmap(bitmap, null, dst, paint)
        }
        return sheet
    }
}
