package com.wooma.business.activities.report.inventorysettings

import android.os.Bundle
import android.util.Log
import android.view.Gravity
import android.view.Menu
import android.view.View
import android.widget.Toast
import androidx.appcompat.widget.PopupMenu
import com.wooma.business.R
import com.wooma.business.activities.BaseActivity
import com.wooma.business.data.network.ApiResponseListener
import com.wooma.business.data.network.MyApi
import com.wooma.business.data.network.makeApiRequest
import com.wooma.business.data.network.showToast
import com.wooma.business.databinding.ActivityChangeAssessorBinding
import com.wooma.business.model.ApiResponse
import com.wooma.business.model.Assessor
import com.wooma.business.model.AssessorUsers
import com.wooma.business.model.ChangeAssessor
import com.wooma.business.model.ErrorResponse
import com.wooma.business.model.ReportData

class ChangeAssessorActivity : BaseActivity() {
    private lateinit var binding: ActivityChangeAssessorBinding

    private val assessorsList = mutableListOf<AssessorUsers>()
    var assessor: Assessor? = null

    var selectedAssessorId = ""
    var selectedAssessorName = ""
    var reportId = ""

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

        getAssessorsApi()

        binding.btnAddReport.setOnClickListener {
            if (selectedAssessorId.isNotEmpty()) {
                changeAssessorApi()
            } else {
                showToast("Please Select Report First.")
            }
        }
    }

    private fun changeAssessorApi() {
        val item = ChangeAssessor(selectedAssessorId)
        makeApiRequest(
            apiServiceClass = MyApi::class.java,
            context = this,
            showLoading = true,
            requestAction = { apiService -> apiService.changeAssessor(reportId, item) },
            listener = object : ApiResponseListener<ApiResponse<ReportData>> {
                override fun onSuccess(response: ApiResponse<ReportData>) {
                    if (response.success) {
                        binding.tvCurrentAssessor.text = selectedAssessorName

                        showToast("Assessor changed successfully")
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
                assessorsList[i].first_name + " " + assessorsList[i].last_name
            )
        }

        // 4. Handle Clicks
        popup.setOnMenuItemClickListener { menuItem ->
            val id = menuItem.itemId
            binding.tvReportChangeTo.text = "${menuItem.title}"
            selectedAssessorId = assessorsList[id].user_id
            selectedAssessorName = assessorsList[id].first_name + " " + assessorsList[id].last_name
            true
        }

        popup.show()
    }

    private fun getAssessorsApi() {
        makeApiRequest(
            apiServiceClass = MyApi::class.java,
            context = this,
            showLoading = true,
            requestAction = { apiService -> apiService.getAssessors() },
            listener = object : ApiResponseListener<ApiResponse<ArrayList<AssessorUsers>>> {
                override fun onSuccess(response: ApiResponse<ArrayList<AssessorUsers>>) {
                    if (response.success && response.data.isNotEmpty()) {
                        assessorsList.clear()
                        for (item in response.data) {
                            if (item.user_id != assessor?.id) {
                                assessorsList.add(item)
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