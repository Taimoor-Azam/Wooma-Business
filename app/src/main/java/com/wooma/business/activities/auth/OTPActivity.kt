package com.wooma.business.activities.auth

import android.content.Context
import android.content.Intent
import android.content.res.ColorStateList
import android.os.Bundle
import android.os.CountDownTimer
import android.text.Editable
import android.text.TextWatcher
import android.util.Log
import android.view.KeyEvent
import android.widget.EditText
import androidx.core.content.ContextCompat
import com.wooma.business.R
import com.wooma.business.activities.BaseActivity
import com.wooma.business.activities.MainActivity
import com.wooma.business.data.network.ApiResponseListener
import com.wooma.business.data.network.MyApi
import com.wooma.business.data.network.makeApiRequest
import com.wooma.business.data.network.showToast
import com.wooma.business.databinding.ActivityOtpBinding
import com.wooma.business.model.ApiResponse
import com.wooma.business.model.ErrorResponse
import com.wooma.business.model.User
import com.wooma.business.model.VerifyOTPRequest
import com.wooma.business.model.VerifyOtpData
import com.wooma.business.storage.Prefs

class OTPActivity : BaseActivity() {
    private lateinit var binding: ActivityOtpBinding
    var isLogin = false
    var email: String = ""

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        binding = ActivityOtpBinding.inflate(layoutInflater)
        setContentView(binding.root)
        applyWindowInsetsToBinding(binding.root)

        isLogin = intent.getBooleanExtra("isLogin", false)
        email = intent.getStringExtra("email") ?: ""

        if (email.isNotEmpty()) {
            binding.tvEmailLabel.text = "Your code was sent to " + email
        }

        startResendOtpTimer(this)

        binding.btnResend.setOnClickListener {
            startResendOtpTimer(this)
        }

        binding.ivBack.setOnClickListener { finish() }

        val otpFields = listOf(
            binding.etOTPOne,
            binding.etOTPTwo,
            binding.etOTPThree,
            binding.etOTPFour,
            binding.etOTPFive,
            binding.etOTPSix
        )

        setupOtpInputs(otpFields) { otp ->
            // All OTP fields are filled
            // Enable button / verify OTP
            val obj = VerifyOTPRequest(email, otp)
            verifyOTPApi(obj)
        }
    }

    /*check the input of the OTP*/
    fun setupOtpInputs(
        otpFields: List<EditText>,
        onOtpComplete: (String) -> Unit
    ) {
        otpFields.forEachIndexed { index, editText ->

            editText.addTextChangedListener(object : TextWatcher {

                override fun beforeTextChanged(
                    s: CharSequence?, start: Int, count: Int, after: Int
                ) {
                }

                override fun onTextChanged(
                    s: CharSequence?, start: Int, before: Int, count: Int
                ) {
                    if (s?.length == 1 && index < otpFields.lastIndex) {
                        otpFields[index + 1].requestFocus()
                    }
                }

                override fun afterTextChanged(s: Editable?) {
                    val otp = otpFields.joinToString("") { it.text.toString() }

                    if (otp.length == otpFields.size) {
                        onOtpComplete(otp)
                    }
                }
            })
            // Handle backspace
            editText.setOnKeyListener { _, keyCode, event ->
                if (event.action == KeyEvent.ACTION_DOWN &&
                    keyCode == KeyEvent.KEYCODE_DEL &&
                    editText.text.isEmpty() &&
                    index > 0
                ) {
                    otpFields[index - 1].requestFocus()
                    true
                } else {
                    false
                }
            }
        }
    }

    /*resend OTP timer*/
    private var countDownTimer: CountDownTimer? = null

    private fun startResendOtpTimer(context: Context) {
        binding.btnResend.backgroundTintList = null
        binding.btnResend.isEnabled = false

        countDownTimer?.cancel()

        countDownTimer = object : CountDownTimer(60_000, 1_000) {

            override fun onTick(millisUntilFinished: Long) {
                val seconds = millisUntilFinished / 1000
                binding.btnResend.text = "Resend in ${seconds}s"
            }

            override fun onFinish() {
                binding.btnResend.text = "Resend OTP"
                binding.btnResend.isEnabled = true
                binding.btnResend.backgroundTintList =
                    ColorStateList.valueOf(
                        ContextCompat.getColor(context, R.color.green)
                    )
            }
        }.start()
    }

    private fun verifyOTPApi(otpObj: VerifyOTPRequest) {
        makeApiRequest(
            apiServiceClass = MyApi::class.java,
            context = this@OTPActivity,
            showLoading = true,
            requestAction = { apiService ->
                apiService.verifyOTP(otpObj)
            },
            listener = object : ApiResponseListener<ApiResponse<VerifyOtpData>> {
                override fun onSuccess(response: ApiResponse<VerifyOtpData>) {
                    if (response.success) {
                        if (!response.data.user.isOnboarded) {
                            val intent =
                                Intent(this@OTPActivity, ActivateAccountActivity::class.java)
                            startActivity(intent)
                            finish()
                        } else {
                            val user = User(
                                response.data.user.id,
                                response.data.user.email,
                                response.data.user.firstName,
                                response.data.user.lastName,
                                response.data.user.isOnboarded,
                                "",
                                "",
                                response.data.accessToken,
                                response.data.refreshToken
                            )
                            Prefs.saveUser(this@OTPActivity, user)
                            val intent = Intent(this@OTPActivity, MainActivity::class.java)
                            startActivity(intent)
                            finish()
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