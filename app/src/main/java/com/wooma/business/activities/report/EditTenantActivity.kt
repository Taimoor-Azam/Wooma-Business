package com.wooma.business.activities.report

import android.os.Bundle
import com.wooma.business.activities.BaseActivity
import com.wooma.business.databinding.ActivityEditPropertyBinding
import com.wooma.business.databinding.ActivityEditTenantBinding

class EditTenantActivity : BaseActivity() {
    private lateinit var binding: ActivityEditTenantBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        binding = ActivityEditTenantBinding.inflate(layoutInflater)
        setContentView(binding.root)
        applyWindowInsetsToBinding(binding.root)

        binding.btnResend.setOnClickListener {
        }

        binding.ivBack.setOnClickListener { finish() }

    }
}