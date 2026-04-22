package com.wooma.business.activities.auth

import android.content.Intent
import android.os.Bundle
import android.util.Log
import com.wooma.business.activities.BaseActivity
import com.wooma.business.activities.MainActivity
import com.wooma.business.data.network.ApiResponseListener
import com.wooma.business.data.network.MyApi
import com.wooma.business.data.network.makeApiRequest
import com.wooma.business.data.network.showToast
import com.wooma.business.databinding.ActivityActivateAccountBinding
import com.wooma.business.model.ApiResponse
import com.wooma.business.model.ErrorResponse
import com.wooma.business.model.OnboardingResponse
import com.wooma.business.model.User
import com.wooma.business.model.UserOnBoardRequest
import com.wooma.business.storage.Prefs

class ActivateAccountActivity : BaseActivity() {
    private lateinit var binding: ActivityActivateAccountBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        binding = ActivityActivateAccountBinding.inflate(layoutInflater)
        setContentView(binding.root)
        applyWindowInsetsToBinding(binding.root)

        binding.btnContinue.setOnClickListener {
            if (isValid()) {
                val userBoard = UserOnBoardRequest(
                    binding.etFirstName.text.toString(),
                    binding.etLastName.text.toString(),
                    binding.etCompany.text.toString(),
                    binding.etPhone.text.toString()
                )
                onboardUserApi(userBoard)
            }
        }
    }

    private fun isValid(): Boolean {
        if (binding.etFirstName.text.toString().isEmpty()) {
            showToast("First name is required")
            return false
        } else if (binding.etLastName.text.toString().isEmpty()) {
            showToast("last name is required")
            return false
        } else if (binding.etPhone.text.toString().isEmpty()) {
            showToast("Phone number is required")
            return false
        } else if (binding.etCompany.text.toString().isEmpty()) {
            showToast("Company name is required")
            return false
        }
        return true
    }

    private fun onboardUserApi(onBoardObj: UserOnBoardRequest) {
        makeApiRequest(
            apiServiceClass = MyApi::class.java,
            context = this@ActivateAccountActivity,
            showLoading = true,
            requestAction = { apiService ->
                apiService.onBoardUser(onBoardObj)
            },
            listener = object : ApiResponseListener<ApiResponse<OnboardingResponse>> {
                override fun onSuccess(response: ApiResponse<OnboardingResponse>) {
                    if (response.success) {
                        val user = User(
                            response.data.user.id,
                            response.data.user.email,
                            response.data.user.first_name,
                            response.data.user.last_name,
                            response.data.user.is_onboarded,
                            "",
                            "",
                            response.data.access_token,
                            response.data.user.role,
                            response.data.refresh_token
                        )
                        Prefs.saveUser(this@ActivateAccountActivity, user)

                        val intent = Intent(this@ActivateAccountActivity, MainActivity::class.java)
                        startActivity(intent)
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