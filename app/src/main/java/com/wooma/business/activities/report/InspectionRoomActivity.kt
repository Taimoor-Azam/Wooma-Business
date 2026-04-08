package com.wooma.business.activities.report

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.util.Log
import android.view.View
import android.widget.ListPopupWindow
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.LinearLayoutManager
import com.wooma.business.R
import com.wooma.business.activities.BaseActivity
import com.wooma.business.adapter.ImageAdapter
import com.wooma.business.adapter.SuggestionsAdapter
import com.wooma.business.customs.AttachmentUploadHelper
import com.wooma.business.data.network.ApiClient
import com.wooma.business.data.network.ApiResponseListener
import com.wooma.business.data.network.MyApi
import com.wooma.business.data.network.makeApiRequest
import com.wooma.business.data.network.showToast
import com.wooma.business.databinding.ActivityInspectionRoomBinding
import com.wooma.business.databinding.AddImageLayoutBinding
import com.wooma.business.model.ApiResponse
import com.wooma.business.model.ErrorResponse
import com.wooma.business.model.ImageItem
import com.wooma.business.model.RoomInspection
import com.wooma.business.model.RoomsResponse
import com.wooma.business.model.UpsertRoomInspectionRequest
import com.wooma.business.model.enums.TenantReportStatus

class InspectionRoomActivity : BaseActivity() {

    private lateinit var binding: ActivityInspectionRoomBinding
    private lateinit var cameraBinding: AddImageLayoutBinding

    private var room: RoomsResponse? = null
    private var reportId = ""
    private var reportStatus = ""
    private var isIssue = false
    private var selectedPriority: String? = null
    private val capturedUris = mutableListOf<Uri>()
    private val allImages = mutableListOf<ImageItem>()
    private val CAMERA_REQUEST = 1001

    private val priorityValues = listOf(null, "observation", "action required", "urgent")
    private val priorityLabels = listOf("Select priority", "Observation", "Action required", "Urgent")

    private lateinit var issueSuggestionsAdapter: SuggestionsAdapter
    private var isHandlingEnter = false

    companion object {
        val ISSUE_SUGGESTIONS = mutableListOf(
            "General lack of cleanliness",
            "Mould growth on walls/ceiling",
            "Mould growth on window seals",
            "Damp/condensation issues",
            "Scuff marks on walls",
            "Limescale build-up in bathroom",
            "Kitchen requires deep clean",
            "Bathroom requires deep clean",
            "Windows require cleaning",
            "Carpet stains",
            "Radiator not heating properly",
            "Heating not working",
            "Boiler not working",
            "Boiler pressure low",
            "Low water pressure",
            "Tap dripping/loose",
            "Blocked sink/drain",
            "Toilet not flushing properly",
            "Shower not working properly",
            "Extractor fan not working",
            "Light bulb requires replacement",
            "Light fitting damaged/not working",
            "Smoke alarm beeping",
            "Smoke alarm not working/missing",
            "Condensation between double glazing",
            "Window won't open/close properly",
            "Door not closing properly",
            "Paint damage/chipping",
            "Holes in walls (picture hooks)",
            "Grease build-up on cooker/hob",
            "Oven requires cleaning",
            "Sealant damaged/mouldy",
            "Grouting cracked/missing",
            "Door handle loose/damaged",
            "Cupboard door loose/damaged",
            "Window lock faulty/missing",
            "Thermostat not working",
            "Garden overgrown/untidy",
            "Weeds requiring removal",
            "Carpets require professional cleaning",
            "Hard floors require cleaning",
            "Damp patches visible",
            "Water stains on ceiling",
            "Guttering blocked/damaged",
            "Dripping tap",
            "Plumbing leak under sink",
            "Washing machine not working properly",
            "Fridge/freezer not working properly",
            "Dishwasher not working properly",
            "Extractor fan filter requires cleaning",
            "Cooker/hob not working",
            "Oven not working properly",
            "Cracks in plaster",
            "Wallpaper damage/peeling",
            "Carpet damage/tears",
            "Broken window seal",
            "Damaged plasterwork",
            "Worn carpet",
            "Socket not working",
            "Switch not working properly",
            "No hot water",
            "Shower head blocked/damaged",
            "Toilet blocked",
            "Toilet cistern leaking",
            "Radiator leaking",
            "Shower screen damaged/leaking",
            "Door damaged/scratched",
            "Window damaged/cracked",
            "Laminate/wood floor damaged",
            "Skirting board damaged",
            "Cupboard handle missing/loose",
            "Drawer damaged/not closing",
            "Worktop damaged/stained",
            "Sink chipped/damaged",
            "Bath chipped/damaged",
            "Shower tray damaged",
            "Curtains/blinds not working properly",
            "Curtains/blinds damaged",
            "Holes in walls (large)",
            "Cracks in ceiling",
            "Loose floorboards",
            "Vinyl flooring damage",
            "Tile cracked/missing",
            "Grout damage/missing",
            "Radiator valve damaged",
            "Boiler making unusual noises",
            "Ventilation grille blocked",
            "Socket loose/damaged",
            "Carbon monoxide alarm not working/missing",
            "Fence damaged/broken",
            "Gate damaged/not closing",
            "Patio/decking requires cleaning",
            "Bin store untidy",
            "Damaged window sill",
            "Towel rail loose/damaged",
            "Toilet seat damaged/loose",
            "Cooker/hob damaged",
            "Oven door damaged/not sealing",
            "Fridge/freezer making excessive noise",
            "Tumble dryer not working",
            "External door damaged",
            "Architrave damaged",
            "Coving damaged",
            "Handrail loose/damaged",
            "Stair banister loose",
            "Fire door damaged/not closing",
            "Shed damaged",
            "Driveway/path damaged",
            "Security light not working",
            "External lighting not working",
            "Doorbell not working",
            "Intercom not working",
            "TV aerial/socket not working",
            "Broadband socket not working",
            "Keys missing",
            "Remote control missing/not working",
            "Damage to furniture (if furnished)",
            "Furniture broken/not usable",
            "Mattress stained/damaged",
            "Power cut/tripped electrics",
            "Pest infestation evident",
            "Roof leak",
            "Roof tiles missing/damaged",
            "Noise from neighbouring properties",
            "Communal area issues",
            "Parking permit issues",
            "General wear and tear beyond reasonable",
            "Tenant to clean immediately",
            "Tenant to deep clean within 7 days",
            "Tenant to clean within 14 days",
            "Tenant to maintain regular cleaning",
            "Tenant advised on proper ventilation",
            "Tenant advised to keep property heated",
            "Tenant advised to report issues promptly",
            "Tenant to arrange professional cleaning",
            "Tenant to remove personal items",
            "Tenant to clear blocked drain",
            "Tenant to replace light bulb",
            "Tenant to reset smoke alarm",
            "Tenant to arrange garden maintenance",
            "Tenant responsible for repair",
            "Tenant charged for damage",
            "Landlord to arrange repair",
            "Landlord to monitor",
            "Landlord arranging contractor",
            "Plumber required - urgent",
            "Plumber required - non-urgent",
            "Electrician required - urgent",
            "Electrician required - non-urgent",
            "Gas Safe engineer required - urgent",
            "Gas Safe engineer required - non-urgent",
            "Heating engineer required",
            "Carpenter required",
            "Glazier required",
            "Plasterer required",
            "Decorator required",
            "Cleaner required",
            "Carpet cleaner required",
            "Gardener required",
            "Pest control required",
            "Roofer required",
            "Gutter clearance required",
            "Handyperson required",
            "Locksmith required",
            "Appliance engineer required",
            "Window repair specialist required",
            "Damp specialist required",
            "Drainage specialist required",
            "Works scheduled for [date]",
            "Contractor to attend within 24 hours",
            "Contractor to attend within 7 days",
            "Contractor to attend within 14 days",
            "Awaiting contractor availability",
            "Awaiting parts/materials",
            "Quote required before proceeding",
            "Multiple quotes being obtained",
            "Repair costs to be recovered from tenant",
            "Repair covered by landlord insurance",
            "Repair to be completed at end of tenancy",
            "Temporary solution implemented",
            "Permanent solution required",
            "Monitoring situation",
            "Issue noted - no action required",
            "Issue acceptable - wear and tear",
            "Issue resolved during inspection",
            "Follow-up inspection required",
            "Awaiting landlord decision",
            "Awaiting tenant response",
            "Reported to building management",
            "Reported to freeholder",
            "Warranty claim submitted",
            "Insurance claim submitted"
        )
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityInspectionRoomBinding.inflate(layoutInflater)
        setContentView(binding.root)
        applyWindowInsetsToBinding(binding.root)
        cameraBinding = binding.cameraLayout
        cameraBinding.rvRoomItems.layoutManager = LinearLayoutManager(this, LinearLayoutManager.HORIZONTAL, false)
        cameraBinding.rvRoomItems.adapter = ImageAdapter(allImages, title = room?.name ?: "", onDelete = {
            capturedUris.clear()
            capturedUris.addAll(allImages.filterIsInstance<ImageItem.Local>().map { it.uri })
        })

        room = intent.getParcelableExtra("room")
        reportId = intent.getStringExtra("reportId") ?: ""
        reportStatus = intent.getStringExtra("reportStatus") ?: ""

        binding.tvTitle.text = room?.name ?: ""

        // Camera
        cameraBinding.ivAddImage.setOnClickListener {
            CameraActivity.pendingUris.clear()
            startActivityForResult(Intent(this, CameraActivity::class.java), CAMERA_REQUEST)
        }

        // All ok / Issues found toggles
        binding.btnAllOk.setOnClickListener { setIssueState(false) }
        binding.btnIssuesFound.setOnClickListener { setIssueState(true) }

        // Issue suggestions
        issueSuggestionsAdapter = SuggestionsAdapter(
            this,
            ISSUE_SUGGESTIONS,
            object : SuggestionsAdapter.OnItemClickInterface {
                override fun onItemClick(item: String) {
                    val fullText = binding.etIssueNote.text.toString()
                    val lastSemicolon = fullText.lastIndexOf(';')
                    val prefix = if (lastSemicolon >= 0) fullText.substring(0, lastSemicolon + 1).trimEnd() + " " else ""
                    val newText = "$prefix$item; "
                    binding.etIssueNote.setText(newText)
                    binding.etIssueNote.setSelection(newText.length)
                    issueSuggestionsAdapter.filter("")
                }
            }
        )
        binding.rvIssueSuggestions.layoutManager =
            LinearLayoutManager(this, LinearLayoutManager.HORIZONTAL, false)
        binding.rvIssueSuggestions.adapter = issueSuggestionsAdapter

        binding.etIssueNote.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) {
                if (isHandlingEnter) return
                val fullText = s?.toString() ?: ""
                if (fullText.contains('\n')) {
                    isHandlingEnter = true
                    val newText = fullText.replace("\n", "").trimEnd()
                    val withSemicolon = if (newText.endsWith(';')) "$newText " else "$newText; "
                    binding.etIssueNote.setText(withSemicolon)
                    binding.etIssueNote.setSelection(withSemicolon.length)
                    issueSuggestionsAdapter.filter("")
                    isHandlingEnter = false
                    return
                }
                val lastSemicolon = fullText.lastIndexOf(';')
                val currentWord = if (lastSemicolon >= 0) fullText.substring(lastSemicolon + 1).trim() else fullText.trim()
                issueSuggestionsAdapter.filter(currentWord)
            }
        })

        // Priority dropdown
        binding.spinnerPriority.setOnClickListener { showPriorityDropdown() }

        binding.btnDone.setOnClickListener { upsertRoomInspectionApi() }
        binding.ivBack.setOnClickListener { finish() }

        setIssueState(false)
        fetchRoomData()

        val isReadOnly = reportStatus == TenantReportStatus.COMPLETED.value ||
                reportStatus == TenantReportStatus.HISTORICAL.value ||
                reportStatus == TenantReportStatus.TENANT_REVIEW.value
        if (isReadOnly) applyReadOnlyMode()
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == CAMERA_REQUEST && resultCode == Activity.RESULT_OK) {
            val newUris = CameraActivity.pendingUris.toList()
            capturedUris.addAll(newUris)
            allImages.addAll(newUris.map { ImageItem.Local(it) })
            cameraBinding.rvRoomItems.adapter?.notifyDataSetChanged()
        }
    }

    private fun setIssueState(issue: Boolean) {
        isIssue = issue
        val selectedBg = ContextCompat.getDrawable(this, R.drawable.bg_white_selected_black_stroke)
        val defaultBg = ContextCompat.getDrawable(this, R.drawable.bg_edittext)

        binding.btnAllOk.background = if (!issue) selectedBg else defaultBg
        binding.btnIssuesFound.background = if (issue) selectedBg else defaultBg

        binding.issuesExpandedLayout.visibility = if (issue) View.VISIBLE else View.GONE
    }

    private fun applyReadOnlyMode() {
        cameraBinding.ivAddImage.isEnabled = false
        cameraBinding.ivAddImage.alpha = 0.5f
        binding.btnAllOk.isEnabled = false
        binding.btnIssuesFound.isEnabled = false
        binding.etIssueNote.isEnabled = false
        binding.spinnerPriority.isEnabled = false
        binding.spinnerPriority.alpha = 0.5f
        binding.rvIssueSuggestions.isEnabled = false
        binding.btnDone.visibility = View.GONE
    }

    private fun fetchRoomData() {
        makeApiRequest(
            apiServiceClass = MyApi::class.java,
            context = this,
            showLoading = false,
            requestAction = { api ->
                api.getInspectionRoomById(
                    report_id = reportId,
                    include_items = false,
                    include_room_inspections = true,
                    include_attachments = true
                )
            },
            listener = object : ApiResponseListener<ApiResponse<ArrayList<RoomsResponse>>> {
                override fun onSuccess(response: ApiResponse<ArrayList<RoomsResponse>>) {
                    val roomData = response.data?.find { it.id == room?.id } ?: return
                    allImages.clear()
                    roomData.attachments?.forEach { attachment ->
                        allImages.add(ImageItem.Remote(attachment.id, "${ApiClient.IMAGE_BASE_URL}${attachment.storageKey}"))
                    }
                    cameraBinding.rvRoomItems.adapter?.notifyDataSetChanged()
                    roomData.inspection?.firstOrNull()?.let { populateInspection(it) }
                }
                override fun onFailure(errorMessage: ErrorResponse?) {}
                override fun onError(throwable: Throwable) {}
            }
        )
    }

    private fun populateInspection(inspection: RoomInspection) {
        val issue = inspection.isIssue ?: false
        setIssueState(issue)

        if (issue) {
            inspection.note?.let { binding.etIssueNote.setText(it) }

            val matched = priorityValues.firstOrNull { it != null && it.equals(inspection.priority, ignoreCase = true) }
            if (matched != null) {
                selectedPriority = matched
                val label = priorityLabels[priorityValues.indexOf(matched)]
                binding.tvPriorityValue.text = label
                binding.tvPriorityValue.setTextColor(ContextCompat.getColor(this, R.color.black))
                val iconRes = when (matched) {
                    "observation" -> R.drawable.svg_observation
                    "action required" -> R.drawable.svg_action_required
                    "urgent" -> R.drawable.svg_urgent
                    else -> null
                }
                val subtitle = when (matched) {
                    "observation" -> "Minor issue for information only"
                    "action required" -> "Issue that needs to be addressed"
                    "urgent" -> "Critical issue requiring immediate attention"
                    else -> null
                }
                if (iconRes != null) {
                    binding.ivSelectedPriorityIcon.setImageResource(iconRes)
                    binding.ivSelectedPriorityIcon.visibility = View.VISIBLE
                }
                if (subtitle != null) {
                    binding.tvPrioritySubtitleValue.text = subtitle
                    binding.tvPrioritySubtitleValue.visibility = View.VISIBLE
                }
            }
        }
    }

    private fun showPriorityDropdown() {
        data class PriorityItem(val label: String, val value: String, val subtitle: String, val iconRes: Int)

        val items = listOf(
            PriorityItem("Observation", "observation", "Minor issue for information only", R.drawable.svg_observation),
            PriorityItem("Action required", "action required", "Issue that needs to be addressed", R.drawable.svg_action_required),
            PriorityItem("Urgent", "urgent", "Critical issue requiring immediate attention", R.drawable.svg_urgent)
        )

        val adapter = object : android.widget.BaseAdapter() {
            override fun getCount() = items.size
            override fun getItem(position: Int) = items[position]
            override fun getItemId(position: Int) = position.toLong()
            override fun getView(position: Int, convertView: android.view.View?, parent: android.view.ViewGroup): android.view.View {
                val view = convertView ?: layoutInflater.inflate(R.layout.item_priority_option, parent, false)
                val item = items[position]
                view.findViewById<android.widget.ImageView>(R.id.ivPriorityIcon).setImageResource(item.iconRes)
                view.findViewById<android.widget.TextView>(R.id.tvPriorityTitle).text = item.label
                view.findViewById<android.widget.TextView>(R.id.tvPrioritySubtitle).text = item.subtitle
                val ivCheck = view.findViewById<android.widget.ImageView>(R.id.ivCheck)
                ivCheck.visibility = if (item.value == selectedPriority) android.view.View.VISIBLE else android.view.View.GONE
                return view
            }
        }

        val popup = ListPopupWindow(this)
        popup.anchorView = binding.spinnerPriority
        popup.setAdapter(adapter)
        popup.width = binding.spinnerPriority.width
        popup.height = ListPopupWindow.WRAP_CONTENT
        popup.verticalOffset = resources.getDimensionPixelSize(com.intuit.sdp.R.dimen._4sdp)
        popup.setBackgroundDrawable(ContextCompat.getDrawable(this, R.drawable.bg_edittext))
        popup.isModal = true
        popup.setOnItemClickListener { _, _, position, _ ->
            val item = items[position]
            selectedPriority = item.value
            binding.tvPriorityValue.text = item.label
            binding.tvPriorityValue.setTextColor(ContextCompat.getColor(this, R.color.black))
            binding.tvPrioritySubtitleValue.text = item.subtitle
            binding.tvPrioritySubtitleValue.visibility = View.VISIBLE
            binding.ivSelectedPriorityIcon.setImageResource(item.iconRes)
            binding.ivSelectedPriorityIcon.visibility = View.VISIBLE
            popup.dismiss()
        }
        popup.show()
    }

    private fun upsertRoomInspectionApi() {
        val roomId = room?.id ?: return
        val note = if (isIssue) binding.etIssueNote.text.toString().ifEmpty { null } else null
        val priority = if (isIssue) selectedPriority else null

        makeApiRequest(
            apiServiceClass = MyApi::class.java,
            context = this,
            showLoading = true,
            requestAction = { api ->
                api.upsertRoomInspection(
                    UpsertRoomInspectionRequest(
                        room_id = roomId,
                        is_issue = isIssue,
                        note = note,
                        priority = priority
                    )
                )
            },
            listener = object : ApiResponseListener<ApiResponse<Any>> {
                override fun onSuccess(response: ApiResponse<Any>) {
                    uploadPhotosIfNeeded(roomId)
                }

                override fun onFailure(errorMessage: ErrorResponse?) {
                    showToast(errorMessage?.error?.message ?: "Failed to save inspection")
                }

                override fun onError(throwable: Throwable) {
                    Log.e("InspectionRoom", "Error: ${throwable.message}")
                    showToast("Error: ${throwable.message}")
                }
            }
        )
    }

    private fun uploadPhotosIfNeeded(entityId: String) {
        if (capturedUris.isEmpty()) { finish(); return }
        AttachmentUploadHelper.uploadImages(
            activity = this,
            imageUris = capturedUris,
            entityId = entityId,
            entityType = "ROOM",
            onComplete = { finish() },
            onError = { finish() }
        )
    }
}
