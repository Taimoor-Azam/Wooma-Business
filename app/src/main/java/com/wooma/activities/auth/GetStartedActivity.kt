package com.wooma.activities.auth

import android.content.Intent
import android.content.res.ColorStateList
import android.os.Bundle
import android.util.Log
import androidx.core.content.ContextCompat
import androidx.core.widget.addTextChangedListener
import com.wooma.activities.BaseActivity
import com.wooma.R
import com.wooma.customs.Utils
import com.wooma.data.network.ApiResponseListener
import com.wooma.data.network.MyApi
import com.wooma.data.network.makeApiRequest
import com.wooma.data.network.showToast
import com.wooma.databinding.ActivityGetStartedBinding
import com.wooma.model.ApiResponse
import com.wooma.model.ErrorResponse
import com.wooma.model.SendOtpData
import com.wooma.model.SendOtpRequest

class GetStartedActivity : BaseActivity() {
    private lateinit var binding: ActivityGetStartedBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        binding = ActivityGetStartedBinding.inflate(layoutInflater)
        setContentView(binding.root)
        applyWindowInsetsToBinding(binding.root)

        binding.createAccountLayout.setOnClickListener {
            checkEmail()
//            startActivity(Intent(this, LoginActivity::class.java).putExtra("isLogin", false))
        }
        binding.btnCreate.setOnClickListener {
            checkEmail()
//            startActivity(Intent(this, LoginActivity::class.java).putExtra("isLogin", false))
        }

        binding.btnLogin.setOnClickListener {
            startActivity(Intent(this, LoginActivity::class.java).putExtra("isLogin", true))
        }

        binding.etEmail.addTextChangedListener { text ->
            val email = text.toString().trim()

            when {
                email.isEmpty() -> {
                    binding.etEmail.error = "email is required"
                    binding.createAccountLayout.backgroundTintList = null
                }

                !Utils.isValidEmail(email) -> {
                    binding.etEmail.error = "Invalid Email address"
                    binding.createAccountLayout.backgroundTintList = null
                }

                else -> {
                    binding.etEmail.error = null
                    binding.createAccountLayout.backgroundTintList =
                        ColorStateList.valueOf(ContextCompat.getColor(this, R.color.green))
                }
            }
        }

    }

    fun checkEmail() {
        if (binding.etEmail.text.toString().isNotEmpty()
            && Utils.isValidEmail(binding.etEmail.text.toString())
        ) {
            val obj = SendOtpRequest(binding.etEmail.text.toString())
            sendOTPApi(obj)
        } else {
            showToast("Please enter valid email")
        }
    }

    private fun sendOTPApi(otpObj: SendOtpRequest) {
        makeApiRequest(
            apiServiceClass = MyApi::class.java,
            context = this@GetStartedActivity,
            showLoading = true,
            requestAction = { apiService ->
                apiService.sendOTP(otpObj)
            },
            listener = object : ApiResponseListener<ApiResponse<SendOtpData>> {
                override fun onSuccess(response: ApiResponse<SendOtpData>) {
                    if (response.success) {
                        startActivity(
                            Intent(
                                this@GetStartedActivity,
                                OTPActivity::class.java
                            ).putExtra("email", otpObj.email)
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
}