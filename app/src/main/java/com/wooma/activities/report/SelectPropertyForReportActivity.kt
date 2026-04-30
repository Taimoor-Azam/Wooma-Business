package com.wooma.activities.report

import android.content.Intent
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.wooma.activities.BaseActivity
import com.wooma.adapter.SelectPropertyAdapter
import com.wooma.data.network.showToast
import com.wooma.data.repository.PropertyRepository
import com.wooma.databinding.ActivitySelectPropertyForReportBinding
import com.wooma.model.Property
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch

class SelectPropertyForReportActivity : BaseActivity() {
    private lateinit var adapter: SelectPropertyAdapter
    private val properties = mutableListOf<Property>()
    private val propertyRepo by lazy { PropertyRepository(this) }

    var isFromProperty = false
    private lateinit var binding: ActivitySelectPropertyForReportBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        binding = ActivitySelectPropertyForReportBinding.inflate(layoutInflater)
        setContentView(binding.root)
        applyWindowInsetsToBinding(binding.root)
        isFromProperty = intent.getBooleanExtra("isFromProperty", false)

        adapter = SelectPropertyAdapter(
            this, properties, isFromProperty,
            object : SelectPropertyAdapter.onPropertyClickInterface {
                override fun onPropertyClick(item: Property) {
                    val resultIntent = Intent()
                    resultIntent.putExtra("propertyItem", item)
                    setResult(RESULT_OK, resultIntent)
                    finish()
                }
            })
        binding.rvSelectProperty.adapter = adapter
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                propertyRepo.observeActivePropertiesAsModels().collect { list ->
                    properties.clear()
                    properties.addAll(list)
                    adapter.updateList(properties)
                }
            }
        }

        binding.ivBack.setOnClickListener { finish() }

        binding.searchView.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {
            }

            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                adapter.filter(s.toString())

            }

            override fun afterTextChanged(s: Editable?) {
            }
        })
    }

    override fun onResume() {
        super.onResume()
        if (ConfigureReportActivity.reportCreated) {
            ConfigureReportActivity.reportCreated = false
            val pid = ConfigureReportActivity.createdPropertyId
            ConfigureReportActivity.createdPropertyId = ""
            if (pid.isNotEmpty()) {
                startActivity(
                    Intent(this, ReportListingActivity::class.java)
                        .putExtra("propertyId", pid)
                )
            }
            finish()
        }
        lifecycleScope.launch {
            try { propertyRepo.refreshActiveProperties() } catch (_: Exception) {}
        }
    }

    /*private fun loadProperties() {
         properties.addAll(
             listOf(
                 Property("42 Princes Street, Edinburgh, EH2 2BY"),
                 Property("158 Sauchiehall Street, Glasgow, G2 3EQ"),
                 Property("67 Union Street, Aberdeen, AB11 6BA"),
                 Property("23 George Street, Edinburgh, EH2 2PB"),
                 Property("91 Buchanan Street, Glasgow, G1 3HF"),
                 Property("134 Queen Street, Aberdeen, AB10 1XL"),
                 Property("76 Rose Street, Edinburgh, EH2 3DX"),
                 Property("205 Argyle Street, Glasgow, G2 8DL"),
                 Property("205 Argyle Street, Glasgow, G2 8DL"),
                 Property("205 Argyle Street, Glasgow, G2 8DL"),
                 Property("205 Argyle Street, Glasgow, G2 8DL"),
                 Property("205 Argyle Street, Glasgow, G2 8DL"),
                 Property("205 Argyle Street, Glasgow, G2 8DL"),
                 Property("207888`546546 Argyle Street, Glasgow, G2 8DL"),
                 Property("207888`546546 Argyle Street, Glasgow, G2 8DL"),
                 Property("207889 546546 Argyle Street, Glasgow, G2 8DL"),
             )
         )
        adapter.updateList(properties)
    }*/

}