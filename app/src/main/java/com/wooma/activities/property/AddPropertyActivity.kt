package com.wooma.activities.property

import android.content.Intent
import android.os.Bundle
import android.util.Log
import com.wooma.activities.BaseActivity
import com.wooma.data.network.ApiResponseListener
import com.wooma.data.network.MyApi
import com.wooma.data.network.makeApiRequest
import com.wooma.data.network.showToast
import com.wooma.databinding.ActivityAddPropertyBinding
import com.wooma.model.ApiResponse
import com.wooma.model.ErrorResponse
import com.wooma.model.PostalAddress
import com.wooma.model.PropertiesRequest
import com.wooma.model.Property
import com.wooma.storage.Prefs

class AddPropertyActivity : BaseActivity() {
    private lateinit var binding: ActivityAddPropertyBinding

    var postalAddress: PostalAddress? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        binding = ActivityAddPropertyBinding.inflate(layoutInflater)
        setContentView(binding.root)
        applyWindowInsetsToBinding(binding.root)

        postalAddress = intent.getParcelableExtra("postalAddress")

        if (postalAddress != null) {
            initView()
        }

        binding.btnSave.setOnClickListener {
            if (isValid()) {
                createPropertyApi()
            }
        }

        binding.ivBack.setOnClickListener { finish() }

    }

    private fun initView() {
        binding.etAddressOne.setText(postalAddress?.line_1)
        binding.etAddressTwo.setText(postalAddress?.line_2 ?: "")
        binding.etCity.setText(postalAddress?.post_town)
        binding.etPostalCode.setText(postalAddress?.postcode)
    }

    private fun isValid(): Boolean {

        if (binding.etAddressOne.text.toString().isEmpty()) {
            showToast("Please enter address one")
            return false
        } /*else if (binding.etAddressTwo.text.toString().isEmpty()) {
            showToast("Please enter address two")
            return false
        } */ else if (binding.etCity.text.toString().isEmpty()) {
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
                        val resultIntent = Intent()
                        resultIntent.putExtra("propertyAdded", true)
                        resultIntent.putExtra("propertyId", response.data.id ?: "")
                        setResult(RESULT_OK, resultIntent)
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