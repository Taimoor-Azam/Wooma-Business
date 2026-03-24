package com.wooma.business.activities.property

import android.os.Bundle
import android.util.Log
import com.wooma.business.activities.BaseActivity
import com.wooma.business.data.network.ApiResponseListener
import com.wooma.business.data.network.MyApi
import com.wooma.business.data.network.makeApiRequest
import com.wooma.business.data.network.showToast
import com.wooma.business.databinding.ActivityAddPropertyBinding
import com.wooma.business.databinding.ActivityEditPropertyBinding
import com.wooma.business.model.ApiResponse
import com.wooma.business.model.ErrorResponse
import com.wooma.business.model.PropertiesRequest
import com.wooma.business.model.Property
import com.wooma.business.model.TenantPropertiesWrapper
import com.wooma.business.storage.Prefs

class AddPropertyActivity : BaseActivity() {
    private lateinit var binding: ActivityAddPropertyBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        binding = ActivityAddPropertyBinding.inflate(layoutInflater)
        setContentView(binding.root)
        applyWindowInsetsToBinding(binding.root)

        binding.btnSave.setOnClickListener {
            if (isValid()){
                createPropertyApi()
            }
        }

        binding.ivBack.setOnClickListener { finish() }

    }

    fun isValid(): Boolean {

        if (binding.etAddressOne.text.toString().isEmpty()) {
            showToast("Please enter address one")
            return false
        } else if (binding.etAddressTwo.text.toString().isEmpty()) {
            showToast("Please enter address two")

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


    private fun createPropertyApi() {
        val body = PropertiesRequest(
            binding.etAddressOne.text.toString(),
            binding.etAddressTwo.text.toString(),
            binding.etCity.text.toString(),
            binding.etPostalCode.text.toString(),
            Prefs.getUser(this)?.id ?: ""
        )

        makeApiRequest(
            apiServiceClass = MyApi::class.java,
            context = this,
            showLoading = true,
            requestAction = { apiService -> apiService.createProperty(body) },
            listener = object : ApiResponseListener<ApiResponse<Property>> {
                override fun onSuccess(response: ApiResponse<Property>) {
                    if (response.success) {
                        showToast("Property Added successfully")
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

}