package com.wooma.activities.report.inventorysettings

import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.text.SpannableString
import android.text.style.ForegroundColorSpan
import android.view.Gravity
import android.view.Menu
import android.view.View
import androidx.appcompat.widget.PopupMenu
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.wooma.activities.BaseActivity
import com.wooma.activities.report.ReportListingActivity
import com.wooma.R
import com.wooma.data.local.entity.ReportTypeEntity
import com.wooma.data.network.showToast
import com.wooma.data.repository.ConfigRepository
import com.wooma.data.repository.ReportRepository
import com.wooma.databinding.ActivityChangeReportTypeBinding
import com.wooma.model.enums.ReportTypes
import com.wooma.sync.ConnectivityObserver
import com.wooma.sync.SyncScheduler
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch

class ChangeReportTypeActivity : BaseActivity() {
    private lateinit var binding: ActivityChangeReportTypeBinding

    val reportTypeList = mutableListOf<ReportTypeEntity>()

    var selectedReportTypeId = ""
    var reportTypeName = ""
    var reportTypeId = ""
    var reportId = ""
    var propertyId = ""

    private val configRepo by lazy { ConfigRepository(this) }
    private val reportRepo by lazy { ReportRepository(this) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        binding = ActivityChangeReportTypeBinding.inflate(layoutInflater)
        setContentView(binding.root)
        applyWindowInsetsToBinding(binding.root)

        reportTypeName = intent.getStringExtra("reportTypeName") ?: ""
        reportTypeId = intent.getStringExtra("reportTypeId") ?: ""
        reportId = intent.getStringExtra("reportId") ?: ""
        propertyId = intent.getStringExtra("propertyId") ?: ""

        if (reportTypeName.isNotEmpty()) {
            binding.tvCurrentReportType.text = reportTypeName
        }

        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                configRepo.observeReportTypes().collect { list ->
                    reportTypeList.clear()
                    reportTypeList.addAll(list.filter {
                        (it.typeCode == ReportTypes.CHECK_OUT.value || it.typeCode == ReportTypes.INVENTORY.value)
                            && it.id != reportTypeId
                    })
                }
            }
        }
        lifecycleScope.launch {
            try { configRepo.seedReferenceData() } catch (_: Exception) {}
        }

        binding.tvReportChangeTo.setOnClickListener { view ->
            showDynamicPopupMenu(view)
        }

        binding.btnAddReport.setOnClickListener {
            if (selectedReportTypeId.isNotEmpty()) {
                changeReportTypeApi()
            } else {
                showToast("Please Select Report First.")
            }
        }

        binding.ivBack.setOnClickListener { finish() }
    }

    /*private fun openPopUp(view: View) {
        val popup = PopupMenu(
            this,
            view,
            Gravity.START,
            0,
            R.style.PopupMenuStyle
        )   // this = Activity context
        popup.menuInflater.inflate(R.menu.menu_popup, popup.menu)

        popup.setOnMenuItemClickListener { item ->
            binding.tvReportChangeTo.text = "${item.title}"
            when (item.itemId) {
                R.id.action_inspection -> {
                    // handle edit
                    true
                }

                R.id.action_checkout -> {
                    // handle delete
                    true
                }

                else -> false
            }
        }

        popup.show()
    }*/

    fun showDynamicPopupMenu(view: View) {
        val popup = PopupMenu(
            this,
            view,
            Gravity.START,
            0,
            R.style.PopupMenuStyle
        )

        // 3. Iterate and add items dynamically
        for (i in reportTypeList.indices) {
            popup.menu.add(Menu.NONE, i, Menu.NONE, reportTypeList[i].displayName)
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
            selectedReportTypeId = reportTypeList[id].id
            true
        }

        popup.show()
    }

    private fun changeReportTypeApi() {
        if (!ConnectivityObserver(this).isConnected()) {
            showToast("Internet connection required")
            return
        }
        val selected = reportTypeList.find { it.id == selectedReportTypeId } ?: return
        lifecycleScope.launch {
            reportRepo.updateReportType(reportId, selected.id, selected.displayName, selected.typeCode)
            SyncScheduler.scheduleImmediateSync(this@ChangeReportTypeActivity)
            showToast("Report Type changed successfully")
            startActivity(
                Intent(this@ChangeReportTypeActivity, ReportListingActivity::class.java)
                    .putExtra("propertyId", propertyId)
                    .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP)
            )
        }
    }
}
