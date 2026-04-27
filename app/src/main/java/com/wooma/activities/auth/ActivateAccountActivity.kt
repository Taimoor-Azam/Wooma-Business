package com.wooma.activities.auth

import android.content.Intent
import android.os.Bundle
import android.util.Log
import com.wooma.activities.BaseActivity
import com.wooma.activities.MainActivity
import com.wooma.data.network.ApiResponseListener
import com.wooma.data.network.MyApi
import com.wooma.data.network.makeApiRequest
import com.wooma.data.network.showToast
import com.wooma.databinding.ActivityActivateAccountBinding
import com.wooma.model.ApiResponse
import com.wooma.model.ErrorResponse
import com.wooma.model.OnboardingResponse
import com.wooma.model.User
import com.wooma.model.UserOnBoardRequest
import com.wooma.storage.Prefs

class ActivateAccountActivity : BaseActivity() {
    private lateinit var binding: ActivityActivateAccountBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        binding = ActivityActivateAccountBinding.inflate(layoutInflater)
        setContentView(binding.root)
        applyWindowInsetsToBinding(binding.root)

        binding.ccp.registerCarrierNumberEditText(binding.etPhone)

        val nameWatcher = object : android.text.TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: android.text.Editable?) {
                if (binding.checkbox.isChecked) {
                    updateCompanyNameFromNames()
                }
            }
        }

        binding.etFirstName.addTextChangedListener(nameWatcher)
        binding.etLastName.addTextChangedListener(nameWatcher)

        binding.checkbox.setOnCheckedChangeListener { _, isChecked ->
            if (isChecked) {
                binding.etCompany.isEnabled = false
                binding.etCompany.alpha = 0.5f
                updateCompanyNameFromNames()
            } else {
                binding.etCompany.isEnabled = true
                binding.etCompany.alpha = 1.0f
                binding.etCompany.setText("")
            }
        }

        binding.btnContinue.setOnClickListener {
            if (isValid()) {
                val fullPhoneNumber = binding.ccp.fullNumberWithPlus
                val userBoard = UserOnBoardRequest(
                    binding.etFirstName.text.toString(),
                    binding.etLastName.text.toString(),
                    binding.etCompany.text.toString(),
                    fullPhoneNumber
                )
                onboardUserApi(userBoard)
            }
        }
    }

    private fun updateCompanyNameFromNames() {
        val firstName = binding.etFirstName.text.toString().trim()
        val lastName = binding.etLastName.text.toString().trim()
        binding.etCompany.setText("$firstName $lastName".trim())
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