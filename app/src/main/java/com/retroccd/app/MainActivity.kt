package com.retroccd.app

import android.Manifest
import android.app.AlertDialog
import android.content.ContentValues
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.Typeface
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.net.Uri
import android.os.Bundle
import android.os.Environment
import android.os.Handler
import android.os.Looper
import android.provider.MediaStore
import android.view.Gravity
import android.view.HapticFeedbackConstants
import android.view.Surface
import android.view.View
import android.widget.FrameLayout
import android.widget.HorizontalScrollView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.camera.core.Camera
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.video.MediaStoreOutputOptions
import androidx.camera.video.Quality
import androidx.camera.video.QualitySelector
import androidx.camera.video.Recorder
import androidx.camera.video.Recording
import androidx.camera.video.VideoCapture
import androidx.camera.video.VideoRecordEvent
import androidx.camera.view.PreviewView
import androidx.core.content.ContextCompat
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

class MainActivity : ComponentActivity(), SensorEventListener {
    private enum class Mode { PHOTO, VIDEO, BOOTH, ROLL, IMPORT }
    private enum class Flash { OFF, AUTO, ON }

    private lateinit var root: FrameLayout
    private lateinit var previewView: PreviewView
    private lateinit var overlayView: RetroOverlayView
    private lateinit var topBarView: LinearLayout
    private lateinit var bottomPanelView: LinearLayout
    private lateinit var modeLabel: TextView
    private lateinit var presetLabel: TextView
    private lateinit var shutterButton: TextView
    private lateinit var flashButton: TextView
    private lateinit var timerButton: TextView
    private lateinit var ratioButton: TextView
    private lateinit var gridButton: TextView
    private lateinit var dateButton: TextView
    private lateinit var countdownLabel: TextView
    private lateinit var galleryButton: TextView
    private lateinit var modeButtons: List<TextView>
    private lateinit var presetButtons: List<TextView>

    private val mainHandler = Handler(Looper.getMainLooper())
    private val cameraExecutor: ExecutorService = Executors.newSingleThreadExecutor()
    private val ioExecutor: ExecutorService = Executors.newSingleThreadExecutor()

    private var cameraProvider: ProcessCameraProvider? = null
    private var camera: Camera? = null
    private var imageCapture: ImageCapture? = null
    private var videoCapture: VideoCapture<Recorder>? = null
    private var recording: Recording? = null
    private var isRecording = false
    private var lensFacing = CameraSelector.LENS_FACING_BACK

    private var selectedPreset = CameraPresets.all.first()
    private var currentMode = Mode.PHOTO
    private var flash = Flash.AUTO
    private var timerSeconds = 0
    private val ratios = listOf("4:3", "1:1", "3:4", "16:9", "9:16")
    private var ratioIndex = 0
    private var gridEnabled = false
    private var dateStampEnabled = true
    private var ambientLux = 100f
    private var lastSavedUri: Uri? = null
    private var lastSavedMime = "image/jpeg"
    private val rollFrames = mutableListOf<Bitmap>()

    private val sensorManager by lazy { getSystemService(SENSOR_SERVICE) as SensorManager }
    private val lightSensor: Sensor? by lazy { sensorManager.getDefaultSensor(Sensor.TYPE_LIGHT) }

    private val russian: Boolean get() = Locale.getDefault().language != "en"
    private fun tr(ru: String, en: String) = if (russian) ru else en

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.statusBarColor = Color.rgb(8, 10, 15)
        window.navigationBarColor = Color.rgb(8, 10, 15)
        window.decorView.systemUiVisibility = 0
        buildInterface()
        updateControls()
        ensureCameraPermission()
    }

    override fun onResume() {
        super.onResume()
        lightSensor?.let { sensorManager.registerListener(this, it, SensorManager.SENSOR_DELAY_NORMAL) }
        if (::previewView.isInitialized && checkSelfPermission(Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED && cameraProvider == null) {
            startCamera()
        }
    }

    override fun onPause() {
        sensorManager.unregisterListener(this)
        super.onPause()
    }

    override fun onDestroy() {
        recording?.stop()
        cameraExecutor.shutdown()
        ioExecutor.shutdown()
        super.onDestroy()
    }

    private fun buildInterface() {
        root = FrameLayout(this).apply { setBackgroundColor(Color.BLACK) }
        previewView = PreviewView(this).apply {
            implementationMode = PreviewView.ImplementationMode.COMPATIBLE
            scaleType = PreviewView.ScaleType.FILL_CENTER
            setBackgroundColor(Color.rgb(9, 11, 16))
        }
        overlayView = RetroOverlayView(this)
        root.addView(previewView, FrameLayout.LayoutParams(-1, -1))
        root.addView(overlayView, FrameLayout.LayoutParams(-1, -1))

        countdownLabel = TextView(this).apply {
            textSize = 74f
            typeface = Typeface.create("sans-serif-black", Typeface.NORMAL)
            setTextColor(Color.WHITE)
            gravity = Gravity.CENTER
            visibility = View.GONE
            setShadowLayer(18f, 0f, 0f, Color.BLACK)
        }
        root.addView(countdownLabel, FrameLayout.LayoutParams(-1, -1))

        topBarView = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(16), dp(7), dp(12), dp(7))
            background = rounded(Color.argb(188, 8, 10, 15), 0f)
        }
        val brand = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        val brandName = text("RETROCCD", 17f, Color.WHITE, true).apply { letterSpacing = 0.14f }
        val brandSub = text("DIGITAL CAMERA / 2003", 8f, Color.rgb(165, 174, 184), true).apply { letterSpacing = 0.12f }
        brand.addView(brandName)
        brand.addView(brandSub)
        topBarView.addView(brand, LinearLayout.LayoutParams(0, -2, 1f))

        flashButton = topButton("AUTO") { cycleFlash() }
        timerButton = topButton("OFF") { cycleTimer() }
        ratioButton = topButton("4:3") { cycleRatio() }
        gridButton = topButton("GRID") { toggleGrid() }
        dateButton = topButton("DATE") { toggleDateStamp() }
        listOf(flashButton, timerButton, ratioButton, gridButton, dateButton).forEach {
            topBarView.addView(it, LinearLayout.LayoutParams(-2, dp(38)).apply { leftMargin = dp(4) })
        }
        root.addView(topBarView, FrameLayout.LayoutParams(-1, dp(58), Gravity.TOP))

        bottomPanelView = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            setPadding(dp(12), dp(10), dp(12), dp(12))
            background = rounded(Color.argb(226, 8, 10, 15), dp(18).toFloat())
        }
        modeLabel = text("2000S  ·  READY", 11f, Color.rgb(239, 169, 102), true).apply {
            letterSpacing = 0.11f
            gravity = Gravity.CENTER
        }
        bottomPanelView.addView(modeLabel, LinearLayout.LayoutParams(-1, dp(21)))

        presetLabel = text(tr("ВЫБОР КАМЕРЫ", "SELECT CAMERA"), 8f, Color.rgb(140, 151, 164), true).apply {
            letterSpacing = 0.15f
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(5), dp(3), 0, dp(2))
        }
        bottomPanelView.addView(presetLabel, LinearLayout.LayoutParams(-1, dp(22)))

        val presetScroll = HorizontalScrollView(this).apply {
            isHorizontalScrollBarEnabled = false
            overScrollMode = View.OVER_SCROLL_NEVER
        }
        val presetRow = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
        presetButtons = CameraPresets.all.map { preset ->
            val chip = chip(preset.title, false)
            chip.setOnClickListener { selectPreset(preset) }
            presetRow.addView(chip, LinearLayout.LayoutParams(-2, dp(36)).apply { rightMargin = dp(6) })
            chip
        }
        presetScroll.addView(presetRow)
        bottomPanelView.addView(presetScroll, LinearLayout.LayoutParams(-1, dp(43)))

        val modeScroll = HorizontalScrollView(this).apply {
            isHorizontalScrollBarEnabled = false
            overScrollMode = View.OVER_SCROLL_NEVER
        }
        val modeRow = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER }
        val modes = listOf(
            Mode.PHOTO to tr("ФОТО", "PHOTO"),
            Mode.VIDEO to tr("ВИДЕО", "VIDEO"),
            Mode.BOOTH to "BOOTH",
            Mode.ROLL to "ROLL",
            Mode.IMPORT to tr("ИМПОРТ", "IMPORT")
        )
        modeButtons = modes.map { (mode, label) ->
            val chip = chip(label, mode == Mode.PHOTO)
            chip.setOnClickListener { chooseMode(mode) }
            modeRow.addView(chip, LinearLayout.LayoutParams(-2, dp(33)).apply { rightMargin = dp(5) })
            chip
        }
        modeScroll.addView(modeRow)
        bottomPanelView.addView(modeScroll, LinearLayout.LayoutParams(-1, dp(38)))

        val captureRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
            setPadding(0, dp(6), 0, dp(3))
        }
        val switchButton = circleSideButton("↻") { switchCamera() }
        galleryButton = circleSideButton("▣") { openLatestMedia() }
        shutterButton = TextView(this).apply {
            text = ""
            gravity = Gravity.CENTER
            setTextColor(Color.BLACK)
            background = rounded(Color.WHITE, dp(50).toFloat()).apply {
                setStroke(dp(5), Color.rgb(235, 164, 91))
            }
            elevation = dp(4).toFloat()
            contentDescription = tr("Спустить затвор", "Take photo")
            setOnClickListener { onShutterPressed() }
        }
        captureRow.addView(switchButton, LinearLayout.LayoutParams(dp(64), dp(64)).apply { rightMargin = dp(28) })
        captureRow.addView(shutterButton, LinearLayout.LayoutParams(dp(76), dp(76)))
        captureRow.addView(galleryButton, LinearLayout.LayoutParams(dp(64), dp(64)).apply { leftMargin = dp(28) })
        bottomPanelView.addView(captureRow, LinearLayout.LayoutParams(-1, dp(86)))

        val footer = text("OFFLINE  ·  NO ACCOUNT  ·  NO CLOUD", 8f, Color.rgb(111, 122, 135), true).apply {
            gravity = Gravity.CENTER
            letterSpacing = 0.12f
        }
        bottomPanelView.addView(footer, LinearLayout.LayoutParams(-1, dp(19)))
        root.addView(bottomPanelView, FrameLayout.LayoutParams(-1, -2, Gravity.BOTTOM).apply {
            leftMargin = dp(8); rightMargin = dp(8); bottomMargin = dp(6)
        })

        setContentView(root)
        root.setOnApplyWindowInsetsListener { _, insets ->
            val topParams = topBarView.layoutParams as FrameLayout.LayoutParams
            topParams.topMargin = insets.systemWindowInsetTop
            topBarView.layoutParams = topParams
            val bottomParams = bottomPanelView.layoutParams as FrameLayout.LayoutParams
            bottomParams.bottomMargin = insets.systemWindowInsetBottom + dp(6)
            bottomPanelView.layoutParams = bottomParams
            insets
        }
        root.requestApplyInsets()
        refreshPresetChips()
    }

    private fun text(value: String, size: Float, color: Int, bold: Boolean): TextView = TextView(this).apply {
        text = value
        textSize = size
        setTextColor(color)
        if (bold) typeface = Typeface.create("sans-serif", Typeface.BOLD)
        includeFontPadding = false
    }

    private fun topButton(value: String, action: () -> Unit): TextView = text(value, 9f, Color.WHITE, true).apply {
        gravity = Gravity.CENTER
        minWidth = dp(42)
        setPadding(dp(7), 0, dp(7), 0)
        background = rounded(Color.argb(80, 255, 255, 255), dp(9).toFloat())
        setOnClickListener { action() }
    }

    private fun chip(value: String, selected: Boolean): TextView = text(value, 9f, if (selected) Color.BLACK else Color.rgb(211, 218, 225), true).apply {
        gravity = Gravity.CENTER
        setPadding(dp(11), 0, dp(11), 0)
        background = rounded(if (selected) Color.rgb(241, 169, 99) else Color.argb(80, 255, 255, 255), dp(10).toFloat())
    }

    private fun circleSideButton(value: String, action: () -> Unit): TextView = text(value, 25f, Color.WHITE, false).apply {
        gravity = Gravity.CENTER
        background = rounded(Color.argb(90, 255, 255, 255), dp(40).toFloat()).apply { setStroke(dp(1), Color.argb(70, 255, 255, 255)) }
        setOnClickListener { action() }
    }

    private fun rounded(color: Int, radius: Float): android.graphics.drawable.GradientDrawable =
        android.graphics.drawable.GradientDrawable().apply {
            setColor(color)
            cornerRadius = radius
        }

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()

    private fun ensureCameraPermission() {
        if (checkSelfPermission(Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) {
            startCamera()
        } else {
            requestPermissions(arrayOf(Manifest.permission.CAMERA), REQUEST_CAMERA)
        }
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == REQUEST_CAMERA) {
            if (grantResults.firstOrNull() == PackageManager.PERMISSION_GRANTED) startCamera()
            else toast(tr("Без доступа к камере приложение не сможет снимать.", "Camera permission is required to take photos."))
        }
    }

    private fun startCamera() {
        val future = ProcessCameraProvider.getInstance(this)
        future.addListener({
            try {
                cameraProvider = future.get()
                bindCameraUseCases()
            } catch (error: Exception) {
                toast(tr("Не удалось запустить камеру: ${error.localizedMessage}", "Could not start camera: ${error.localizedMessage}"))
            }
        }, ContextCompat.getMainExecutor(this))
    }

    private fun bindCameraUseCases() {
        val provider = cameraProvider ?: return
        if (isRecording) recording?.stop()
        val rotation = previewView.display?.rotation ?: Surface.ROTATION_0
        val preview = Preview.Builder().setTargetRotation(rotation).build().also {
            it.setSurfaceProvider(previewView.surfaceProvider)
        }
        val stillCapture = ImageCapture.Builder()
            .setCaptureMode(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY)
            .setTargetRotation(rotation)
            .build()
        val recorder = Recorder.Builder()
            .setQualitySelector(QualitySelector.from(Quality.HD))
            .build()
        val video = VideoCapture.withOutput(recorder)
        val selector = CameraSelector.Builder().requireLensFacing(lensFacing).build()

        provider.unbindAll()
        try {
            camera = provider.bindToLifecycle(this, selector, preview, stillCapture, video)
            videoCapture = video
        } catch (firstError: Exception) {
            // A few entry-level devices cannot bind three use cases at once; photo mode still works.
            try {
                provider.unbindAll()
                camera = provider.bindToLifecycle(this, selector, preview, stillCapture)
                videoCapture = null
            } catch (secondError: Exception) {
                toast(tr("Камера недоступна на этой конфигурации.", "Camera is unavailable on this device configuration."))
            }
        }
        imageCapture = stillCapture
        applyFlashMode()
        previewView.scaleX = if (lensFacing == CameraSelector.LENS_FACING_FRONT) -1f else 1f
        overlayView.setCameraPreset(selectedPreset)
    }

    private fun switchCamera() {
        if (isRecording) {
            toast(tr("Сначала остановите запись.", "Stop recording before switching cameras."))
            return
        }
        lensFacing = if (lensFacing == CameraSelector.LENS_FACING_BACK) CameraSelector.LENS_FACING_FRONT else CameraSelector.LENS_FACING_BACK
        bindCameraUseCases()
    }

    private fun selectPreset(preset: CameraPreset) {
        selectedPreset = preset
        overlayView.setCameraPreset(preset)
        presetLabel.text = "${preset.title.uppercase(Locale.US)}  /  ${preset.description.uppercase(Locale.US)}"
        modeLabel.text = "${preset.title.uppercase(Locale.US)}  ·  READY"
        refreshPresetChips()
    }

    private fun refreshPresetChips() {
        if (!::presetButtons.isInitialized) return
        CameraPresets.all.forEachIndexed { index, preset ->
            val selected = preset.id == selectedPreset.id
            presetButtons[index].background = rounded(
                if (selected) Color.rgb(241, 169, 99) else Color.argb(80, 255, 255, 255),
                dp(10).toFloat()
            )
            presetButtons[index].setTextColor(if (selected) Color.BLACK else Color.rgb(211, 218, 225))
        }
    }

    private fun chooseMode(mode: Mode) {
        if (mode == Mode.IMPORT) {
            openImportPicker()
            return
        }
        if (isRecording && mode != Mode.VIDEO) recording?.stop()
        currentMode = mode
        modeButtons.forEachIndexed { index, button ->
            val active = listOf(Mode.PHOTO, Mode.VIDEO, Mode.BOOTH, Mode.ROLL, Mode.IMPORT)[index] == mode
            button.background = rounded(if (active) Color.rgb(241, 169, 99) else Color.argb(80, 255, 255, 255), dp(10).toFloat())
            button.setTextColor(if (active) Color.BLACK else Color.rgb(211, 218, 225))
        }
        updateControls()
    }

    private fun updateControls() {
        if (!::shutterButton.isInitialized) return
        shutterButton.text = if (isRecording) "■" else if (currentMode == Mode.VIDEO) "●" else ""
        shutterButton.setTextColor(if (currentMode == Mode.VIDEO || isRecording) Color.rgb(205, 48, 52) else Color.BLACK)
        modeLabel.text = when (currentMode) {
            Mode.PHOTO -> "${selectedPreset.title.uppercase(Locale.US)}  ·  READY"
            Mode.VIDEO -> "${selectedPreset.title.uppercase(Locale.US)}  ·  VIDEO"
            Mode.BOOTH -> "PHOTO BOOTH  ·  4 FRAMES"
            Mode.ROLL -> "FILM ROLL  ·  ${rollFrames.size}/12"
            Mode.IMPORT -> "IMPORT"
        }
        flashButton.text = when (flash) { Flash.OFF -> "FLASH OFF"; Flash.AUTO -> "FLASH AUTO"; Flash.ON -> "FLASH ON" }
        timerButton.text = if (timerSeconds == 0) "TIMER OFF" else "${timerSeconds}S"
        ratioButton.text = ratios[ratioIndex]
        gridButton.text = if (gridEnabled) "GRID ON" else "GRID"
        dateButton.text = if (dateStampEnabled) "DATE ON" else "DATE OFF"
        overlayView.setGridEnabled(gridEnabled)
        overlayView.setStampEnabled(dateStampEnabled)
        overlayView.setRecording(isRecording)
    }

    private fun cycleFlash() {
        flash = when (flash) { Flash.OFF -> Flash.AUTO; Flash.AUTO -> Flash.ON; Flash.ON -> Flash.OFF }
        applyFlashMode()
        updateControls()
    }

    private fun applyFlashMode() {
        val capture = imageCapture ?: return
        val shouldFlash = when (flash) {
            Flash.OFF -> false
            Flash.ON -> true
            Flash.AUTO -> ambientLux < AUTO_FLASH_LUX
        }
        capture.flashMode = if (shouldFlash) ImageCapture.FLASH_MODE_ON else ImageCapture.FLASH_MODE_OFF
    }

    private fun cycleTimer() {
        timerSeconds = when (timerSeconds) { 0 -> 3; 3 -> 10; else -> 0 }
        updateControls()
    }

    private fun cycleRatio() {
        ratioIndex = (ratioIndex + 1) % ratios.size
        updateControls()
    }

    private fun toggleGrid() {
        gridEnabled = !gridEnabled
        updateControls()
    }

    private fun toggleDateStamp() {
        dateStampEnabled = !dateStampEnabled
        updateControls()
    }

    private fun onShutterPressed() {
        if (currentMode == Mode.VIDEO) {
            if (isRecording) recording?.stop() else startVideoRecording()
            return
        }
        if (currentMode == Mode.ROLL && rollFrames.size >= FILM_ROLL_SIZE) {
            showDevelopDialog()
            return
        }
        runWithTimer {
            when (currentMode) {
                Mode.PHOTO -> capturePhoto()
                Mode.BOOTH -> capturePhotoBooth()
                Mode.ROLL -> captureRollFrame()
                else -> openImportPicker()
            }
        }
    }

    private fun runWithTimer(action: () -> Unit) {
        if (timerSeconds == 0) {
            shutterFeedback()
            action()
            return
        }
        var remaining = timerSeconds
        countdownLabel.visibility = View.VISIBLE
        countdownLabel.text = remaining.toString()
        val countdown = object : Runnable {
            override fun run() {
                remaining--
                if (remaining <= 0) {
                    countdownLabel.visibility = View.GONE
                    shutterFeedback()
                    action()
                } else {
                    countdownLabel.text = remaining.toString()
                    mainHandler.postDelayed(this, 1000L)
                }
            }
        }
        mainHandler.postDelayed(countdown, 1000L)
    }

    private fun shutterFeedback() {
        shutterButton.performHapticFeedback(HapticFeedbackConstants.CONTEXT_CLICK)
        try {
            val tone = android.media.ToneGenerator(android.media.AudioManager.STREAM_SYSTEM, 65)
            tone.startTone(android.media.ToneGenerator.TONE_PROP_ACK, 90)
            mainHandler.postDelayed({ tone.release() }, 180L)
        } catch (_: Exception) { }
    }

    private fun capturePhoto() {
        captureProcessedFrame { bitmap -> saveBitmapAsync(bitmap, "RETRO") }
    }

    private fun capturePhotoBooth() {
        val frames = mutableListOf<Bitmap>()
        var index = 0
        val total = 4
        fun nextFrame() {
            if (index >= total) {
                countdownLabel.visibility = View.GONE
                val sheet = RetroEffectProcessor.contactSheet(frames, 2, 2)
                frames.forEach { it.recycle() }
                saveBitmapAsync(sheet, "BOOTH")
                return
            }
            countdownLabel.visibility = View.VISIBLE
            countdownLabel.text = (total - index).toString()
            captureProcessedFrame(onSuccess = { bitmap ->
                frames.add(bitmap)
                index++
                mainHandler.postDelayed({ nextFrame() }, 750L)
            })
        }
        nextFrame()
    }

    private fun captureRollFrame() {
        captureProcessedFrame(onSuccess = { bitmap ->
            val maxEdge = 1100
            val scaled = if (maxOf(bitmap.width, bitmap.height) > maxEdge) {
                val scale = maxEdge.toFloat() / maxOf(bitmap.width, bitmap.height)
                Bitmap.createScaledBitmap(bitmap, (bitmap.width * scale).toInt(), (bitmap.height * scale).toInt(), true)
            } else bitmap
            if (scaled !== bitmap) bitmap.recycle()
            rollFrames.add(scaled)
            updateControls()
            toast(tr("Кадр ${rollFrames.size}/12 проявлен в ролле.", "Frame ${rollFrames.size}/12 exposed."))
            if (rollFrames.size == FILM_ROLL_SIZE) showDevelopDialog()
        })
    }

    private fun showDevelopDialog() {
        AlertDialog.Builder(this)
            .setTitle(tr("Плёнка отснята", "Film roll exposed"))
            .setMessage(tr("12 кадров готовы. Запустить проявку и сохранить снимки?", "12 frames are ready. Develop the roll and save the photos?"))
            .setNegativeButton(tr("Позже", "Later"), null)
            .setPositiveButton(tr("Проявить", "Develop")) { _, _ -> developRoll() }
            .show()
    }

    private fun developRoll() {
        if (rollFrames.isEmpty()) return
        val frames = rollFrames.toList()
        rollFrames.clear()
        updateControls()
        ioExecutor.execute {
            try {
                val sheet = RetroEffectProcessor.contactSheet(frames, 3, 4)
                frames.forEachIndexed { index, bitmap ->
                    saveBitmapToMediaStore(bitmap, "ROLL_${index + 1}")
                    bitmap.recycle()
                }
                val sheetUri = saveBitmapToMediaStore(sheet, "FILM_ROLL_CONTACT_SHEET")
                sheet.recycle()
                runOnUiThread {
                    lastSavedUri = sheetUri
                    lastSavedMime = "image/jpeg"
                    toast(tr("Ролл проявлен: 12 фото + контактный лист.", "Roll developed: 12 photos + contact sheet."))
                    openUri(sheetUri, "image/jpeg")
                }
            } catch (error: Exception) {
                frames.forEach { if (!it.isRecycled) it.recycle() }
                runOnUiThread { toast(tr("Ошибка проявки: ${error.localizedMessage}", "Develop error: ${error.localizedMessage}")) }
            }
        }
    }

    private fun captureProcessedFrame(onSuccess: (Bitmap) -> Unit) {
        val capture = imageCapture
        if (capture == null) {
            toast(tr("Камера ещё запускается.", "Camera is still starting."))
            return
        }
        val presetAtCapture = selectedPreset
        val ratioAtCapture = ratios[ratioIndex]
        val frontAtCapture = lensFacing == CameraSelector.LENS_FACING_FRONT
        val stampAtCapture = dateStampEnabled
        val tempFile = File(cacheDir, "capture_${System.nanoTime()}.jpg")
        applyFlashMode()
        val options = ImageCapture.OutputFileOptions.Builder(tempFile).build()
        capture.takePicture(options, cameraExecutor, object : ImageCapture.OnImageSavedCallback {
            override fun onImageSaved(outputFileResults: ImageCapture.OutputFileResults) {
                try {
                    val bitmap = RetroEffectProcessor.render(
                        tempFile, presetAtCapture, ratioAtCapture, frontAtCapture, stampAtCapture
                    )
                    tempFile.delete()
                    runOnUiThread { onSuccess(bitmap) }
                } catch (error: Exception) {
                    tempFile.delete()
                    runOnUiThread { toast(tr("Ошибка обработки кадра: ${error.localizedMessage}", "Frame processing failed: ${error.localizedMessage}")) }
                }
            }

            override fun onError(exception: ImageCaptureException) {
                tempFile.delete()
                runOnUiThread { toast(tr("Снимок не получен: ${exception.message}", "Capture failed: ${exception.message}")) }
            }
        })
    }

    private fun saveBitmapAsync(bitmap: Bitmap, prefix: String) {
        ioExecutor.execute {
            try {
                val uri = saveBitmapToMediaStore(bitmap, prefix)
                bitmap.recycle()
                runOnUiThread {
                    lastSavedUri = uri
                    lastSavedMime = "image/jpeg"
                    modeLabel.text = "${selectedPreset.title.uppercase(Locale.US)}  ·  SAVED"
                    toast(tr("Снимок сохранён в Pictures/RetroCCD", "Saved to Pictures/RetroCCD"))
                }
            } catch (error: Exception) {
                bitmap.recycle()
                runOnUiThread { toast(tr("Не удалось сохранить фото: ${error.localizedMessage}", "Could not save photo: ${error.localizedMessage}")) }
            }
        }
    }

    private fun saveBitmapToMediaStore(bitmap: Bitmap, prefix: String): Uri {
        val stamp = SimpleDateFormat("yyyyMMdd_HHmmss_SSS", Locale.US).format(Date())
        val values = ContentValues().apply {
            put(MediaStore.Images.Media.DISPLAY_NAME, "${prefix}_${stamp}.jpg")
            put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg")
            put(MediaStore.Images.Media.RELATIVE_PATH, "${Environment.DIRECTORY_PICTURES}/RetroCCD")
            put(MediaStore.Images.Media.DATE_TAKEN, System.currentTimeMillis())
            put(MediaStore.Images.Media.IS_PENDING, 1)
        }
        val uri = contentResolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values)
            ?: error("MediaStore insert returned null")
        try {
            contentResolver.openOutputStream(uri, "w")?.use { stream ->
                if (!bitmap.compress(Bitmap.CompressFormat.JPEG, 94, stream)) error("JPEG encoder failed")
            } ?: error("Cannot open output stream")
            val ready = ContentValues().apply { put(MediaStore.Images.Media.IS_PENDING, 0) }
            contentResolver.update(uri, ready, null, null)
            return uri
        } catch (error: Exception) {
            contentResolver.delete(uri, null, null)
            throw error
        }
    }

    private fun startVideoRecording() {
        val capture = videoCapture
        if (capture == null) {
            toast(tr("Эта камера не поддерживает одновременную фотосъёмку и видео.", "Video recording is not supported with this camera configuration."))
            return
        }
        val stamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
        val values = ContentValues().apply {
            put(MediaStore.Video.Media.DISPLAY_NAME, "RETRO_VIDEO_${stamp}.mp4")
            put(MediaStore.Video.Media.MIME_TYPE, "video/mp4")
            put(MediaStore.Video.Media.RELATIVE_PATH, "${Environment.DIRECTORY_MOVIES}/RetroCCD")
            put(MediaStore.Video.Media.IS_PENDING, 1)
        }
        val output = MediaStoreOutputOptions.Builder(contentResolver, MediaStore.Video.Media.EXTERNAL_CONTENT_URI)
            .setContentValues(values)
            .build()
        try {
            recording = capture.output.prepareRecording(this, output)
                .start(ContextCompat.getMainExecutor(this)) { event ->
                    when (event) {
                        is VideoRecordEvent.Start -> {
                            isRecording = true
                            updateControls()
                        }
                        is VideoRecordEvent.Finalize -> {
                            isRecording = false
                            recording = null
                            overlayView.setRecording(false)
                            updateControls()
                            if (!event.hasError()) {
                                val uri = event.outputResults.outputUri
                                val ready = ContentValues().apply { put(MediaStore.Video.Media.IS_PENDING, 0) }
                                contentResolver.update(uri, ready, null, null)
                                lastSavedUri = uri
                                lastSavedMime = "video/mp4"
                                toast(tr("Видео сохранено в Movies/RetroCCD", "Video saved to Movies/RetroCCD"))
                            } else {
                                toast(tr("Ошибка записи видео: ${event.error}", "Video recording error: ${event.error}"))
                            }
                        }
                    }
                }
        } catch (error: Exception) {
            isRecording = false
            updateControls()
            toast(tr("Не удалось начать запись: ${error.localizedMessage}", "Could not start recording: ${error.localizedMessage}"))
        }
    }

    private fun openLatestMedia() {
        val uri = lastSavedUri ?: findLatestMedia()
        if (uri == null) {
            toast(tr("Сохранённых кадров пока нет.", "No saved captures yet."))
            return
        }
        openUri(uri, lastSavedMime)
    }

    private fun findLatestMedia(): Uri? {
        val projection = arrayOf(MediaStore.Images.Media._ID)
        return try {
            contentResolver.query(
                MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                projection,
                "${MediaStore.Images.Media.RELATIVE_PATH} = ?",
                arrayOf("${Environment.DIRECTORY_PICTURES}/RetroCCD/"),
                "${MediaStore.Images.Media.DATE_TAKEN} DESC"
            )?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val id = cursor.getLong(cursor.getColumnIndexOrThrow(MediaStore.Images.Media._ID))
                    Uri.withAppendedPath(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, id.toString())
                } else null
            }
        } catch (_: Exception) { null }
    }

    private fun openUri(uri: Uri, mime: String) {
        try {
            val intent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(uri, mime)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            startActivity(intent)
        } catch (_: Exception) {
            toast(tr("Не найдено приложение для просмотра файла.", "No app is available to open this file."))
        }
    }

    private fun openImportPicker() {
        val intent = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            type = "image/*"
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        try {
            startActivityForResult(intent, REQUEST_IMPORT)
        } catch (_: Exception) {
            toast(tr("Не удалось открыть галерею.", "Could not open image picker."))
        }
    }

    @Deprecated("The classic result callback is retained for broad Android API compatibility in this MVP.")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode != REQUEST_IMPORT || resultCode != RESULT_OK) return
        val uri = data?.data ?: return
        ioExecutor.execute {
            val temp = File(cacheDir, "import_${System.nanoTime()}.jpg")
            try {
                contentResolver.openInputStream(uri)?.use { input -> temp.outputStream().use { output -> input.copyTo(output) } }
                    ?: error("Cannot read selected image")
                val bitmap = RetroEffectProcessor.render(temp, selectedPreset, ratios[ratioIndex], false, dateStampEnabled)
                temp.delete()
                runOnUiThread { saveBitmapAsync(bitmap, "IMPORT") }
            } catch (error: Exception) {
                temp.delete()
                runOnUiThread { toast(tr("Не удалось обработать изображение: ${error.localizedMessage}", "Could not process image: ${error.localizedMessage}")) }
            }
        }
        chooseMode(Mode.PHOTO)
    }

    private fun toast(message: String) = Toast.makeText(this, message, Toast.LENGTH_SHORT).show()

    override fun onSensorChanged(event: SensorEvent) {
        if (event.sensor?.type == Sensor.TYPE_LIGHT) {
            ambientLux = event.values.firstOrNull() ?: ambientLux
            if (flash == Flash.AUTO) applyFlashMode()
        }
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit

    companion object {
        private const val REQUEST_CAMERA = 1001
        private const val REQUEST_IMPORT = 1002
        private const val FILM_ROLL_SIZE = 12
        private const val AUTO_FLASH_LUX = 24f
    }
}
