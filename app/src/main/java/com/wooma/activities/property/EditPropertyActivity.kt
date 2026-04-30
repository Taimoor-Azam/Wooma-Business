package com.wooma.activities.property

import android.content.Intent
import android.os.Bundle
import android.util.Log
import androidx.activity.OnBackPressedCallback
import androidx.lifecycle.lifecycleScope
import com.wooma.activities.BaseActivity
import com.wooma.activities.MainActivity
import com.wooma.activities.report.ReportListingActivity
import com.wooma.customs.Utils
import com.wooma.data.network.showToast
import com.wooma.data.repository.PropertyRepository
import com.wooma.databinding.ActivityEditPropertyBinding
import com.wooma.model.PropertiesRequest
import com.wooma.sync.SyncScheduler
import kotlinx.coroutines.launch

class EditPropertyActivity : BaseActivity() {
    private lateinit var binding: ActivityEditPropertyBinding
    private val propertyRepo by lazy { PropertyRepository(this) }

    var id = ""
    var address = ""
    var address_line_2 = ""
    var city = ""
    var postcode = ""

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        binding = ActivityEditPropertyBinding.inflate(layoutInflater)
        setContentView(binding.root)
        applyWindowInsetsToBinding(binding.root)

        id = intent.getStringExtra("id") ?: ""
        address = intent.getStringExtra("address") ?: ""
        address_line_2 = intent.getStringExtra("address_line_2") ?: ""
        city = intent.getStringExtra("city") ?: ""
        postcode = intent.getStringExtra("postcode") ?: ""

        binding.btnSave.setOnClickListener {
            if (isValid()) {
                updatePropertyApi()
            }
        }

        binding.ivBack.setOnClickListener { navigateToReportListing() }
        binding.ivReportArchive.setOnClickListener {
            Utils.showDialogBox(
                this@EditPropertyActivity,
                "Archive Property",
                "Are you sure you want to archive ${address}, ${city}, ${postcode} to your archive properties?"
            ) {
                archivePropertyApi(id)
            }
        }
        initView()

        val callback = object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                navigateToReportListing()
                isEnabled = false
                onBackPressedDispatcher.onBackPressed()
            }
        }

        onBackPressedDispatcher.addCallback(this, callback)
    }

    private fun initView() {
        if (address.isNotEmpty()) {
            binding.etAddressOne.setText(address)
        }
        if (address_line_2.isNotEmpty()) {
            binding.etAddressTwo.setText(address_line_2)
        }
        if (city.isNotEmpty()) {
            binding.etCity.setText(city)
        }
        if (postcode.isNotEmpty()) {
            binding.etPostalCode.setText(postcode)
        }
    }


    private fun isValid(): Boolean {
        if (binding.etAddressOne.text.toString().isEmpty()) {
            showToast("Please enter address one")
            return false
        } else if (binding.etCity.text.toString().isEmpty()) {
            showToast("Please enter city name")
            return false
        } else if (binding.etPostalCode.text.toString().isEmpty()) {
            showToast("Please enter Postal Code")
            return false
        }
        return true
    }

    private fun updatePropertyApi() {
        val body = PropertiesRequest(
            binding.etAddressOne.text.toString(),
            binding.etAddressTwo.text.toString(),
            binding.etCity.text.toString(),
            binding.etPostalCode.text.toString(),
            null
        )
        lifecycleScope.launch {
            propertyRepo.updateProperty(id, body)
            SyncScheduler.scheduleImmediateSync(this@EditPropertyActivity)
            showToast("Property updated")
            navigateToReportListing()
        }
    }

    private fun navigateToReportListing() {
        val intent = Intent(this, ReportListingActivity::class.java)
        intent.putExtra("propertyId", id)
        intent.flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
        startActivity(intent)
        finish()
    }

    private fun archivePropertyApi(id: String) {
        lifecycleScope.launch {
            propertyRepo.archiveProperty(id)
            SyncScheduler.scheduleImmediateSync(this@EditPropertyActivity)
            showToast("Archived Successfully")
            val intent = Intent(this@EditPropertyActivity, MainActivity::class.java)
            intent.flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_NEW_TASK
            startActivity(intent)
            finish()
        }
    }


}