package com.wooma.activities.report.inventorysettings

import android.graphics.Color
import android.os.Bundle
import android.text.SpannableString
import android.text.style.ForegroundColorSpan
import android.util.Log
import android.view.Gravity
import android.view.Menu
import android.view.View
import android.widget.Toast
import androidx.appcompat.widget.PopupMenu
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.wooma.activities.BaseActivity
import com.wooma.R
import com.wooma.data.local.entity.AssessorEntity
import com.wooma.data.network.showToast
import com.wooma.data.repository.ConfigRepository
import com.wooma.data.repository.ReportRepository
import com.wooma.databinding.ActivityChangeAssessorBinding
import com.wooma.model.Assessor
import com.wooma.sync.ConnectivityObserver
import com.wooma.sync.SyncScheduler
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch

class ChangeAssessorActivity : BaseActivity() {
    private lateinit var binding: ActivityChangeAssessorBinding

    private val assessorsList = mutableListOf<AssessorEntity>()
    var assessor: Assessor? = null

    var selectedAssessorId = ""
    var selectedAssessorName = ""
    var reportId = ""

    private val configRepo by lazy { ConfigRepository(this) }
    private val reportRepo by lazy { ReportRepository(this) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        binding = ActivityChangeAssessorBinding.inflate(layoutInflater)
        setContentView(binding.root)
        applyWindowInsetsToBinding(binding.root)

        assessor = intent.getParcelableExtra("assessor")
        reportId = intent.getStringExtra("reportId") ?: ""

        if (assessor != null) {
            binding.tvCurrentAssessor.text = assessor?.first_name + " " + assessor?.last_name
        }

        binding.tvReportChangeTo.setOnClickListener { view ->
            if (assessorsList.isEmpty()) {
                Toast.makeText(this, "No assessor available", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            showDynamicPopupMenu(view)
        }

        binding.ivBack.setOnClickListener { finish() }

        // Observe from local DB
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                configRepo.observeAssessors().collect { list ->
                    assessorsList.clear()
                    assessorsList.addAll(list.filter { it.userId != assessor?.id })
                }
            }
        }
        // Background refresh from server
        lifecycleScope.launch {
            try { configRepo.seedReferenceData() } catch (_: Exception) {}
        }

        binding.btnAddReport.setOnClickListener {
            if (selectedAssessorId.isNotEmpty()) {
                changeAssessorApi()
            } else {
                showToast("Please Select Report First.")
            }
        }
    }

    private fun changeAssessorApi() {
        if (!ConnectivityObserver(this).isConnected()) {
            showToast("Internet connection required")
            return
        }
        val nameParts = selectedAssessorName.split(" ", limit = 2)
        lifecycleScope.launch {
            reportRepo.updateAssessor(
                reportId,
                selectedAssessorId,
                nameParts.getOrElse(0) { "" },
                nameParts.getOrElse(1) { "" }
            )
            SyncScheduler.scheduleImmediateSync(this@ChangeAssessorActivity)
            binding.tvCurrentAssessor.text = selectedAssessorName
            showToast("Assessor changed successfully")
        }
    }

    fun showDynamicPopupMenu(view: View) {
        val popup = PopupMenu(
            this,
            view,
            Gravity.START,
            0,
            R.style.PopupMenuStyle
        )

        // 3. Iterate and add items dynamically
        for (i in assessorsList.indices) {
            popup.menu.add(
                Menu.NONE,
                i,
                Menu.NONE,
                assessorsList[i].firstName + " " + assessorsList[i].lastName
            )
        }
        for (i in 0 until popup.menu.size()) {
            val item = popup.menu.getItem(i)
            val spannable = SpannableString(item.title)
            spannable.setSpan(ForegroundColorSpan(Color.BLACK), 0, spannable.length, 0)
            item.title = spannable
        }

        // 4. Handle Clicks
        popup.setOnMenuItemClickListener { menuItem ->
            val id = menuItem.itemId
            binding.tvReportChangeTo.text = "${menuItem.title}"
            selectedAssessorId = assessorsList[id].userId
            selectedAssessorName = assessorsList[id].firstName + " " + assessorsList[id].lastName
            true
        }

        popup.show()
    }
}
