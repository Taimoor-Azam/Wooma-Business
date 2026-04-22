package com.wooma.activities.report

import android.app.ProgressDialog
import android.text.Editable
import android.text.TextWatcher
import com.wooma.data.network.ApiClient
import com.wooma.model.ImageItem
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.View
import androidx.activity.OnBackPressedCallback
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.LinearLayoutManager
import com.wooma.activities.BaseActivity
import com.wooma.adapter.ConditionChipAdapter
import com.wooma.adapter.ImageAdapter
import com.wooma.adapter.ItemConditionAdapter
import com.wooma.adapter.SuggestionsAdapter
import com.wooma.R
import com.wooma.customs.AttachmentUploadHelper
import com.wooma.customs.Utils
import com.wooma.data.network.ApiResponseListener
import com.wooma.data.network.MyApi
import com.wooma.data.network.makeApiRequest
import com.wooma.data.network.showToast
import com.wooma.databinding.ActivityInventoryRoomItemBinding
import com.wooma.databinding.AddImageLayoutBinding
import com.wooma.model.ApiResponse
import com.wooma.model.ConditionDAO
import com.wooma.model.ErrorResponse
import com.wooma.model.PropertyReportType
import com.wooma.model.ReportData
import com.wooma.model.RoomItem
import com.wooma.model.UpdateRoomItemRequest
import com.wooma.model.UpsertRoomInspectionRequest
import com.wooma.model.enums.ReportTypes
import java.util.Locale

class InventoryRoomItemActivity : BaseActivity() {
    private lateinit var binding: ActivityInventoryRoomItemBinding
    private lateinit var cameraBinding: AddImageLayoutBinding
    var selectedCondition = ""
    var selectedCleanliness = ""
    var roomItems: RoomItem? = null
    var reportId = ""
    var roomId = ""

    var reportType: PropertyReportType? = null
    var isInspection = false
    var isIssue: Boolean = false
    var selectedPriority: String? = null
    private var hasChanges = false
    private lateinit var noteSuggestionsAdapter: SuggestionsAdapter
    private var isHandlingNoteEnter = false
    private lateinit var descriptionSuggestionsAdapter: SuggestionsAdapter
    private var isHandlingDescriptionEnter = false
    private val conditionChips =
        listOf("Marked", "Scuffed", "Stained", "Loose fitting", "Cracked", "Damp", "Mould", "Faded")
    private val selectedChips = mutableSetOf<String>()

    private val capturedUris = mutableListOf<Uri>()
    private val allImages = mutableListOf<ImageItem>()
    private val CAMERA_REQUEST = 1001
    private val S3_BASE_URL = ApiClient.IMAGE_BASE_URL

    val conditionItems = mutableListOf(
        ConditionDAO(R.drawable.svg_excellent, "Excellent"),
        ConditionDAO(R.drawable.svg_excellent, "Good"),
        ConditionDAO(R.drawable.svg_poor, "Poor"),
        ConditionDAO(R.drawable.svg_poor, "Unacceptable"),
        ConditionDAO(R.drawable.svg_n_a, "N/A")
    )

    companion object {
        val NOTE_SUGGESTIONS = mutableListOf(
            "Excellent", "Very good", "Good", "Fair", "Poor", "New", "As new", "Like new",
            "Well maintained", "Showing wear", "Dated", "Old but serviceable", "End of life",
            "Clean", "Very clean", "Spotless", "Dirty", "Dusty", "Grimy", "Stained", "Marked",
            "Lightly soiled", "Heavily soiled", "Requires cleaning", "Recently cleaned",
            "Scratched", "Light scratches", "Heavy scratches", "Scuffed", "Scuff marks",
            "Dented", "Chipped", "Cracked", "Hairline crack", "Large crack", "Gouged", "Scored",
            "Abraded", "Worn smooth", "Pitted", "Indented", "Peeling", "Flaking", "Bubbling",
            "Blistering", "Faded", "Discoloured", "Yellowed", "Scuffed paint", "Paint splashes",
            "Uneven coverage", "Touch-ups visible", "Patchy", "Chalking", "Loose", "Wobbly",
            "Unstable", "Broken", "Split", "Warped", "Bowed", "Sagging", "Twisted", "Misaligned",
            "Out of square", "Failing", "Collapsed", "Water stained", "Water marked",
            "Water damage", "Damp patch", "Damp staining", "Mould present", "Mildew",
            "Waterlogged", "Tide marks", "Condensation damage", "Leak staining", "Worn",
            "Heavily worn", "Lightly worn", "Frayed", "Threadbare", "Balding", "Flattened",
            "Compressed", "Traffic wear", "High traffic wear", "Uneven wear", "Age-related wear",
            "Hole", "Small hole", "Large hole", "Pin holes", "Nail holes", "Screw holes",
            "Tear", "Small tear", "Large tear", "Rip", "Split seam", "Missing section",
            "Light staining", "Heavy staining", "Ink stain", "Grease stain", "Oil stain",
            "Rust stain", "Food stain", "Beverage stain", "Paint marks", "Pen marks",
            "Burn mark", "Heat mark", "Ring mark", "Blu-tack marks", "Tape residue",
            "Adhesive residue", "Sticky residue", "Glue marks", "Poster marks", "Picture hooks",
            "Command strip residue", "Missing", "Incomplete", "Snapped", "Bent", "Shattered",
            "Damaged", "Faulty", "Not working", "Inoperable", "Seized", "Working",
            "Fully functional", "Partially working", "Intermittent", "Stiff", "Jammed", "Stuck",
            "Won't open", "Won't close", "Draughty", "Rattles", "Squeaks", "Binding",
            "Catching", "Poorly fitted", "Loose fit", "Gaps visible", "Uneven", "Skewed",
            "Crooked", "Sloping", "Not flush", "Proud", "Recessed", "Out of level",
            "Grout missing", "Grout cracked", "Grout discoloured", "Grout mouldy",
            "Sealant missing", "Sealant cracked", "Sealant peeling", "Sealant discoloured",
            "Sealant mouldy", "Poor grouting", "Poor sealing", "Tile cracked", "Tile chipped",
            "Tile loose", "Tile missing", "Tile stained", "Tile discoloured", "Hollow sounding",
            "Rotten", "Decayed", "Soft spots", "Woodworm", "Insect damage", "Splintered",
            "Knot holes", "Veneer lifting", "Veneer bubbling", "Delaminating", "Rusty",
            "Corroded", "Tarnished", "Oxidised", "Stiff to operate", "Loose fixings",
            "Missing screws", "Stripped threads"
        )

        val DESCRIPTION_SUGGESTIONS = mutableListOf(
            "Timber", "UPVC", "Composite", "Oak", "Pine", "MDF", "Hardwood", "Softwood",
            "Glass", "Metal", "Fire-rated", "Panel", "Flush", "Glazed", "Half-glazed",
            "Fully glazed", "Bi-fold", "Sliding", "French", "Pocket", "4-panel", "6-panel",
            "Gloss", "Matt", "Satin", "Eggshell", "Varnished", "Lacquered", "Waxed", "Painted",
            "Chrome handle", "Brass handle", "Nickel handle", "Stainless steel handle",
            "Lever handle", "Round knob", "D-handle", "Mortice lock", "Yale lock",
            "Multi-point lock", "Barrel bolt", "Chain", "Spy hole", "Letter plate",
            "Brass hinges", "Chrome hinges", "Self-closing", "Wallpapered", "Textured",
            "Smooth", "Plastered", "Rendered", "Tiled", "Wood panelled", "Brick",
            "Exposed brick", "Feature wall", "Dado rail", "Picture rail", "Coving",
            "Matt emulsion", "Silk emulsion", "Vinyl matt", "Vinyl silk", "Ceramic",
            "Porcelain", "Metro", "Subway", "Mosaic", "Marble", "Travertine", "Full height",
            "Half height", "Splashback only", "Carpet", "Laminate", "Vinyl",
            "Engineered wood", "Tile", "Ceramic tile", "Porcelain tile", "Stone", "Slate",
            "Linoleum", "Cork", "Parquet", "LVT", "Vinyl planks", "Concrete", "Beige",
            "Light grey", "Dark grey", "Light brown", "Dark brown", "Walnut", "Natural",
            "Terracotta", "Blue", "Polished", "Honed", "Rustic", "Distressed",
            "Short pile", "Medium pile", "Long pile", "Loop pile", "Twist pile", "Berber",
            "Shag", "Plain", "Patterned", "Striped", "Herringbone", "Chevron",
            "Wood effect", "Stone effect", "Marble effect", "Artex", "Stippled",
            "Ceiling rose", "Beamed", "Cornicing", "Suspended", "Coffered",
            "Pendant", "Ceiling light", "Chandelier", "Downlights", "Spotlights",
            "Strip light", "LED panel", "Track lighting", "Wall light", "Wall sconce",
            "Picture light", "Under-cabinet", "Recessed", "Flush mount", "Semi-flush",
            "Statement light", "Bayonet", "Screw", "GU10", "E27", "E14", "LED", "Halogen",
            "Fluorescent", "Chrome", "Brass", "Brushed nickel", "Brushed steel", "Copper",
            "Bronze", "Fabric shade", "Metal shade", "Dimmable", "Dimmer switch", "Pull cord",
            "Motion sensor", "Timer", "Smart bulb", "Multiple bulbs", "Single bulb",
            "Sash", "Casement", "Tilt and turn", "Bay", "Bow", "Skylight", "Velux",
            "Awning", "Picture window", "Fixed", "Aluminium", "Wood", "Double glazed",
            "Triple glazed", "Single glazed", "Secondary glazing", "White", "Oak effect",
            "Rosewood effect", "Espagnolette lock", "Key lock", "Sash lock", "Trickle vent",
            "Window restrictor", "Clear glass", "Frosted glass", "Obscured glass",
            "Tinted glass", "Patterned glass", "Low-E glass", "Leaded glass", "Georgian bars",
            "Single socket", "Double socket", "USB socket", "Single switch", "Double switch",
            "Cooker switch", "Fused spur", "Shaver socket", "TV point", "Telephone point",
            "Ethernet point", "White plastic", "Brushed chrome", "Polished chrome",
            "Stainless steel", "Modern", "Victorian", "Edwardian", "Art deco",
            "Surface mounted", "Bullnose", "Torus", "Ogee", "Square edge", "Chamfered",
            "Traditional", "Ornate", "White", "Cream", "Magnolia", "Grey", "Black", "Brown",
            "Natural wood", "Stained", "Off-white", "Ivory", "Neutral",
            "50mm", "70mm", "95mm", "120mm", "145mm", "170mm", "220mm"
        )
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        binding = ActivityInventoryRoomItemBinding.inflate(layoutInflater)
        setContentView(binding.root)
        cameraBinding = binding.cameraLayout

        applyWindowInsetsToBinding(binding.root)

        cameraBinding.ivAddImage.setOnClickListener {
            CameraActivity.existingImages = allImages.toList()
            CameraActivity.pendingUris.clear()
            startActivityForResult(Intent(this, CameraActivity::class.java), CAMERA_REQUEST)
        }

        roomItems = intent.getParcelableExtra("roomItem")
        reportId = intent.getStringExtra("reportId") ?: ""
        roomId = intent.getStringExtra("roomId") ?: ""

        setupCapturedImagesRecycler()

        reportType = intent.getParcelableExtra("reportType")
        isInspection = reportType?.type_code?.lowercase() == ReportTypes.INSPECTION.value

        binding.tvTitle.text = if (roomItems != null) roomItems?.name ?: "" else "Add Room Item"

        if (roomItems != null) {
            binding.etItemName.setText(roomItems?.name ?: "")

            binding.etItemName.addTextChangedListener(object : TextWatcher {
                override fun beforeTextChanged(
                    s: CharSequence?,
                    start: Int,
                    count: Int,
                    after: Int
                ) {
                }

                override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                    binding.tvTitle.text = s?.toString() ?: ""
                }

                override fun afterTextChanged(s: Editable?) {}
            })

            selectedCondition = roomItems?.general_condition ?: ""
            selectedCleanliness = roomItems?.general_cleanliness ?: ""

            binding.etDescription.setText(roomItems?.description ?: "")
            binding.etNote.setText(roomItems?.note ?: "")

            // Load existing images from API
            roomItems?.attachments?.forEach { attachment ->
                val id = attachment.id ?: return@forEach
                val key = attachment.storageKey ?: return@forEach
                allImages.add(ImageItem.Remote(id, "$S3_BASE_URL$key"))
            }
            cameraBinding.rvRoomItems.adapter?.notifyDataSetChanged()

            binding.rvCondition.adapter =
                ItemConditionAdapter(this, conditionItems, selectedCondition) { dao ->
                    selectedCondition = dao?.name ?: ""
                    hasChanges = true
                }
            binding.tvCleanliness.adapter =
                ItemConditionAdapter(this, conditionItems, selectedCleanliness) { dao ->
                    selectedCleanliness = dao?.name ?: ""
                    hasChanges = true
                }

            val conditionIndex = conditionItems.indexOfFirst { it.name.equals(selectedCondition, ignoreCase = true) }
            if (conditionIndex >= 0) binding.rvCondition.scrollToPosition(conditionIndex)

            val cleanlinessIndex = conditionItems.indexOfFirst { it.name.equals(selectedCleanliness, ignoreCase = true) }
            if (cleanlinessIndex >= 0) binding.tvCleanliness.scrollToPosition(cleanlinessIndex)
        }

        if (isInspection) {
            binding.regularLayout.visibility = View.GONE
            binding.inspectionLayout.visibility = View.VISIBLE
            binding.btnSave.text = "Done"

            setupConditionChipsRecycler()

            binding.btnAllOk.setOnClickListener {
                isIssue = false
                binding.issuesExpandedLayout.visibility = View.GONE
                updateToggleButtonStates()
                hasChanges = true
            }

            binding.btnIssuesFound.setOnClickListener {
                isIssue = true
                binding.issuesExpandedLayout.visibility = View.VISIBLE
                updateToggleButtonStates()
                hasChanges = true
            }

            binding.btnObservation.setOnClickListener {
                selectedPriority = if (selectedPriority == "observation") null else "observation"
                updatePriorityButtonStates()
                hasChanges = true
            }

            binding.btnActionRequired.setOnClickListener {
                selectedPriority =
                    if (selectedPriority == "action required") null else "action required"
                updatePriorityButtonStates()
                hasChanges = true
            }

            binding.btnUrgent.setOnClickListener {
                selectedPriority = if (selectedPriority == "urgent") null else "urgent"
                updatePriorityButtonStates()
                hasChanges = true
            }
        }

        binding.btnSave.setOnClickListener {
            if (isInspection) {
                upsertRoomInspectionApi()
            } else {
                var genCondition = selectedCondition.lowercase(Locale.ROOT)
                if (selectedCondition.equals("N/A")){
                    genCondition = selectedCondition
                }

                var genCleanliness = selectedCleanliness.lowercase(Locale.ROOT)
                if (selectedCleanliness.equals("N/A")){
                    genCleanliness = selectedCleanliness
                }

                val roomItem = UpdateRoomItemRequest(
                    name = binding.etItemName.text.toString().trim().ifEmpty { null },
                    general_condition = genCondition/*.replace("/", "")*/,
                    general_cleanliness = genCleanliness/*.replace("/", "")*/,
                    description = binding.etDescription.text.toString(),
                    note = binding.etNote.text.toString()
                )
                updateRoomItemApi(roomItem)
            }
        }

        binding.btnDelete.setOnClickListener {
            Utils.showDialogBox(
                this@InventoryRoomItemActivity,
                "Delete Room Item",
                "Are you sure you want to delete '${roomItems?.name}' ?"
            ) {
                deleteRoomItemApi()
            }
        }

        binding.ivBack.setOnClickListener {
            if (hasChanges) showUnsavedChangesDialog { finish() } else finish()
        }
        attachChangeWatchers()
        setupNoteSuggestions()
        setupDescriptionSuggestions()

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

    private fun setupNoteSuggestions() {
        noteSuggestionsAdapter = SuggestionsAdapter(
            this,
            NOTE_SUGGESTIONS,
            object : SuggestionsAdapter.OnItemClickInterface {
                override fun onItemClick(item: String) {
                    val fullText = binding.etNote.text.toString()
                    val lastSemicolon = fullText.lastIndexOf(';')
                    val prefix = if (lastSemicolon >= 0) fullText.substring(0, lastSemicolon + 1)
                        .trimEnd() + " " else ""
                    val newText = "$prefix$item; "
                    binding.etNote.setText(newText)
                    binding.etNote.setSelection(newText.length)
                    noteSuggestionsAdapter.filter("")
                    hasChanges = true
                }
            }
        )
        binding.rvNoteSuggestions.layoutManager =
            LinearLayoutManager(this, LinearLayoutManager.HORIZONTAL, false)
        binding.rvNoteSuggestions.adapter = noteSuggestionsAdapter

        binding.etNote.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) {
                if (isHandlingNoteEnter) return
                val fullText = s?.toString() ?: ""
                if (fullText.contains('\n')) {
                    isHandlingNoteEnter = true
                    val newText = fullText.replace("\n", "").trimEnd()
                    val withSemicolon = if (newText.endsWith(';')) "$newText " else "$newText; "
                    binding.etNote.setText(withSemicolon)
                    binding.etNote.setSelection(withSemicolon.length)
                    noteSuggestionsAdapter.filter("")
                    isHandlingNoteEnter = false
                    return
                }
                val lastSemicolon = fullText.lastIndexOf(';')
                val currentWord = if (lastSemicolon >= 0) fullText.substring(lastSemicolon + 1).trim() else fullText.trim()
                noteSuggestionsAdapter.filter(currentWord)
            }
        })
    }

    private fun setupDescriptionSuggestions() {
        descriptionSuggestionsAdapter = SuggestionsAdapter(
            this,
            DESCRIPTION_SUGGESTIONS,
            object : SuggestionsAdapter.OnItemClickInterface {
                override fun onItemClick(item: String) {
                    val fullText = binding.etDescription.text.toString()
                    val lastSemicolon = fullText.lastIndexOf(';')
                    val prefix = if (lastSemicolon >= 0) fullText.substring(0, lastSemicolon + 1)
                        .trimEnd() + " " else ""
                    val newText = "$prefix$item; "
                    binding.etDescription.setText(newText)
                    binding.etDescription.setSelection(newText.length)
                    descriptionSuggestionsAdapter.filter("")
                    hasChanges = true
                }
            }
        )
        binding.rvDescriptionSuggestions.layoutManager =
            LinearLayoutManager(this, LinearLayoutManager.HORIZONTAL, false)
        binding.rvDescriptionSuggestions.adapter = descriptionSuggestionsAdapter

        binding.etDescription.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) {
                if (isHandlingDescriptionEnter) return
                val fullText = s?.toString() ?: ""
                if (fullText.contains('\n')) {
                    isHandlingDescriptionEnter = true
                    val newText = fullText.replace("\n", "").trimEnd()
                    val withSemicolon = if (newText.endsWith(';')) "$newText " else "$newText; "
                    binding.etDescription.setText(withSemicolon)
                    binding.etDescription.setSelection(withSemicolon.length)
                    descriptionSuggestionsAdapter.filter("")
                    isHandlingDescriptionEnter = false
                    return
                }
                val lastSemicolon = fullText.lastIndexOf(';')
                val currentWord = if (lastSemicolon >= 0) fullText.substring(lastSemicolon + 1).trim() else fullText.trim()
                descriptionSuggestionsAdapter.filter(currentWord)
            }
        })
    }

    private fun attachChangeWatchers() {
        val w = object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) {
                hasChanges = true
            }
        }
        binding.etItemName.addTextChangedListener(w)
        binding.etDescription.addTextChangedListener(w)
        binding.etNote.addTextChangedListener(w)
        binding.etIssueNote.addTextChangedListener(w)
    }

    private fun setupCapturedImagesRecycler() {
        cameraBinding.rvRoomItems.layoutManager =
            LinearLayoutManager(this, LinearLayoutManager.HORIZONTAL, false)
        cameraBinding.rvRoomItems.adapter =
            ImageAdapter(allImages, title = roomItems?.name ?: "", onDelete = {
                capturedUris.clear()
                capturedUris.addAll(allImages.filterIsInstance<ImageItem.Local>().map { it.uri })
            })
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == CAMERA_REQUEST && resultCode == RESULT_OK) {
            val newUris = CameraActivity.pendingUris.toList()
            
            // Sync allImages based on what remains in CameraActivity.resultImages
            allImages.clear()
            allImages.addAll(CameraActivity.resultImages)
            cameraBinding.rvRoomItems.adapter?.notifyDataSetChanged()

            // Refresh capturedUris (local only)
            capturedUris.clear()
            capturedUris.addAll(allImages.filterIsInstance<ImageItem.Local>().map { it.uri })

            val entityId = roomItems?.id ?: ""
            if (entityId.isNotEmpty() && newUris.isNotEmpty()) {
                val progress = ProgressDialog(this).apply {
                    setMessage("Uploading images...")
                    setCancelable(false)
                    show()
                }
                AttachmentUploadHelper.uploadImages(
                    activity = this,
                    imageUris = newUris,
                    entityId = entityId,
                    entityType = "ROOM_ITEM",
                    onComplete = { progress.dismiss() },
                    onError = { progress.dismiss() }
                )
            }
        }
    }

    private fun deleteRoomItemApi() {
        makeApiRequest(
            apiServiceClass = MyApi::class.java,
            context = this,
            showLoading = true,
            requestAction = { apiService ->
                apiService.deleteRoomItem(
                    reportId,
                    roomId,
                    roomItems?.id ?: ""
                )
            },
            listener = object : ApiResponseListener<ApiResponse<ReportData>> {
                override fun onSuccess(response: ApiResponse<ReportData>) {
                    if (response.success) {
                        finish()
                    }
                }

                override fun onFailure(errorMessage: ErrorResponse?) {}
                override fun onError(throwable: Throwable) {}
            }
        )
    }

    private fun updateRoomItemApi(item: UpdateRoomItemRequest) {
        makeApiRequest(
            apiServiceClass = MyApi::class.java,
            context = this,
            showLoading = true,
            requestAction = { apiService ->
                apiService.updateRoomItem(
                    reportId,
                    roomId,
                    roomItems?.id ?: "",
                    item
                )
            },
            listener = object : ApiResponseListener<ApiResponse<ReportData>> {
                override fun onSuccess(response: ApiResponse<ReportData>) {
                    if (response.success) {
                        uploadPhotosIfNeeded()
                    }
                }

                override fun onFailure(errorMessage: ErrorResponse?) {}
                override fun onError(throwable: Throwable) {}
            }
        )
    }

    private fun uploadPhotosIfNeeded() {
        val itemId = roomItems?.id ?: return finish()
        if (capturedUris.isEmpty()) {
            finish()
            return
        }
        AttachmentUploadHelper.uploadImages(
            activity = this,
            imageUris = capturedUris,
            entityId = itemId,
            entityType = "ROOM_ITEM",
            onComplete = { finish() },
            onError = { finish() }
        )
    }

    private fun setupConditionChipsRecycler() {
        binding.rvConditionChips.layoutManager =
            LinearLayoutManager(
                this,
                LinearLayoutManager.HORIZONTAL,
                false
            )
        binding.rvConditionChips.adapter =
            ConditionChipAdapter(conditionChips, selectedChips) { chip, isSelected ->
                if (isSelected) selectedChips.add(chip) else selectedChips.remove(chip)
                val joined = selectedChips.joinToString("; ")
                binding.etIssueNote.setText(joined)
                binding.etIssueNote.setSelection(binding.etIssueNote.text.length)
            }
    }

    private fun updateToggleButtonStates() {
        val selectedBg = ContextCompat.getDrawable(this, R.drawable.bg_button_green)
        val defaultBg = ContextCompat.getDrawable(this, R.drawable.bg_edittext)
        val whiteColor = ContextCompat.getColor(this, R.color.white)
        val blackColor = ContextCompat.getColor(this, R.color.black)

        binding.btnAllOk.background = if (!isIssue) selectedBg else defaultBg
        binding.btnAllOk.setTextColor(if (!isIssue) whiteColor else blackColor)
        binding.btnIssuesFound.background = if (isIssue) selectedBg else defaultBg
        binding.btnIssuesFound.setTextColor(if (isIssue) whiteColor else blackColor)
    }

    private fun updatePriorityButtonStates() {
        val selectedBg = ContextCompat.getDrawable(this, R.drawable.bg_button_green)
        val defaultBg = ContextCompat.getDrawable(this, R.drawable.bg_edittext)
        val whiteColor = ContextCompat.getColor(this, R.color.white)
        val blackColor = ContextCompat.getColor(this, R.color.black)

        binding.btnObservation.background =
            if (selectedPriority == "observation") selectedBg else defaultBg
        binding.btnObservation.setTextColor(if (selectedPriority == "observation") whiteColor else blackColor)
        binding.btnActionRequired.background =
            if (selectedPriority == "action required") selectedBg else defaultBg
        binding.btnActionRequired.setTextColor(if (selectedPriority == "action required") whiteColor else blackColor)
        binding.btnUrgent.background = if (selectedPriority == "urgent") selectedBg else defaultBg
        binding.btnUrgent.setTextColor(if (selectedPriority == "urgent") whiteColor else blackColor)
    }

    private fun upsertRoomInspectionApi() {
        makeApiRequest(
            apiServiceClass = MyApi::class.java,
            context = this,
            showLoading = true,
            requestAction = { api ->
                api.upsertRoomInspection(
                    UpsertRoomInspectionRequest(
                        room_id = roomItems?.room_id ?: roomId,
                        is_issue = isIssue,
                        note = if (isIssue) binding.etIssueNote.text.toString()
                            .ifEmpty { null } else null,
                        priority = if (isIssue) selectedPriority else null
                    )
                )
            },
            listener = object : ApiResponseListener<ApiResponse<Any>> {
                override fun onSuccess(response: ApiResponse<Any>) {
                    uploadPhotosIfNeeded()
                }

                override fun onFailure(errorMessage: ErrorResponse?) {
                    showToast(errorMessage?.error?.message ?: "Failed to save inspection")
                }

                override fun onError(throwable: Throwable) {
                    showToast("Error: ${throwable.message}")
                }
            }
        )
    }
}
