package com.wooma.activities.report.inventorysettings

import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.text.SpannableString
import android.text.style.ForegroundColorSpan
import android.util.Log
import android.view.Gravity
import android.view.Menu
import android.view.View
import androidx.appcompat.widget.PopupMenu
import com.wooma.activities.BaseActivity
import com.wooma.activities.report.ReportListingActivity
import com.wooma.R
import com.wooma.data.network.ApiResponseListener
import com.wooma.data.network.MyApi
import com.wooma.data.network.makeApiRequest
import com.wooma.data.network.showToast
import com.wooma.databinding.ActivityChangeReportTypeBinding
import com.wooma.model.ApiResponse
import com.wooma.model.ErrorResponse
import com.wooma.model.ReportData
import com.wooma.model.ReportType
import com.wooma.model.ReportTypeResponse
import com.wooma.model.changeReportType
import com.wooma.model.enums.ReportTypes

class ChangeReportTypeActivity : BaseActivity() {
    private lateinit var binding: ActivityChangeReportTypeBinding

    val reportTypeList: ArrayList<ReportType> = ArrayList()

    var selectedReportTypeId = ""
    var reportTypeName = ""
    var reportTypeId = ""
    var reportId = ""
    var propertyId = ""

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

        getReportTypeListApi()

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
            popup.menu.add(Menu.NONE, i, Menu.NONE, reportTypeList[i].display_name)
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
        val item = changeReportType(selectedReportTypeId)
        makeApiRequest(
            apiServiceClass = MyApi::class.java,
            context = this,
            showLoading = true,
            requestAction = { apiService -> apiService.changeReportType(reportId, item) },
            listener = object : ApiResponseListener<ApiResponse<ReportData>> {
                override fun onSuccess(response: ApiResponse<ReportData>) {
                    if (response.success) {
                        showToast("Report Type changed successfully")
                        startActivity(
                            Intent(this@ChangeReportTypeActivity, ReportListingActivity::class.java)
                                .putExtra("propertyId", propertyId)
                                .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP)
                        )
                    }
                }

                override fun onFailure(errorMessage: ErrorResponse?) {
                    // Handle API error
                    Log.e("API", errorMessage?.error?.message ?: "")
                    showToast(errorMessage?.error?.message ?: "")
                }

                override fun onError(throwable: Throwable) {
                    // Handle network error
                    Log.e("API", "Error: ${throwable.message}")
                    showToast("Error: ${throwable.message}")
                }
            }
        )
    }

    private fun getReportTypeListApi() {
        makeApiRequest(
            apiServiceClass = MyApi::class.java,
            context = this,
            showLoading = true,
            requestAction = { apiService -> apiService.getReportTypes() },
            listener = object : ApiResponseListener<ApiResponse<ReportTypeResponse>> {
                override fun onSuccess(response: ApiResponse<ReportTypeResponse>) {
                    if (response.data.data.isNotEmpty()) {
                        reportTypeList.clear()
                        for (item in response.data.data) {
                            if (item.type_code == ReportTypes.CHECK_OUT.value || item.type_code == ReportTypes.INVENTORY.value) {
                                if (item.id != reportTypeId)
                                    reportTypeList.add(item)
                            }
                        }
                    }
                }

                override fun onFailure(errorMessage: ErrorResponse?) {
                    // Handle API error
                    Log.e("API", errorMessage?.error?.message ?: "")
                    showToast(errorMessage?.error?.message ?: "")
                }

                override fun onError(throwable: Throwable) {
                    // Handle network error
                    Log.e("API", "Error: ${throwable.message}")
                    showToast("Error: ${throwable.message}")
                }
            }
        )
    }
}