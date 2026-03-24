package com.wooma.business.activities.auth

import android.content.Intent
import android.os.Bundle
import com.wooma.business.activities.BaseActivity
import com.wooma.business.databinding.ActivityGetStartedBinding

class GetStartedActivity : BaseActivity() {
    private lateinit var binding: ActivityGetStartedBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        binding = ActivityGetStartedBinding.inflate(layoutInflater)
        setContentView(binding.root)
        applyWindowInsetsToBinding(binding.root)

        binding.createAccountLayout.setOnClickListener {
            startActivity(Intent(this, LoginActivity::class.java).putExtra("isLogin", false))
        }
        binding.btnCreate.setOnClickListener {
            startActivity(Intent(this, LoginActivity::class.java).putExtra("isLogin", false))
        }

        binding.btnLogin.setOnClickListener {
            startActivity(Intent(this, LoginActivity::class.java).putExtra("isLogin", true))

        }

    }
}