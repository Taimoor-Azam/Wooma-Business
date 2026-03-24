package com.wooma.business.activities.report

import android.Manifest
import android.content.pm.PackageManager
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
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.wooma.business.activities.BaseActivity
import com.wooma.business.adapter.ImageAdapter
import com.wooma.business.data.network.showToast
import com.wooma.business.databinding.ActivityCameraBinding
import java.io.File

class CameraActivity : BaseActivity() {
    private lateinit var binding: ActivityCameraBinding

    lateinit var bottomSheetBehavior: BottomSheetBehavior<View>

    private lateinit var imageCapture: ImageCapture
    private lateinit var camera: Camera

    private val images = mutableListOf<Uri>()
    private lateinit var adapter: ImageAdapter

    private var flashEnabled = false

    private val cameraPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { isGranted ->

            if (isGranted) {
                startCamera()
            } else {
                showToast("Camera permission denied")
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityCameraBinding.inflate(layoutInflater)
        setContentView(binding.root)
        applyWindowInsetsToBinding(binding.root)

        bottomSheetBehavior = BottomSheetBehavior.from(binding.bottomSheet)

        bottomSheetBehavior.peekHeight = 150
        bottomSheetBehavior.state = BottomSheetBehavior.STATE_COLLAPSED

        setupRecycler()
        checkCameraPermission()
//        startCamera()

        binding.btnCapture.setOnClickListener { takePhoto() }

        binding.btnGallery.setOnClickListener { openGallery() }

        binding.btnFlash.setOnClickListener { toggleFlash() }
        binding.btnBack.setOnClickListener { finish() }

//        binding.zoomHalf.setOnClickListener { zoom(0.5f) }
//        binding.zoom1.setOnClickListener { zoom(1f) }
//        binding.zoom2.setOnClickListener { zoom(2f) }

        bottomSheetBehavior.addBottomSheetCallback(object :
            BottomSheetBehavior.BottomSheetCallback() {
            override fun onStateChanged(bottomSheet: View, newState: Int) {
                when (newState) {
                    BottomSheetBehavior.STATE_EXPANDED -> {
                        binding.recyclerImages.layoutManager =
                            GridLayoutManager(this@CameraActivity, 3)

                    }

                    BottomSheetBehavior.STATE_COLLAPSED -> {
                        binding.recyclerImages.layoutManager =
                            LinearLayoutManager(
                                this@CameraActivity,
                                LinearLayoutManager.HORIZONTAL,
                                false
                            )
                    }
                }
            }

            override fun onSlide(bottomSheet: View, slideOffset: Float) {}
        })


    }

    private fun checkCameraPermission() {
        if (ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.CAMERA
            ) == PackageManager.PERMISSION_GRANTED
        ) {

            startCamera()

        } else {

            cameraPermissionLauncher.launch(Manifest.permission.CAMERA)

        }
    }

    private fun startCamera() {
        val cameraProviderFuture = ProcessCameraProvider.getInstance(this)

        cameraProviderFuture.addListener({
            val cameraProvider = cameraProviderFuture.get()
            val preview = Preview.Builder().build()
            imageCapture = ImageCapture.Builder()
                .setFlashMode(ImageCapture.FLASH_MODE_OFF)
                .build()

            val selector = CameraSelector.DEFAULT_BACK_CAMERA
            preview.surfaceProvider = binding.previewView.surfaceProvider

            cameraProvider.unbindAll()

            camera = cameraProvider.bindToLifecycle(
                this,
                selector,
                preview,
                imageCapture
            )

        }, ContextCompat.getMainExecutor(this))
    }

    private fun takePhoto() {
        if (images.size >= 50) return

        val file = File(externalMediaDirs.first(), "${System.currentTimeMillis()}.jpg")
        val output = ImageCapture.OutputFileOptions.Builder(file).build()

        imageCapture.takePicture(
            output,
            ContextCompat.getMainExecutor(this),
            object : ImageCapture.OnImageSavedCallback {
                override fun onImageSaved(result: ImageCapture.OutputFileResults) {
                    val uri = Uri.fromFile(file)

                    images.add(uri)
                    adapter.notifyItemInserted(images.size - 1)

                    updateCounter()
                }

                override fun onError(exc: ImageCaptureException) {
                    exc.printStackTrace()
                }
            }
        )
    }

    private fun toggleFlash() {
        flashEnabled = !flashEnabled

        imageCapture.flashMode =
            if (flashEnabled)
                ImageCapture.FLASH_MODE_ON
            else
                ImageCapture.FLASH_MODE_OFF
    }

    private fun zoom(value: Float) {
        val cameraControl = camera.cameraControl
        val cameraInfo = camera.cameraInfo

        val zoomState = cameraInfo.zoomState.value

        zoomState?.let {
            cameraControl.setZoomRatio(value)
        }
    }

    private fun setupRecycler() {
        adapter = ImageAdapter(images)

        binding.recyclerImages.layoutManager =
            LinearLayoutManager(this, LinearLayoutManager.HORIZONTAL, false)

        binding.recyclerImages.adapter = adapter
    }

    private fun updateCounter() {
        binding.txtCounter.text = "${images.size}/50"
    }

    private val galleryLauncher =
        registerForActivityResult(ActivityResultContracts.GetContent()) { uri ->
            uri?.let {
                images.add(it)
                adapter.notifyItemInserted(images.size - 1)
                updateCounter()
            }
        }

    private fun openGallery() {
        galleryLauncher.launch("image/*")
    }
}