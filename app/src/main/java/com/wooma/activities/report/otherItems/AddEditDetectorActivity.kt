package com.wooma.activities.report.otherItems

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.View
import androidx.activity.OnBackPressedCallback
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.wooma.activities.BaseActivity
import com.wooma.activities.report.CameraActivity
import com.wooma.adapter.ImageAdapter
import com.wooma.adapter.SuggestionsAdapter
import com.wooma.customs.Utils
import com.wooma.data.local.WoomaDatabase
import com.wooma.data.network.ApiClient
import com.wooma.data.network.showToast
import com.wooma.data.repository.AttachmentRepository
import com.wooma.data.repository.OtherItemsRepository
import com.wooma.databinding.ActivityAddEditDetectorBinding
import com.wooma.databinding.AddImageLayoutBinding
import com.wooma.model.AddDetectorRequest
import com.wooma.model.DetectorItem
import com.wooma.model.ImageItem
import com.wooma.sync.SyncScheduler
import kotlinx.coroutines.launch

class AddEditDetectorActivity : BaseActivity() {
    private lateinit var binding: ActivityAddEditDetectorBinding
    private lateinit var cameraBinding: AddImageLayoutBinding
    var detectorItem: DetectorItem? = null

    private val capturedUris = mutableListOf<Uri>()
    private val allImages = mutableListOf<ImageItem>()
    private val CAMERA_REQUEST = 1001

    var reportId = ""
    var showTimestamp = true
    val suggestionList =
        mutableListOf(
            "Smoke Alarm",
            "Carbon Monoxide Detector",
            "Heat Detector",
            "Smoke and Heat Detector",
            "Carbon Monoxide and Smoke Alarm",
            "Fire Alarm",
            "Combined Smoke and CO Detector",
            "Ionization Smoke Alarm",
            "Photoelectric Smoke Alarm",
            "Gas Detector",
            "Radon Detector",
            "Water Leak Detector",
            "Motion Detector",
            "Security Alarm"
        )

    val locationSuggestionList = mutableListOf(
        "Living Room", "Dining Room", "Kitchen", "Hallway", "Landing", "Entrance Hall",
        "Utility Room", "Conservatory", "Study", "Office", "Garage", "Basement", "Attic", "Loft",
        "Master Bedroom", "Bedroom 1", "Bedroom 2", "Bedroom 3", "Bedroom 4", "Guest Bedroom",
        "Bathroom", "Ensuite", "WC", "Downstairs WC", "Family Bathroom", "Main Bathroom",
        "Front Garden", "Back Garden", "Shed", "Outbuilding", "Summer House"
    )

    val testResultSuggestionList = mutableListOf(
        "Detector tested. Audible alarm noted. Operating correctly.",
        "Detector tested. Unit failed to activate. Requires replacement.",
        "Detector present. Testing not completed at time of inspection.",
        "No detector fitted at this location.",
        "Detector tested. Audible alarm noted. Low battery warning detected.",
        "Detector present but inaccessible for testing."
    )

    private lateinit var suggestionsAdapter: SuggestionsAdapter
    private lateinit var locationSuggestionsAdapter: SuggestionsAdapter
    private lateinit var testResultSuggestionsAdapter: SuggestionsAdapter

    var isEdit = false
    private var hasChanges = false

    private val repo by lazy { OtherItemsRepository(this) }
    private val attachmentRepo by lazy { AttachmentRepository(this) }
    private val db by lazy { WoomaDatabase.getInstance(this) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        binding = ActivityAddEditDetectorBinding.inflate(layoutInflater)
        setContentView(binding.root)
        cameraBinding = binding.cameraLayout
        applyWindowInsetsToBinding(binding.root)
        setupCapturedImagesRecycler()
        detectorItem = intent.getParcelableExtra("detectorItem")

        reportId = intent.getStringExtra("reportId") ?: ""
        showTimestamp = intent.getBooleanExtra("showTimestamp", true)
        isEdit = intent.getBooleanExtra("isEdit", false)

        cameraBinding.ivAddImage.setOnClickListener {
            CameraActivity.Companion.existingImages = allImages.toList()
            CameraActivity.Companion.pendingUris.clear()
            startActivityForResult(
                Intent(this, CameraActivity::class.java).putExtra(
                    "showTimestamp",
                    showTimestamp
                ), CAMERA_REQUEST
            )
        }

        binding.btnSave.setOnClickListener {
            saveDetector()
        }

        binding.ivDelete.visibility = if (detectorItem != null) {
            View.VISIBLE
        } else {
            View.GONE
        }

        binding.ivDelete.setOnClickListener {
            deleteDetector()
        }

        suggestionsAdapter = SuggestionsAdapter(
            this,
            suggestionList,
            object : SuggestionsAdapter.OnItemClickInterface {
                override fun onItemClick(item: String) {
                    binding.etType.setText(item)
                    binding.etType.text?.let { binding.etType.setSelection(it.length) }
                    suggestionsAdapter.filter("")
                }
            })
        binding.rvSuggestions.adapter = suggestionsAdapter

        binding.etType.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) {
                suggestionsAdapter.filter(s?.toString() ?: "")
            }
        })

        locationSuggestionsAdapter = SuggestionsAdapter(
            this,
            locationSuggestionList,
            object : SuggestionsAdapter.OnItemClickInterface {
                override fun onItemClick(item: String) {
                    binding.etLocation.setText(item)
                    binding.etLocation.text?.let { binding.etLocation.setSelection(it.length) }
                    locationSuggestionsAdapter.filter("")
                }
            })
        binding.rvLocationSuggestions.adapter = locationSuggestionsAdapter

        binding.etLocation.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) {
                locationSuggestionsAdapter.filter(s?.toString() ?: "")
            }
        })

        testResultSuggestionsAdapter = SuggestionsAdapter(
            this,
            testResultSuggestionList,
            object : SuggestionsAdapter.OnItemClickInterface {
                override fun onItemClick(item: String) {
                    binding.etTestResult.setText(item)
                    binding.etTestResult.text?.let { binding.etTestResult.setSelection(it.length) }
                    testResultSuggestionsAdapter.filter("")
                }
            })
        binding.rvTestResultSuggestions.adapter = testResultSuggestionsAdapter

        binding.etTestResult.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) {
                testResultSuggestionsAdapter.filter(s?.toString() ?: "")
            }
        })

        binding.ivBack.setOnClickListener {
            if (hasChanges) showUnsavedChangesDialog { finish() } else finish()
        }

        setDetectorData()
        attachChangeWatchers()

        val callback = object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                if (hasChanges)
                    showUnsavedChangesDialog {
                        isEnabled = false
                        onBackPressedDispatcher.onBackPressed()
                    } else {
                    isEnabled = false
                    onBackPressedDispatcher.onBackPressed()
                }
            }
        }

        onBackPressedDispatcher.addCallback(this, callback)
    }

    private fun attachChangeWatchers() {
        val w = object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) {
                hasChanges = true
            }
        }
        binding.etType.addTextChangedListener(w)
        binding.etLocation.addTextChangedListener(w)
        binding.etTestResult.addTextChangedListener(w)
    }

    private fun setupCapturedImagesRecycler() {
        cameraBinding.rvRoomItems.layoutManager =
            LinearLayoutManager(this, LinearLayoutManager.HORIZONTAL, false)
        cameraBinding.rvRoomItems.adapter =
            ImageAdapter(allImages, title = detectorItem?.name ?: "", onDelete = {
                capturedUris.clear()
                capturedUris.addAll(allImages.filterIsInstance<ImageItem.Local>().map { it.uri })
                hasChanges = true
            })
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == CAMERA_REQUEST && resultCode == RESULT_OK) {
            val newUris = CameraActivity.Companion.pendingUris.toList()
            allImages.removeAll { it is ImageItem.Local }
            allImages.addAll(newUris.map { ImageItem.Local(it) })
            cameraBinding.rvRoomItems.adapter?.notifyDataSetChanged()
            if (newUris.isNotEmpty()) hasChanges = true
            capturedUris.clear()
            capturedUris.addAll(newUris)
        }
    }

    private fun setDetectorData() {
        if (detectorItem != null) {
            binding.etType.setText(detectorItem?.name)
            binding.etLocation.setText(detectorItem?.location ?: "")
            binding.etTestResult.setText(detectorItem?.note ?: "")

            lifecycleScope.launch {
                db.attachmentDao().observeByEntity(detectorItem!!.id, "DETECTOR").collect { dbAttachments ->
                    dbAttachments.forEach { a ->
                        val img: ImageItem? = when {
                            a.isUploaded && a.storageKey != null ->
                                ImageItem.Remote(a.serverId ?: a.id, "${ApiClient.IMAGE_BASE_URL}${a.storageKey}")
                            !a.isUploaded && a.localUri != null ->
                                ImageItem.Local(android.net.Uri.fromFile(java.io.File(a.localUri!!)))
                            else -> null
                        }
                        img ?: return@forEach
                        val exists = allImages.any {
                            (img is ImageItem.Remote && it is ImageItem.Remote && it.id == img.id) ||
                            (img is ImageItem.Local && it is ImageItem.Local && it.uri == img.uri)
                        }
                        if (!exists) {
                            allImages.add(img)
                            cameraBinding.rvRoomItems.adapter?.notifyDataSetChanged()
                        }
                    }
                }
            }
        }
    }

    private fun isValid(): Boolean {
        if (binding.etType.text.toString().isEmpty()) {
            showToast("Please enter Detector Type")
            return false
        }
        return true
    }

    private fun saveDetector() {
        if (!isValid()) return
        val request = AddDetectorRequest(
            name = binding.etType.text.toString(),
            location = binding.etLocation.text.toString(),
            note = binding.etTestResult.text.toString()
        )
        showLoading("Saving...")
        lifecycleScope.launch {
            try {
                if (detectorItem != null) {
                    repo.updateDetector(detectorItem!!.id, request)
                    for (uri in capturedUris) {
                        val existing = db.detectorDao().getById(detectorItem!!.id)
                        attachmentRepo.saveLocalAttachment(uri, detectorItem!!.id, existing?.serverId, "DETECTOR")
                    }
                } else {
                    val entity = repo.addDetector(reportId, request)
                    for (uri in capturedUris) {
                        attachmentRepo.saveLocalAttachment(uri, entity.id, entity.serverId, "DETECTOR")
                    }
                }
                hasChanges = false
                SyncScheduler.scheduleImmediateSync(this@AddEditDetectorActivity)
                finish()
            } catch (e: Exception) {
                showToast("Failed to save: ${e.message}")
            } finally {
                hideLoading()
            }
        }
    }

    private fun deleteDetector() {
        Utils.showDialogBox(
            this, "Delete Detector",
            "Do you want to delete this ? This action can't be undone"
        ) {
            lifecycleScope.launch {
                repo.deleteDetector(detectorItem?.id ?: "")
                SyncScheduler.scheduleImmediateSync(this@AddEditDetectorActivity)
                finish()
            }
        }
    }
}
