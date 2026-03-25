package com.wooma.business.activities.report.inventorysettings

import android.os.Bundle
import android.util.Log
import android.view.Gravity
import android.view.Menu
import android.view.View
import androidx.appcompat.widget.PopupMenu
import com.wooma.business.R
import com.wooma.business.activities.BaseActivity
import com.wooma.business.data.network.ApiResponseListener
import com.wooma.business.data.network.MyApi
import com.wooma.business.data.network.makeApiRequest
import com.wooma.business.data.network.showToast
import com.wooma.business.databinding.ActivityChangeReportTypeBinding
import com.wooma.business.model.ApiResponse
import com.wooma.business.model.ErrorResponse
import com.wooma.business.model.ReportData
import com.wooma.business.model.ReportType
import com.wooma.business.model.ReportTypeResponse
import com.wooma.business.model.changeReportType
import com.wooma.business.model.enums.ReportTypes

class ChangeReportTypeActivity : BaseActivity() {
    private lateinit var binding: ActivityChangeReportTypeBinding

    val reportTypeList: ArrayList<ReportType> = ArrayList()

    var selectedReportTypeId = ""
    var reportTypeName = ""
    var reportTypeId = ""
    var reportId = ""

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        binding = ActivityChangeReportTypeBinding.inflate(layoutInflater)
        setContentView(binding.root)
        applyWindowInsetsToBinding(binding.root)

        reportTypeName = intent.getStringExtra("reportTypeName") ?: ""
        reportTypeId = intent.getStringExtra("reportTypeId") ?: ""
        reportId = intent.getStringExtra("reportId") ?: ""


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