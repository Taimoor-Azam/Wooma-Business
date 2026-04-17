package com.wooma.business.activities.auth

import android.content.Intent
import android.content.res.ColorStateList
import android.os.Bundle
import android.util.Log
import androidx.core.content.ContextCompat
import androidx.core.widget.addTextChangedListener
import com.wooma.business.R
import com.wooma.business.activities.BaseActivity
import com.wooma.business.customs.Utils
import com.wooma.business.data.network.ApiResponseListener
import com.wooma.business.data.network.MyApi
import com.wooma.business.databinding.ActivityLoginBinding

import com.wooma.business.data.network.makeApiRequest
import com.wooma.business.data.network.showToast
import com.wooma.business.model.ApiResponse
import com.wooma.business.model.ErrorResponse
import com.wooma.business.model.SendOtpData
import com.wooma.business.model.SendOtpRequest
import com.wooma.business.model.SendOtpResponse


class LoginActivity : BaseActivity() {
    private lateinit var binding: ActivityLoginBinding
    var isLogin = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        binding = ActivityLoginBinding.inflate(layoutInflater)
        setContentView(binding.root)
        applyWindowInsetsToBinding(binding.root)

        binding.ivBack.setOnClickListener { finish() }

        isLogin = intent.getBooleanExtra("isLogin", false)

        if (!isLogin) {
            binding.tvTitle.text = getString(R.string.create_account_)
        }

        binding.etEmail.addTextChangedListener { text ->
            val email = text.toString().trim()

            when {
                email.isEmpty() -> {
                    binding.etEmail.error = "email is required"
                    binding.btnSendCode.backgroundTintList = null
                }

                !Utils.isValidEmail(email) -> {
                    binding.etEmail.error = "Invalid Email address"
                    binding.btnSendCode.backgroundTintList = null
                }

                else -> {
                    binding.etEmail.error = null
                    binding.btnSendCode.backgroundTintList =
                        ColorStateList.valueOf(ContextCompat.getColor(this, R.color.green))
                }
            }
        }

        binding.btnSendCode.setOnClickListener {
            if (binding.etEmail.text.toString().isNotEmpty()
                && Utils.isValidEmail(binding.etEmail.text.toString())
            ) {
                val obj =
                    SendOtpRequest(binding.etEmail.text.toString())

                sendOTPApi(obj)
            }
        }
    }

    private fun sendOTPApi(otpObj: SendOtpRequest) {
        makeApiRequest(
            apiServiceClass = MyApi::class.java,
            context = this@LoginActivity,
            showLoading = true,
            requestAction = { apiService ->
                apiService.sendOTP(otpObj)
            },
            listener = object : ApiResponseListener<ApiResponse<SendOtpData>> {
                override fun onSuccess(response: ApiResponse<SendOtpData>) {
                    if (response.success) {
                        startActivity(
                            Intent(this@LoginActivity, OTPActivity::class.java).putExtra(
                                "isLogin",
                                isLogin
                            ).putExtra("email", otpObj.email)
                        )
                    } else {
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