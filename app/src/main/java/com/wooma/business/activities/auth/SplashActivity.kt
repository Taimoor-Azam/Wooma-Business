package com.wooma.business.activities.auth

import android.content.Intent
import android.os.Bundle
import android.os.Handler
import com.wooma.business.activities.BaseActivity
import com.wooma.business.activities.MainActivity
import com.wooma.business.databinding.ActivitySplashBinding
import com.wooma.business.storage.Prefs

class SplashActivity : BaseActivity() {
    private lateinit var binding: ActivitySplashBinding
    private val handler = Handler()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        binding = ActivitySplashBinding.inflate(layoutInflater)
        setContentView(binding.root)
        applyWindowInsetsToBinding(binding.root)

        handler.postDelayed(kotlinx.coroutines.Runnable {

            if (Prefs.getUser(this) != null && !Prefs.getUser(this)?.access_token.isNullOrEmpty()) {
                startActivity(Intent(this, MainActivity::class.java))
            } else {
                startActivity(Intent(this, GetStartedActivity::class.java))
            }
            finish()
        }, 2000)
    }
}