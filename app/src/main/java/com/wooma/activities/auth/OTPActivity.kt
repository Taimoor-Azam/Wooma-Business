package com.wooma.activities.auth

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
import androidx.lifecycle.lifecycleScope
import com.wooma.activities.BaseActivity
import com.wooma.activities.MainActivity
import com.wooma.R
import com.wooma.data.network.ApiResponseListener
import com.wooma.data.network.MyApi
import com.wooma.data.network.makeApiRequest
import com.wooma.data.network.showToast
import com.wooma.databinding.ActivityOtpBinding
import com.wooma.model.ApiResponse
import com.wooma.model.ErrorResponse
import com.wooma.model.SendOtpData
import com.wooma.model.SendOtpRequest
import com.wooma.model.User
import com.wooma.model.VerifyOTPRequest
import com.wooma.model.VerifyOtpData
import com.wooma.data.repository.ConfigRepository
import com.wooma.storage.Prefs
import kotlinx.coroutines.launch

class OTPActivity : BaseActivity() {
    private lateinit var binding: ActivityOtpBinding
    var email: String = ""
    private val configRepo by lazy { ConfigRepository(this) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        binding = ActivityOtpBinding.inflate(layoutInflater)
        setContentView(binding.root)
        applyWindowInsetsToBinding(binding.root)

        email = intent.getStringExtra("email") ?: ""

        if (email.isNotEmpty()) {
            binding.tvEmailLabel.text = "Your code was sent to " + email
        }

        startResendOtpTimer(this)

        binding.btnResend.setOnClickListener {
            startResendOtpTimer(this)
            val obj = SendOtpRequest(email)

            sendOTPApi(obj)
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

        binding.etOTPOne.postDelayed({
            binding.etOTPOne.requestFocus()
            val imm = getSystemService(Context.INPUT_METHOD_SERVICE) as android.view.inputmethod.InputMethodManager
            imm.showSoftInput(binding.etOTPOne, android.view.inputmethod.InputMethodManager.SHOW_IMPLICIT)
        }, 200)
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
                        val user = User(
                            response.data.user.id,
                            response.data.user.email,
                            response.data.user.firstName,
                            response.data.user.lastName,
                            response.data.user.isOnboarded,
                            "",
                            "",
                            response.data.accessToken,
                            response.data.user.role,
                            response.data.refreshToken
                        )
                        Prefs.saveUser(this@OTPActivity, user)
                        // Seed reference data in background — fire and forget
                        lifecycleScope.launch {
                            try { configRepo.seedReferenceData() } catch (_: Exception) {}
                        }

                        if (!response.data.user.isOnboarded) {
                            val intent =
                                Intent(this@OTPActivity, ActivateAccountActivity::class.java)
                            intent.flags =
                                Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                            startActivity(intent)
                            finish()
                        } else {

                            val intent = Intent(this@OTPActivity, MainActivity::class.java)
                            intent.flags =
                                Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
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

    private fun sendOTPApi(otpObj: SendOtpRequest) {
        makeApiRequest(
            apiServiceClass = MyApi::class.java,
            context = this@OTPActivity,
            showLoading = true,
            requestAction = { apiService ->
                apiService.sendOTP(otpObj)
            },
            listener = object : ApiResponseListener<ApiResponse<SendOtpData>> {
                override fun onSuccess(response: ApiResponse<SendOtpData>) {
                    if (response.success) {

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