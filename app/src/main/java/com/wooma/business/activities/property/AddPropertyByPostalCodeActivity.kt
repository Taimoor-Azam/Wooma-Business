package com.wooma.business.activities.property

import android.content.Intent
import android.os.Bundle
import com.wooma.business.activities.BaseActivity
import com.wooma.business.databinding.ActivityAddPropertyBinding
import com.wooma.business.databinding.ActivityAddPropertyByPostalBinding
import com.wooma.business.databinding.ActivityEditPropertyBinding

class AddPropertyByPostalCodeActivity : BaseActivity() {
    private lateinit var binding: ActivityAddPropertyByPostalBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        binding = ActivityAddPropertyByPostalBinding.inflate(layoutInflater)
        setContentView(binding.root)
        applyWindowInsetsToBinding(binding.root)

        binding.btnManualAddress.setOnClickListener {
            // Handle manual address button click
            startActivity(Intent(this, AddPropertyActivity::class.java))
        }

        binding.ivBack.setOnClickListener { finish() }

    }
}