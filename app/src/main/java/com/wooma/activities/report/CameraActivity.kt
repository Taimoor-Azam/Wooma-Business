package com.wooma.activities.report

import android.Manifest
import androidx.core.content.res.ResourcesCompat
import android.content.pm.PackageManager
import android.media.AudioManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.RectF
import androidx.exifinterface.media.ExifInterface
import android.media.MediaActionSound
import android.net.Uri
import android.os.Bundle
import android.view.OrientationEventListener
import android.view.Surface
import android.view.View
import androidx.activity.OnBackPressedCallback
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.Camera
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.LinearLayoutManager
import com.wooma.R
import com.wooma.activities.BaseActivity
import com.wooma.adapter.ImageAdapter
import com.wooma.model.ImageItem
import com.wooma.data.network.showToast
import com.wooma.databinding.ActivityCameraBinding
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class CameraActivity : BaseActivity() {
    private lateinit var binding: ActivityCameraBinding

    private var imageCapture: ImageCapture? = null
    private lateinit var camera: Camera

    private val images = mutableListOf<ImageItem>()
    private val sessionLocalUris = mutableListOf<Uri>() // Only newly added images this session
    private lateinit var adapter: ImageAdapter

    private var flashEnabled = false
    private var isCoverImage = false
    private var isZoom2x = false
    private var showTimestamp = true

    val imageLimit = 50
    private val shutterSound = MediaActionSound()

    companion object {
        val pendingUris = mutableListOf<Uri>()
        var existingImages = listOf<ImageItem>()
        val resultImages = mutableListOf<ImageItem>()
    }

    private val cameraPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { isGranted ->
            if (isGranted) startCamera()
            else showToast("Camera permission denied")
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityCameraBinding.inflate(layoutInflater)
        setContentView(binding.root)
        applyWindowInsetsToBinding(binding.root)

        isCoverImage = intent.getBooleanExtra("isCoverImage", false)
        showTimestamp = intent.getBooleanExtra("showTimestamp", true)

        if (isCoverImage) {
            binding.btnDone.visibility = View.GONE
            binding.recyclerImages.visibility = View.GONE
            binding.txtCounter.visibility = View.GONE
        } else {
            images.addAll(existingImages)
        }

        setupRecycler()
        checkCameraPermission()

        binding.btnCapture.setOnClickListener { takePhoto() }
        binding.btnGallery.setOnClickListener { openGallery() }
        binding.btnFlash.setOnClickListener { toggleFlash() }
        binding.btn1x.setOnClickListener { setZoom(false) }
        binding.btn2x.setOnClickListener { setZoom(true) }

        binding.btnBack.setOnClickListener {
            saveAndExit()
        }

        binding.btnDone.setOnClickListener {
            saveAndExit()
        }

        val callback = object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                saveAndExit()
                // If you want default behavior after your logic:
                isEnabled = false
                onBackPressedDispatcher.onBackPressed()
            }
        }

        onBackPressedDispatcher.addCallback(this, callback)
    }

    private fun saveAndExit() {
        pendingUris.clear()
        pendingUris.addAll(sessionLocalUris)

        resultImages.clear()
        resultImages.addAll(images)

        setResult(RESULT_OK)
        finish()
    }

    private fun checkCameraPermission() {
        if (ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.CAMERA
            ) == PackageManager.PERMISSION_GRANTED
        )
            startCamera()
        else
            cameraPermissionLauncher.launch(Manifest.permission.CAMERA)
    }

    private fun startCamera() {
        val cameraProviderFuture = ProcessCameraProvider.getInstance(this)
        cameraProviderFuture.addListener({
            val cameraProvider = cameraProviderFuture.get()
            val preview = Preview.Builder().build()

            val rotation = binding.previewView.display?.rotation ?: Surface.ROTATION_0

            val capture = ImageCapture.Builder()
                .setFlashMode(ImageCapture.FLASH_MODE_OFF)
                .setJpegQuality(80)
                .setTargetRotation(rotation)
                .build()

            imageCapture = capture

            preview.surfaceProvider = binding.previewView.surfaceProvider
            cameraProvider.unbindAll()
            camera = cameraProvider.bindToLifecycle(
                this,
                CameraSelector.DEFAULT_BACK_CAMERA,
                preview,
                capture
            )
            camera.cameraControl.setZoomRatio(if (isZoom2x) 2f else 1f)
        }, ContextCompat.getMainExecutor(this))
    }

    private val orientationEventListener by lazy {
        object : OrientationEventListener(this) {
            override fun onOrientationChanged(orientation: Int) {
                if (orientation == ORIENTATION_UNKNOWN) return

                val rotation = when (orientation) {
                    in 45..134 -> Surface.ROTATION_270
                    in 135..224 -> Surface.ROTATION_180
                    in 225..314 -> Surface.ROTATION_90
                    else -> Surface.ROTATION_0
                }

                imageCapture?.targetRotation = rotation
            }
        }
    }

    override fun onStart() {
        super.onStart()
        orientationEventListener.enable()
    }

    override fun onStop() {
        super.onStop()
        orientationEventListener.disable()
    }

    private fun takePhoto() {
        if (images.size >= imageLimit) return
        val capture = imageCapture ?: return

        val audioManager = getSystemService(AUDIO_SERVICE) as AudioManager
        if (audioManager.ringerMode == AudioManager.RINGER_MODE_NORMAL) {
            shutterSound.play(MediaActionSound.SHUTTER_CLICK)
        }

        val file = createTempImageFile()
        val output = ImageCapture.OutputFileOptions.Builder(file).build()

        capture.takePicture(
            output, ContextCompat.getMainExecutor(this),
            object : ImageCapture.OnImageSavedCallback {
                override fun onImageSaved(result: ImageCapture.OutputFileResults) {
                    val fixedFile = fixOrientationToPortrait(file)
                    val stampedFile =
                        if (isCoverImage || !showTimestamp) fixedFile else stampDateTimeOnImage(fixedFile)
                    val uri = Uri.fromFile(stampedFile)

                    images.add(ImageItem.Local(uri))
                    sessionLocalUris.add(uri)
                    adapter.notifyItemInserted(images.size - 1)
                    updateCounter()

                    if (isCoverImage) {
                        pendingUris.clear()
                        pendingUris.add(uri)
                        setResult(RESULT_OK)
                        finish()
                    }
                }

                override fun onError(exc: ImageCaptureException) {
                    exc.printStackTrace()
                }
            }
        )
    }

    private fun stampDateTimeOnImage(originalFile: File): File {
        val original = BitmapFactory.decodeFile(originalFile.absolutePath)
        val mutable = original.copy(Bitmap.Config.ARGB_8888, true)
        val canvas = Canvas(mutable)

        val dateText =
            SimpleDateFormat("dd  MMM  yyyy,  HH:mm:ss", Locale.getDefault()).format(Date())

        val scale = mutable.width / 1080f
        val textSizePx = 40f * scale
        val paddingH = 50f * scale
        val paddingV = 14f * scale
        val cornerRadius = 10f * scale
        val marginLeft = 30f * scale
        val marginBottom = 55f * scale

        val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            textSize = textSizePx
            typeface = ResourcesCompat.getFont(this@CameraActivity, R.font.sofiasans_bold)
        }

        val textWidth = textPaint.measureText(dateText)
        val textHeight = textPaint.descent() - textPaint.ascent()

        val rectLeft = marginLeft
        val rectBottom = mutable.height - marginBottom
        val rectRight = rectLeft + textWidth + paddingH * 2
        val rectTop = rectBottom - textHeight - paddingV * 2

        val bgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#80000000")
        }
        canvas.drawRoundRect(
            RectF(rectLeft, rectTop, rectRight, rectBottom),
            cornerRadius,
            cornerRadius,
            bgPaint
        )

        val textX = rectLeft + paddingH
        val textY = rectBottom - paddingV - textPaint.descent()
        canvas.drawText(dateText, textX, textY, textPaint)

        val stampedFile = createTempImageFile(prefix = "stamped_")
        FileOutputStream(stampedFile).use { out ->
            mutable.compress(Bitmap.CompressFormat.JPEG, 80, out)
        }
        original.recycle()
        mutable.recycle()
        originalFile.delete()
        return stampedFile
    }

    private fun toggleFlash() {
        val capture = imageCapture ?: return
        flashEnabled = !flashEnabled
        capture.flashMode =
            if (flashEnabled) ImageCapture.FLASH_MODE_ON else ImageCapture.FLASH_MODE_OFF
        binding.btnFlash.setImageResource(if (flashEnabled) R.drawable.svg_flash else R.drawable.svg_flash_active)
    }

    private fun setZoom(zoom2x: Boolean) {
        isZoom2x = zoom2x
        camera.cameraControl.setZoomRatio(if (zoom2x) 2f else 1f)
        updateZoomButtons()
    }

    private fun updateZoomButtons() {
        if (isZoom2x) {
            binding.btn1x.setBackgroundResource(android.R.color.transparent)
            binding.btn1x.setTextColor(ContextCompat.getColor(this, R.color.white))
            binding.btn2x.setBackgroundResource(R.drawable.bg_zoom_selected)
            binding.btn2x.setTextColor(ContextCompat.getColor(this, R.color.black))
        } else {
            binding.btn1x.setBackgroundResource(R.drawable.bg_zoom_selected)
            binding.btn1x.setTextColor(ContextCompat.getColor(this, R.color.black))
            binding.btn2x.setBackgroundResource(android.R.color.transparent)
            binding.btn2x.setTextColor(ContextCompat.getColor(this, R.color.white))
        }
    }

    private fun setupRecycler() {
        adapter = ImageAdapter(images, showDelete = true, onDelete = {
            sessionLocalUris.clear()
            val sessionImages =
                images.filterIsInstance<ImageItem.Local>().map { it.uri }.filter { uri ->
                    existingImages.filterIsInstance<ImageItem.Local>().none { it.uri == uri }
                }
            sessionLocalUris.addAll(sessionImages)
            updateCounter()
        })
        binding.recyclerImages.layoutManager =
            LinearLayoutManager(this, LinearLayoutManager.HORIZONTAL, false)
        binding.recyclerImages.adapter = adapter
        updateCounter()
    }

    private fun updateCounter() {
        val size = images.size
        binding.txtCounter.text = "$size/$imageLimit"

        if (!isCoverImage) {
            binding.previewCard.visibility = if (size >= imageLimit) View.GONE else View.VISIBLE
        }
    }

    private val coverGalleryLauncher =
        registerForActivityResult(ActivityResultContracts.GetContent()) { uri ->
            uri?.let {
                binding.progressBar.visibility = View.VISIBLE
                lifecycleScope.launch(Dispatchers.IO) {
                    try {
                        val file = fixOrientationToPortrait(copyUriToFile(it))
                        val localItem = ImageItem.Local(Uri.fromFile(file))

                        withContext(Dispatchers.Main) {
                            images.add(localItem)
                            sessionLocalUris.add(localItem.uri)
                            adapter.notifyItemInserted(images.size - 1)
                            updateCounter()
                            pendingUris.clear()
                            pendingUris.add(localItem.uri)
                            binding.progressBar.visibility = View.GONE
                            setResult(RESULT_OK)
                            finish()
                        }
                    } catch (e: Exception) {
                        withContext(Dispatchers.Main) {
                            binding.progressBar.visibility = View.GONE
                            showToast("Failed to process image")
                        }
                    }
                }
            }
        }

    private val multiGalleryLauncher =
        registerForActivityResult(ActivityResultContracts.PickMultipleVisualMedia(imageLimit)) { uris ->
            if (uris.isNullOrEmpty()) return@registerForActivityResult
            
            val remainingSlots = imageLimit - images.size
            if (remainingSlots <= 0) {
                showToast("Limit reached")
                return@registerForActivityResult
            }
            
            val limitedUris = uris.take(remainingSlots)
            
            binding.progressBar.visibility = View.VISIBLE
            lifecycleScope.launch(Dispatchers.IO) {
                try {
                    for (uri in limitedUris) {
                        val file = fixOrientationToPortrait(copyUriToFile(uri))
                        val stampedFile = if (showTimestamp) stampDateTimeOnImage(file) else file
                        val localItem = ImageItem.Local(Uri.fromFile(stampedFile))

                        withContext(Dispatchers.Main) {
                            images.add(localItem)
                            sessionLocalUris.add(localItem.uri)
                            adapter.notifyItemInserted(images.size - 1)
                            updateCounter()
                            binding.recyclerImages.scrollToPosition(images.size - 1)
                        }
                    }
                    
                    if (uris.size > remainingSlots) {
                        withContext(Dispatchers.Main) {
                            showToast("Only $remainingSlots images were added (Limit: $imageLimit)")
                        }
                    }
                } catch (e: Exception) {
                    withContext(Dispatchers.Main) {
                        showToast("Failed to process some images")
                    }
                } finally {
                    withContext(Dispatchers.Main) {
                        binding.progressBar.visibility = View.GONE
                    }
                }
            }
        }

    private fun openGallery() {
        if (isCoverImage) coverGalleryLauncher.launch("image/*")
        else {
            val remaining = imageLimit - images.size
            if (remaining <= 0) {
                showToast("Limit reached")
                return
            }
            multiGalleryLauncher.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
        }
    }

    private fun createTempImageFile(prefix: String = "temp_"): File {
        val storageDir = getExternalFilesDir(null)
        return File.createTempFile(prefix, ".jpg", storageDir)
    }

    private fun copyUriToFile(uri: Uri): File {
        val file = createTempImageFile("gallery_")
        contentResolver.openInputStream(uri)?.use { input ->
            FileOutputStream(file).use { output ->
                input.copyTo(output)
            }
        }
        return file
    }

    private fun fixOrientationToPortrait(file: File): File {
        val exifInterface = ExifInterface(file.absolutePath)
        val orientation = exifInterface.getAttributeInt(
            ExifInterface.TAG_ORIENTATION,
            ExifInterface.ORIENTATION_UNDEFINED
        )

        val bitmap = BitmapFactory.decodeFile(file.absolutePath)
        val matrix = Matrix()
        when (orientation) {
            ExifInterface.ORIENTATION_ROTATE_90 -> matrix.postRotate(90f)
            ExifInterface.ORIENTATION_ROTATE_180 -> matrix.postRotate(180f)
            ExifInterface.ORIENTATION_ROTATE_270 -> matrix.postRotate(270f)
        }

        val rotatedBitmap =
            Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)

        // Ensure it's portrait
        val finalBitmap = if (rotatedBitmap.width > rotatedBitmap.height) {
            val portraitMatrix = Matrix()
            portraitMatrix.postRotate(90f)
            Bitmap.createBitmap(
                rotatedBitmap,
                0,
                0,
                rotatedBitmap.width,
                rotatedBitmap.height,
                portraitMatrix,
                true
            )
        } else {
            rotatedBitmap
        }

        val portraitFile = createTempImageFile("portrait_")
        FileOutputStream(portraitFile).use { out ->
            finalBitmap.compress(Bitmap.CompressFormat.JPEG, 80, out)
        }

        bitmap.recycle()
        if (rotatedBitmap != finalBitmap) rotatedBitmap.recycle()
        finalBitmap.recycle()
        file.delete()

        return portraitFile
    }
}
