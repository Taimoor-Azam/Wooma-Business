package com.wooma.business.activities.report.inventorysettings

import android.os.Bundle
import com.wooma.business.activities.BaseActivity
import com.wooma.business.databinding.ActivityChangeReportDateBinding

class ChangeReportDateActivity : BaseActivity() {
    private lateinit var binding: ActivityChangeReportDateBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        binding = ActivityChangeReportDateBinding.inflate(layoutInflater)
        setContentView(binding.root)
        applyWindowInsetsToBinding(binding.root)

        binding.ivBack.setOnClickListener { finish() }
    }
}