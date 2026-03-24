package com.wooma.business.activities.property

import android.os.Bundle
import android.util.Log
import com.wooma.business.activities.BaseActivity
import com.wooma.business.customs.Utils
import com.wooma.business.data.network.ApiResponseListener
import com.wooma.business.data.network.MyApi
import com.wooma.business.data.network.makeApiRequest
import com.wooma.business.data.network.showToast
import com.wooma.business.databinding.ActivityEditPropertyBinding
import com.wooma.business.model.ApiResponse
import com.wooma.business.model.ErrorResponse
import com.wooma.business.model.PropertiesRequest
import com.wooma.business.model.Property

class EditPropertyActivity : BaseActivity() {
    private lateinit var binding: ActivityEditPropertyBinding

    var id = ""
    var address = ""
    var address_line_2 = ""
    var city = ""
    var postcode = ""

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        binding = ActivityEditPropertyBinding.inflate(layoutInflater)
        setContentView(binding.root)
        applyWindowInsetsToBinding(binding.root)

        id = intent.getStringExtra("id") ?: ""
        address = intent.getStringExtra("address") ?: ""
        address_line_2 = intent.getStringExtra("address_line_2") ?: ""
        city = intent.getStringExtra("city") ?: ""
        postcode = intent.getStringExtra("postcode") ?: ""

        binding.btnSave.setOnClickListener {
            if (isValid()) {
                updatePropertyApi()
            }
        }

        binding.ivBack.setOnClickListener { finish() }
        binding.ivReportArchive.setOnClickListener {
            Utils.showDialogBox(
                this@EditPropertyActivity,
                "Archive Property",
                "Are you sure you want to archive ${address}, ${city}, ${postcode} to your archive properties?"
            ) {
                archivePropertyApi(id)
            }
        }
        initView()
    }

    private fun initView() {
        if (address.isNotEmpty()) {
            binding.etAddressOne.setText(address)
        }
        if (address_line_2.isNotEmpty()) {
            binding.etAddressTwo.setText(address_line_2)
        }
        if (city.isNotEmpty()) {
            binding.etCity.setText(city)
        }
        if (postcode.isNotEmpty()) {
            binding.etPostalCode.setText(postcode)
        }
    }


    private fun isValid(): Boolean {

        if (binding.etAddressOne.text.toString().isEmpty()) {
            showToast("Please enter address one")
            return false
        } else if (binding.etCity.text.toString().isEmpty()) {
            showToast("Please enter city name")

            return false
        } else if (binding.etPostalCode.text.toString().isEmpty()) {
            showToast("Please enter Postal Code")
            return false
        }
        return true
    }

    private fun updatePropertyApi() {
        val body = PropertiesRequest(
            binding.etAddressOne.text.toString(),
            binding.etAddressTwo.text.toString(),
            binding.etCity.text.toString(),
            binding.etPostalCode.text.toString(),
            null
        )

        makeApiRequest(
            apiServiceClass = MyApi::class.java,
            context = this,
            showLoading = true,
            requestAction = { apiService -> apiService.updateProperty(id, body) },
            listener = object : ApiResponseListener<ApiResponse<Property>> {
                override fun onSuccess(response: ApiResponse<Property>) {
                    if (response.success) {
                        showToast("Property Updated successfully")
                        finish()
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

    private fun archivePropertyApi(id: String) {
        makeApiRequest(
            apiServiceClass = MyApi::class.java,
            context = this,
            showLoading = true,
            requestAction = { apiService -> apiService.archiveProperty(id) },
            listener = object : ApiResponseListener<ApiResponse<Property>> {
                override fun onSuccess(response: ApiResponse<Property>) {
                    if (response.success) {
                        showToast("Archived SuccessFully")
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