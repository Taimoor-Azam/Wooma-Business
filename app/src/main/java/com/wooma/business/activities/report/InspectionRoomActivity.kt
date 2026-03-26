package com.wooma.business.activities.report

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.graphics.Typeface
import android.net.Uri
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.Spinner
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.wooma.business.R
import com.wooma.business.activities.BaseActivity
import com.wooma.business.customs.AttachmentUploadHelper
import com.wooma.business.data.network.ApiResponseListener
import com.wooma.business.data.network.MyApi
import com.wooma.business.data.network.makeApiRequest
import com.wooma.business.data.network.showToast
import com.wooma.business.databinding.ActivityInspectionRoomBinding
import com.wooma.business.databinding.AddImageLayoutBinding
import com.wooma.business.model.ApiResponse
import com.wooma.business.model.ErrorResponse
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
    private val CAMERA_REQUEST = 1001

    private val conditionChips = listOf(
        "Marked", "Scuffed", "Stained", "Loose fitting",
        "Cracked", "Damp", "Mould", "Faded"
    )
    private val selectedChips = mutableSetOf<String>()
    private val priorityValues = listOf(null, "observation", "action required", "urgent")

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityInspectionRoomBinding.inflate(layoutInflater)
        setContentView(binding.root)
        applyWindowInsetsToBinding(binding.root)
        cameraBinding = binding.cameraLayout

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


        // Condition chips
        binding.rvConditionChips.layoutManager =
            LinearLayoutManager(this, LinearLayoutManager.HORIZONTAL, false)
        binding.rvConditionChips.adapter = ChipAdapter()

        // Priority spinner — no default selection
        val priorityOptions = listOf("Select priority", "Observation", "Action required", "Urgent")

        val spinnerAdapter = object : ArrayAdapter<String>(this, android.R.layout.simple_spinner_item, priorityOptions) {
            override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
                val tv = super.getView(position, convertView, parent) as TextView
                tv.typeface = resources.getFont(R.font.sofiasans_regular)
                tv.textSize = 12f
                tv.setTextColor(ContextCompat.getColor(context, if (position == 0) R.color.lbls_color else R.color.black))
                return tv
            }

            override fun getDropDownView(position: Int, convertView: View?, parent: ViewGroup): View {
                val tv = super.getDropDownView(position, convertView, parent) as TextView
                tv.typeface = resources.getFont(R.font.sofiasans_regular)
                tv.textSize = 12f
                tv.setPadding(
                    resources.getDimensionPixelSize(com.intuit.sdp.R.dimen._12sdp),
                    resources.getDimensionPixelSize(com.intuit.sdp.R.dimen._12sdp),
                    resources.getDimensionPixelSize(com.intuit.sdp.R.dimen._12sdp),
                    resources.getDimensionPixelSize(com.intuit.sdp.R.dimen._12sdp)
                )
                tv.setTextColor(ContextCompat.getColor(context, R.color.black))
                // Dim the hint item in dropdown
                if (position == 0) tv.setTextColor(ContextCompat.getColor(context, R.color.lbls_color))
                return tv
            }
        }.apply { setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item) }

        binding.spinnerPriority.adapter = spinnerAdapter
        binding.spinnerPriority.setSelection(0, false)  // show hint, no real selection

        binding.spinnerPriority.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>, view: View?, position: Int, id: Long) {
                selectedPriority = priorityValues[position]
            }
            override fun onNothingSelected(parent: AdapterView<*>) {
                selectedPriority = null
            }
        }

        binding.btnDone.setOnClickListener { upsertRoomInspectionApi() }
        binding.ivBack.setOnClickListener { finish() }

        // Default state
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
            capturedUris.addAll(CameraActivity.pendingUris)
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


    private fun updateNoteFromChips() {
        val chipsText = selectedChips.joinToString("; ")
        val currentNote = binding.etIssueNote.text.toString()
        // Only auto-update if note is empty or previously set by chips
        if (currentNote.isEmpty() || conditionChips.any { currentNote.contains(it) }) {
            binding.etIssueNote.setText(chipsText)
        }
    }

    private fun applyReadOnlyMode() {
        cameraBinding.ivAddImage.isEnabled = false
        cameraBinding.ivAddImage.alpha = 0.5f
        binding.btnAllOk.isEnabled = false
        binding.btnIssuesFound.isEnabled = false
        binding.etIssueNote.isEnabled = false
        binding.spinnerPriority.isEnabled = false
        binding.rvConditionChips.isEnabled = false
        binding.btnDone.visibility = View.GONE
    }

    private fun fetchRoomData() {
        val roomId = room?.id ?: return
        makeApiRequest(
            apiServiceClass = MyApi::class.java,
            context = this,
            showLoading = false,
            requestAction = { api ->
                api.getRoomById(
                    id = roomId,
                    report_id = reportId,
                    include_items = false,
                    include_room_inspections = true,
                    include_attachments = true
                )
            },
            listener = object : ApiResponseListener<ApiResponse<RoomsResponse>> {
                override fun onSuccess(response: ApiResponse<RoomsResponse>) {
                    val inspection = response.data?.inspection?.firstOrNull()
                    inspection?.let { populateInspection(it) }
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
            // Note
            inspection.note?.let { binding.etIssueNote.setText(it) }

            // Chips — match words in note against conditionChips
            selectedChips.clear()
            val noteText = inspection.note ?: ""
            conditionChips.forEach { chip ->
                if (noteText.contains(chip, ignoreCase = true)) selectedChips.add(chip)
            }
            binding.rvConditionChips.adapter?.notifyDataSetChanged()

            // Priority spinner
            val priorityIndex = priorityValues.indexOfFirst {
                it != null && it.equals(inspection.priority, ignoreCase = true)
            }.takeIf { it >= 0 } ?: 0
            binding.spinnerPriority.setSelection(priorityIndex, false)
            selectedPriority = priorityValues[priorityIndex]
        }
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

    inner class ChipAdapter : RecyclerView.Adapter<ChipAdapter.ChipHolder>() {

        inner class ChipHolder(val tv: TextView) : RecyclerView.ViewHolder(tv)

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ChipHolder {
            val tv = TextView(parent.context).apply {
                val pad = resources.getDimensionPixelSize(com.intuit.sdp.R.dimen._8sdp)
                val margin = resources.getDimensionPixelSize(com.intuit.sdp.R.dimen._6sdp)
                setPadding(pad * 2, pad, pad * 2, pad)
                layoutParams = RecyclerView.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
                ).apply { marginEnd = margin }
                setTextColor(ContextCompat.getColor(context, R.color.black))
                textSize = 12f
                typeface = resources.getFont(R.font.sofiasans_regular)
                background = ContextCompat.getDrawable(context, R.drawable.bg_edittext)
            }
            return ChipHolder(tv)
        }

        override fun getItemCount() = conditionChips.size

        override fun onBindViewHolder(holder: ChipHolder, position: Int) {
            val chip = conditionChips[position]
            holder.tv.text = chip
            val isSelected = selectedChips.contains(chip)
            holder.tv.background = ContextCompat.getDrawable(
                this@InspectionRoomActivity,
                if (isSelected) R.drawable.bg_white_selected_black_stroke else R.drawable.bg_edittext
            )

            holder.tv.setOnClickListener {
                if (selectedChips.contains(chip)) selectedChips.remove(chip)
                else selectedChips.add(chip)
                notifyItemChanged(position)
                updateNoteFromChips()
            }
        }
    }
}
