package com.wooma.business.activities.report

import android.Manifest
import androidx.core.content.res.ResourcesCompat
import android.content.Context
import android.content.pm.PackageManager
import android.media.AudioManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import androidx.exifinterface.media.ExifInterface
import android.media.MediaActionSound
import android.net.Uri
import android.os.Bundle
import android.view.View
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.Camera
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.LinearLayoutManager
import com.wooma.business.R
import com.wooma.business.activities.BaseActivity
import com.wooma.business.adapter.ImageAdapter
import com.wooma.business.model.ImageItem
import com.wooma.business.data.network.showToast
import com.wooma.business.databinding.ActivityCameraBinding
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

    private lateinit var imageCapture: ImageCapture
    private lateinit var camera: Camera

    private val images = mutableListOf<ImageItem>()
    private lateinit var adapter: ImageAdapter

    private var flashEnabled = false
    private var isCoverImage = false
    private var isZoom2x = false

    private val shutterSound = MediaActionSound()

    companion object {
        val pendingUris = mutableListOf<Uri>()
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

        if (isCoverImage) {
            binding.btnDone.visibility = View.GONE
            binding.recyclerImages.visibility = View.GONE
            binding.txtCounter.visibility = View.GONE
        }

        setupRecycler()
        checkCameraPermission()

        binding.btnCapture.setOnClickListener { takePhoto() }
        binding.btnGallery.setOnClickListener { openGallery() }
        binding.btnFlash.setOnClickListener { toggleFlash() }
        binding.btn1x.setOnClickListener { setZoom(false) }
        binding.btn2x.setOnClickListener { setZoom(true) }

        binding.btnBack.setOnClickListener {
            setResult(RESULT_CANCELED)
            finish()
        }

        binding.btnDone.setOnClickListener {
            pendingUris.clear()
            pendingUris.addAll(images.filterIsInstance<ImageItem.Local>().map { it.uri })
            setResult(RESULT_OK)
            finish()
        }
    }

    private fun checkCameraPermission() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED)
            startCamera()
        else
            cameraPermissionLauncher.launch(Manifest.permission.CAMERA)
    }

    private fun startCamera() {
        val cameraProviderFuture = ProcessCameraProvider.getInstance(this)
        cameraProviderFuture.addListener({
            val cameraProvider = cameraProviderFuture.get()
            val preview = Preview.Builder().build()
            imageCapture = ImageCapture.Builder()
                .setFlashMode(ImageCapture.FLASH_MODE_OFF)
                .setJpegQuality(80)
                .build()

            preview.surfaceProvider = binding.previewView.surfaceProvider
            cameraProvider.unbindAll()
            camera = cameraProvider.bindToLifecycle(this, CameraSelector.DEFAULT_BACK_CAMERA, preview, imageCapture)

            // Apply current zoom after camera is ready
            camera.cameraControl.setZoomRatio(if (isZoom2x) 2f else 1f)
        }, ContextCompat.getMainExecutor(this))
    }

    private fun takePhoto() {
        if (images.size >= 50) return

        val audioManager = getSystemService(Context.AUDIO_SERVICE) as AudioManager
        if (audioManager.ringerMode == AudioManager.RINGER_MODE_NORMAL) {
            shutterSound.play(MediaActionSound.SHUTTER_CLICK)
        }

        val file = createTempImageFile()
        val output = ImageCapture.OutputFileOptions.Builder(file).build()

        imageCapture.takePicture(output, ContextCompat.getMainExecutor(this),
            object : ImageCapture.OnImageSavedCallback {
                override fun onImageSaved(result: ImageCapture.OutputFileResults) {
                    val fixedFile = fixOrientationToPortrait(file)
                    val stampedFile = if (isCoverImage) fixedFile else stampDateTimeOnImage(fixedFile)
                    val uri = Uri.fromFile(stampedFile)

                    images.add(ImageItem.Local(uri))
                    adapter.notifyItemInserted(images.size - 1)
                    updateCounter()

                    if (isCoverImage) {
                        pendingUris.clear()
                        pendingUris.addAll(images.filterIsInstance<ImageItem.Local>().map { it.uri })
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

        val dateText = SimpleDateFormat("dd  MMM  yyyy,  HH:mm:ss", Locale.getDefault()).format(Date())

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
        canvas.drawRoundRect(RectF(rectLeft, rectTop, rectRight, rectBottom), cornerRadius, cornerRadius, bgPaint)

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
        flashEnabled = !flashEnabled
        imageCapture.flashMode = if (flashEnabled) ImageCapture.FLASH_MODE_ON else ImageCapture.FLASH_MODE_OFF
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
        adapter = ImageAdapter(images, onDelete = { updateCounter() })
        binding.recyclerImages.layoutManager = LinearLayoutManager(this, LinearLayoutManager.HORIZONTAL, false)
        binding.recyclerImages.adapter = adapter
    }

    private fun updateCounter() {
        binding.txtCounter.text = "${images.size}/50"
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
                            adapter.notifyItemInserted(images.size - 1)
                            updateCounter()
                            pendingUris.clear()
                            pendingUris.addAll(images.filterIsInstance<ImageItem.Local>().map { img -> img.uri })
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
        registerForActivityResult(ActivityResultContracts.GetMultipleContents()) { uris ->
            if (uris.isNullOrEmpty()) return@registerForActivityResult
            binding.progressBar.visibility = View.VISIBLE
            lifecycleScope.launch(Dispatchers.IO) {
                try {
                    for (uri in uris) {
                        if (images.size >= 50) break
                        val file = fixOrientationToPortrait(copyUriToFile(uri))
                        val stampedFile = stampDateTimeOnImage(file)
                        val localItem = ImageItem.Local(Uri.fromFile(stampedFile))

                        withContext(Dispatchers.Main) {
                            images.add(localItem)
                            adapter.notifyItemInserted(images.size - 1)
                            updateCounter()
                            binding.recyclerImages.scrollToPosition(images.size - 1)
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
        else multiGalleryLauncher.launch("image/*")
    }

    private fun fixOrientationToPortrait(file: File): File {
        val exif = ExifInterface(file.absolutePath)
        val orientation = exif.getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)
        val rotationDegrees = when (orientation) {
            ExifInterface.ORIENTATION_ROTATE_90 -> 90f
            ExifInterface.ORIENTATION_ROTATE_180 -> 180f
            ExifInterface.ORIENTATION_ROTATE_270 -> 270f
            else -> 0f
        }

        val options = BitmapFactory.Options().apply {
            inJustDecodeBounds = true
        }
        BitmapFactory.decodeFile(file.absolutePath, options)

        // Calculate inSampleSize to downsample if the image is very large
        options.inSampleSize = calculateInSampleSize(options, 1920, 1920)
        options.inJustDecodeBounds = false
        
        var bitmap = BitmapFactory.decodeFile(file.absolutePath, options) ?: return file

        if (rotationDegrees != 0f) {
            val matrix = Matrix()
            matrix.postRotate(rotationDegrees)
            val rotated = Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
            bitmap.recycle()
            bitmap = rotated
        }

        // If still landscape after EXIF correction, rotate 90° clockwise to portrait
        if (bitmap.width > bitmap.height) {
            val matrix = Matrix()
            matrix.postRotate(90f)
            val rotated = Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
            bitmap.recycle()
            bitmap = rotated
        }
        
        // Ensure orientation in EXIF is set to normal (portrait) for the new file
        val outFile = createTempImageFile(prefix = "portrait_")
        FileOutputStream(outFile).use { out -> bitmap.compress(Bitmap.CompressFormat.JPEG, 80, out) }
        
        try {
            val newExif = ExifInterface(outFile.absolutePath)
            newExif.setAttribute(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL.toString())
            newExif.saveAttributes()
        } catch (e: Exception) {
            e.printStackTrace()
        }

        bitmap.recycle()
        file.delete()
        return outFile
    }

    private fun calculateInSampleSize(options: BitmapFactory.Options, reqWidth: Int, reqHeight: Int): Int {
        val (height: Int, width: Int) = options.outHeight to options.outWidth
        var inSampleSize = 1

        if (height > reqHeight || width > reqWidth) {
            val halfHeight: Int = height / 2
            val halfWidth: Int = width / 2
            while (halfHeight / inSampleSize >= reqHeight && halfWidth / inSampleSize >= reqWidth) {
                inSampleSize *= 2
            }
        }
        return inSampleSize
    }

    private fun copyUriToFile(uri: Uri): File {
        val input = contentResolver.openInputStream(uri) ?: error("Cannot open URI: $uri")
        val file = createTempImageFile(prefix = "gallery_")
        file.outputStream().use { out -> input.use { it.copyTo(out) } }
        return file
    }

    private fun createTempImageFile(prefix: String = ""): File {
        val tempDir = File(cacheDir, "camera_images").apply {
            if (!exists()) {
                mkdirs()
                try {
                    File(this, ".nomedia").createNewFile()
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
        }
        return File(tempDir, "${prefix}${System.currentTimeMillis()}.jpg")
    }

    override fun onDestroy() {
        super.onDestroy()
        shutterSound.release()
    }
}
