package com.wooma.activities.report.otherItems

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.View
import androidx.activity.OnBackPressedCallback
import androidx.activity.addCallback
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
import com.wooma.databinding.ActivityAddEditMeterBinding
import com.wooma.databinding.AddImageLayoutBinding
import com.wooma.model.AddMeterRequest
import com.wooma.model.ImageItem
import com.wooma.model.Meter
import com.wooma.sync.SyncScheduler
import kotlinx.coroutines.launch

class AddEditMeterActivity : BaseActivity() {
    private lateinit var binding: ActivityAddEditMeterBinding
    private lateinit var cameraBinding: AddImageLayoutBinding
    var meterItem: Meter? = null

    private val capturedUris = mutableListOf<Uri>()
    private val allImages = mutableListOf<ImageItem>()
    private val CAMERA_REQUEST = 1001
    private val S3_BASE_URL = ApiClient.IMAGE_BASE_URL

    var reportId = ""
    var showTimestamp = true
    val suggestionList =
        mutableListOf("Gas", "Electric", "Water", "Heating Oil", "Liquid Petroleum Gas (LPG)")

    val locationSuggestionList = mutableListOf(
        "Kitchen", "Under Kitchen Sink", "Utility Room", "Under Stairs",
        "Under Staircase Cupboard", "Garage", "Loft", "Attic", "Basement", "Cellar",
        "Outside Wall", "External Wall Box", "Front of Property", "Rear of Property",
        "Side of Property", "Meter Cupboard", "Hallway", "Entrance Hall", "Porch",
        "Outhouse", "Outbuilding", "Airing Cupboard", "Boiler Cupboard"
    )

    private lateinit var suggestionsAdapter: SuggestionsAdapter
    private lateinit var locationSuggestionsAdapter: SuggestionsAdapter

    var isEdit = false
    private var hasChanges = false
    private var initialType = ""
    private var initialReading = ""
    private var initialSerialNumber = ""
    private var initialLocation = ""

    private val repo by lazy { OtherItemsRepository(this) }
    private val attachmentRepo by lazy { AttachmentRepository(this) }
    private val db by lazy { WoomaDatabase.getInstance(this) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        binding = ActivityAddEditMeterBinding.inflate(layoutInflater)
        setContentView(binding.root)
        cameraBinding = binding.cameraLayout
        applyWindowInsetsToBinding(binding.root)
        setupCapturedImagesRecycler()
        meterItem = intent.getParcelableExtra("meterItem")

        reportId = intent.getStringExtra("reportId") ?: ""
        isEdit = intent.getBooleanExtra("isEdit", false)
        showTimestamp = intent.getBooleanExtra("showTimestamp", true)

        cameraBinding.ivAddImage.setOnClickListener {
            CameraActivity.Companion.existingImages = allImages.toList()
            CameraActivity.Companion.pendingUris.clear()
            startActivityForResult(
                Intent(this, CameraActivity::class.java)
                    .putExtra("showTimestamp", showTimestamp),
                CAMERA_REQUEST
            )
        }

        binding.btnSave.setOnClickListener { saveMeter() }

        binding.ivDelete.visibility = if (meterItem != null) {
            View.VISIBLE
        } else {
            View.GONE
        }

        binding.ivDelete.setOnClickListener { deleteMeter() }

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

        binding.ivBack.setOnClickListener {
            handleBackPress()
        }
        setMeterData()
        cacheInitialValues()
        attachChangeWatchers()

        onBackPressedDispatcher.addCallback(this) {
            handleBackPress()
        }

        val callback = object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                handleBackPress()
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
        binding.etReading.addTextChangedListener(w)
        binding.etSerialNumber.addTextChangedListener(w)
        binding.etLocation.addTextChangedListener(w)
    }

    private fun setupCapturedImagesRecycler() {
        cameraBinding.rvRoomItems.layoutManager =
            LinearLayoutManager(this, LinearLayoutManager.HORIZONTAL, false)
        cameraBinding.rvRoomItems.adapter =
            ImageAdapter(allImages, title = meterItem?.name ?: "", onDelete = {
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

    private fun handleBackPress() {
        if (hasUnsavedChanges()) {
            showUnsavedChangesDialog { finish() }
        } else {
            finish()
        }
    }

    private fun cacheInitialValues() {
        initialType = binding.etType.text?.toString().orEmpty()
        initialReading = binding.etReading.text?.toString().orEmpty()
        initialSerialNumber = binding.etSerialNumber.text?.toString().orEmpty()
        initialLocation = binding.etLocation.text?.toString().orEmpty()
        hasChanges = false
    }

    private fun hasUnsavedChanges(): Boolean {
        if (hasChanges) return true

        val textChanged =
            binding.etType.text?.toString().orEmpty() != initialType ||
                    binding.etReading.text?.toString().orEmpty() != initialReading ||
                    binding.etSerialNumber.text?.toString().orEmpty() != initialSerialNumber ||
                    binding.etLocation.text?.toString().orEmpty() != initialLocation

        // For new meters, images are only local until Save is tapped.
        val hasPendingLocalImages = capturedUris.isNotEmpty()

        return textChanged || hasPendingLocalImages
    }

    fun setMeterData() {
        if (meterItem != null) {
            binding.etType.setText(meterItem?.name)
            binding.etReading.setText(meterItem?.reading)
            binding.etSerialNumber.setText(meterItem?.serial_number)
            binding.etLocation.setText(meterItem?.location)

            lifecycleScope.launch {
                val dbAttachments = db.attachmentDao().getByEntity(meterItem!!.id, "METER")
                dbAttachments.forEach { a ->
                    if (a.isUploaded && a.storageKey != null) {
                        allImages.add(ImageItem.Remote(a.serverId ?: a.id, "$S3_BASE_URL${a.storageKey}"))
                    } else if (!a.isUploaded && a.localUri != null) {
                        allImages.add(ImageItem.Local(android.net.Uri.fromFile(java.io.File(a.localUri!!))))
                    }
                }
                cameraBinding.rvRoomItems.adapter?.notifyDataSetChanged()
            }
        }
    }

    private fun isValid(): Boolean {
        if (binding.etType.text.toString().isEmpty()) {
            showToast("Please enter Meter Type")
            return false
        }
        return true
    }

    private fun saveMeter() {
        if (!isValid()) return
        val request = AddMeterRequest(
            binding.etType.text.toString(),
            binding.etReading.text.toString(),
            binding.etLocation.text.toString(),
            binding.etSerialNumber.text.toString()
        )
        showLoading("Saving...")
        lifecycleScope.launch {
            try {
                if (meterItem != null) {
                    repo.updateMeter(meterItem!!.id, request)
                    for (uri in capturedUris) {
                        val existing = db.meterDao().getById(meterItem!!.id)
                        attachmentRepo.saveLocalAttachment(uri, meterItem!!.id, existing?.serverId, "METER")
                    }
                } else {
                    val entity = repo.addMeter(reportId, request)
                    for (uri in capturedUris) {
                        attachmentRepo.saveLocalAttachment(uri, entity.id, entity.serverId, "METER")
                    }
                }
                hasChanges = false
                SyncScheduler.scheduleImmediateSync(this@AddEditMeterActivity)
                finish()
            } catch (e: Exception) {
                showToast("Failed to save: ${e.message}")
            } finally {
                hideLoading()
            }
        }
    }

    private fun deleteMeter() {
        Utils.showDialogBox(
            this,
            "Delete Meter",
            "Do you want to delete this ? This action can't be undone"
        ) {
            lifecycleScope.launch {
                repo.deleteMeter(meterItem?.id ?: "")
                SyncScheduler.scheduleImmediateSync(this@AddEditMeterActivity)
                finish()
            }
        }
    }
}
