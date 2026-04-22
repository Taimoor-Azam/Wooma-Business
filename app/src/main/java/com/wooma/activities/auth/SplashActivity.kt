package com.wooma.activities.auth

import android.content.Intent
import android.os.Bundle
import android.os.Handler
import com.wooma.activities.BaseActivity
import com.wooma.activities.MainActivity
import com.wooma.databinding.ActivitySplashBinding
import com.wooma.storage.Prefs

class SplashActivity : BaseActivity() {
    private lateinit var binding: ActivitySplashBinding
    private val handler = Handler()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        binding = ActivitySplashBinding.inflate(layoutInflater)
        setContentView(binding.root)
        applyWindowInsetsToBinding(binding.root)

        handler.postDelayed({

            if (Prefs.getUser(this) != null && !Prefs.getUser(this)?.access_token.isNullOrEmpty()) {
                if (Prefs.getUser(this)?.is_onboarded == true)
                    startActivity(Intent(this, MainActivity::class.java))
                else {
                    startActivity(Intent(this, ActivateAccountActivity::class.java))
                }
            } else {
                startActivity(Intent(this, GetStartedActivity::class.java))
            }
            finish()
        }, 2000)
    }
}