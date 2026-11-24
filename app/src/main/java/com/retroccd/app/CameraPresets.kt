package com.retroccd.app

import android.graphics.Color

/** A camera is a complete look definition, not a single hue overlay. */
data class CameraPreset(
    val id: String,
    val title: String,
    val description: String,
    val tint: Int,
    val saturation: Float,
    val contrast: Float,
    val warmth: Float,
    val grain: Float,
    val vignette: Float,
    val scanlines: Float = 0f,
    val lowResolution: Int = 0,
    val monochrome: Boolean = false,
    val fade: Float = 0f,
    val jpegQuality: Int = 94,
    val greenShift: Float = 0f,
    val bloom: Float = 0f
)

object CameraPresets {
    val all = listOf(
        CameraPreset("2000S", "2000S", "Warm CCD / JPEG crunch", Color.rgb(255, 153, 83), 1.18f, 1.08f, 0.055f, 0.085f, 0.13f, jpegQuality = 76, bloom = 0.10f),
        CameraPreset("CCD_CLASSIC", "CCD Classic", "Soft compact camera", Color.rgb(137, 188, 203), 1.03f, 1.03f, -0.018f, 0.045f, 0.08f, bloom = 0.08f),
        CameraPreset("FLASH_2K", "Flash 2K", "Hard flash / deep shadows", Color.rgb(250, 206, 155), 1.16f, 1.28f, 0.035f, 0.07f, 0.31f),
        CameraPreset("PAPER_27", "Paper 27", "Disposable film camera", Color.rgb(239, 176, 117), 0.88f, 1.12f, 0.045f, 0.12f, 0.38f, fade = 0.025f),
        CameraPreset("POP_INSTANT", "Pop Instant", "Soft instant print", Color.rgb(244, 201, 151), 1.02f, 0.94f, 0.02f, 0.035f, 0.04f, fade = 0.045f),
        CameraPreset("FLIP_VGA", "Flip VGA", "Early phone camera", Color.rgb(153, 186, 199), 0.88f, 1.13f, -0.018f, 0.16f, 0.18f, lowResolution = 320, jpegQuality = 68),
        CameraPreset("DV_99", "DV-99", "Tape camcorder", Color.rgb(133, 181, 133), 1.02f, 1.10f, -0.015f, 0.12f, 0.12f, scanlines = 0.22f, greenShift = 0.025f, jpegQuality = 84),
        CameraPreset("WEBCAM_01", "Webcam 01", "Early webcam capture", Color.rgb(126, 179, 204), 0.82f, 0.96f, -0.045f, 0.17f, 0.10f, lowResolution = 640, jpegQuality = 72),
        CameraPreset("NOIR_400", "Noir 400", "High contrast B&W", Color.rgb(170, 170, 170), 0f, 1.24f, 0f, 0.095f, 0.33f, monochrome = true),
        CameraPreset("DREAM_620", "Dream 620", "Warm haze / soft glow", Color.rgb(245, 192, 146), 0.84f, 0.86f, 0.07f, 0.035f, 0.07f, fade = 0.07f, bloom = 0.18f)
    )

    fun byId(id: String): CameraPreset = all.firstOrNull { it.id == id } ?: all.first()
}
