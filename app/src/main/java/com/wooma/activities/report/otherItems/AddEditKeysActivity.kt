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
import com.wooma.data.network.showToast
import com.wooma.data.repository.AttachmentRepository
import com.wooma.data.repository.OtherItemsRepository
import com.wooma.databinding.ActivityAddEditKeysBinding
import com.wooma.databinding.AddImageLayoutBinding
import com.wooma.model.AddKeyRequest
import com.wooma.model.ImageItem
import com.wooma.model.KeyItem
import com.wooma.sync.SyncScheduler
import kotlinx.coroutines.launch
import com.wooma.data.network.ApiClient

class AddEditKeysActivity : BaseActivity() {
    private lateinit var binding: ActivityAddEditKeysBinding
    private lateinit var cameraBinding: AddImageLayoutBinding

    var keyItem: KeyItem? = null
    var count = 1

    private val capturedUris = mutableListOf<Uri>()
    private val allImages = mutableListOf<ImageItem>()
    private val CAMERA_REQUEST = 1001

    var reportId = ""
    var showTimestamp = true

    val suggestionList = mutableListOf(
        "Yale",
        "Mortice",
        "Cylinder",
        "Chubb",
        "Union",
        "Era",
        "Abloy",
        "Mul-T-Lock",
        "UPVC Door Key",
        "Window Key",
        "Deadbolt Key",
        "Padlock Key",
        "Garage Key",
        "Shed Key",
        "Gate Key"
    )

    val noteSuggestionList = mutableListOf(
        "Front Door", "Back Door", "Side Door", "Garage Door", "Shed Door", "Gate",
        "Window", "Garage", "Shed", "Outhouse", "Gate Key", "Main Entrance",
        "Rear Entrance", "Internal Door", "Office Door", "Storage Room",
        "Utility Room", "Boiler Room", "Basement Door", "Attic Door"
    )

    private lateinit var suggestionsAdapter: SuggestionsAdapter
    private lateinit var noteSuggestionsAdapter: SuggestionsAdapter

    var isEdit = false
    private var hasChanges = false

    private val repo by lazy { OtherItemsRepository(this) }
    private val attachmentRepo by lazy { AttachmentRepository(this) }
    private val db by lazy { WoomaDatabase.getInstance(this) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        binding = ActivityAddEditKeysBinding.inflate(layoutInflater)
        setContentView(binding.root)
        cameraBinding = binding.cameraLayout
        applyWindowInsetsToBinding(binding.root)
        setupCapturedImagesRecycler()

        keyItem = intent.getParcelableExtra("keyItem")
        reportId = intent.getStringExtra("reportId") ?: ""
        showTimestamp = intent.getBooleanExtra("showTimestamp", true)
        isEdit = intent.getBooleanExtra("isEdit", false)

        cameraBinding.ivAddImage.setOnClickListener {
            CameraActivity.Companion.existingImages = allImages.toList()
            CameraActivity.Companion.pendingUris.clear()
            startActivityForResult(
                Intent(this, CameraActivity::class.java).putExtra("showTimestamp", showTimestamp),
                CAMERA_REQUEST
            )
        }

        binding.btnSave.setOnClickListener {
            saveKey()
        }

        binding.ivDelete.visibility = if (keyItem != null) View.VISIBLE else View.GONE
        binding.ivDelete.setOnClickListener {
            deleteKey()
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

        noteSuggestionsAdapter = SuggestionsAdapter(
            this,
            noteSuggestionList,
            object : SuggestionsAdapter.OnItemClickInterface {
                override fun onItemClick(item: String) {
                    binding.etNote.setText(item)
                    binding.etNote.text?.let { binding.etNote.setSelection(it.length) }
                    noteSuggestionsAdapter.filter("")
                }
            })
        binding.rvNoteSuggestions.adapter = noteSuggestionsAdapter

        binding.etNote.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) {
                noteSuggestionsAdapter.filter(s?.toString() ?: "")
            }
        })

        binding.ivBack.setOnClickListener {
            if (hasChanges) showUnsavedChangesDialog { finish() } else finish()
        }

        binding.ivMinus.setOnClickListener {
            if (count > 1) { count--; hasChanges = true }
            binding.tvQty.text = "$count"
        }

        binding.ivPlus.setOnClickListener {
            count++
            hasChanges = true
            binding.tvQty.text = "$count"
        }

        setMeterData()
        attachChangeWatchers()

        val callback = object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                if (hasChanges) showUnsavedChangesDialog {
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

    private fun saveKey() {
        if (!isValid()) return
        val request = AddKeyRequest(
            name = binding.etType.text.toString(),
            no_of_keys = count,
            note = binding.etNote.text.toString()
        )
        showLoading("Saving...")
        lifecycleScope.launch {
            try {
                if (keyItem != null) {
                    repo.updateKey(keyItem!!.id, request)
                    for (uri in capturedUris) {
                        val existing = db.keyDao().getById(keyItem!!.id)
                        attachmentRepo.saveLocalAttachment(uri, keyItem!!.id, existing?.serverId, "KEY")
                    }
                } else {
                    val entity = repo.addKey(reportId, request)
                    for (uri in capturedUris) {
                        attachmentRepo.saveLocalAttachment(uri, entity.id, entity.serverId, "KEY")
                    }
                }
                hasChanges = false
                SyncScheduler.scheduleImmediateSync(this@AddEditKeysActivity)
                finish()
            } catch (e: Exception) {
                showToast("Failed to save: ${e.message}")
            } finally {
                hideLoading()
            }
        }
    }

    private fun deleteKey() {
        Utils.showDialogBox(
            this, "Delete Key",
            "Do you want to delete this ? This action can't be undone"
        ) {
            lifecycleScope.launch {
                repo.deleteKey(keyItem?.id ?: "")
                SyncScheduler.scheduleImmediateSync(this@AddEditKeysActivity)
                finish()
            }
        }
    }

    private fun attachChangeWatchers() {
        val w = object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) { hasChanges = true }
        }
        binding.etType.addTextChangedListener(w)
        binding.etNote.addTextChangedListener(w)
    }

    private fun setupCapturedImagesRecycler() {
        cameraBinding.rvRoomItems.layoutManager =
            LinearLayoutManager(this, LinearLayoutManager.HORIZONTAL, false)
        cameraBinding.rvRoomItems.adapter =
            ImageAdapter(allImages, title = keyItem?.name ?: "", onDelete = {
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

    private fun setMeterData() {
        if (keyItem != null) {
            binding.etType.setText(keyItem?.name)
            count = keyItem?.no_of_keys ?: 1
            binding.tvQty.text = (keyItem?.no_of_keys ?: 0).toString()
            binding.etNote.setText(keyItem?.note ?: "")

            lifecycleScope.launch {
                db.attachmentDao().observeByEntity(keyItem!!.id, "KEY").collect { dbAttachments ->
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
            showToast("Please enter Key Type")
            return false
        }
        return true
    }
}
