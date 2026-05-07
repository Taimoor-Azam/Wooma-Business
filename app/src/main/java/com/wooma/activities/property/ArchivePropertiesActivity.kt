package com.wooma.activities.property

import android.os.Bundle
import android.view.View
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.wooma.activities.BaseActivity
import com.wooma.adapter.ArchivePropertyAdapter
import com.wooma.customs.Utils
import com.wooma.data.local.mapper.toProperty
import com.wooma.data.network.showToast
import com.wooma.data.repository.PropertyRepository
import com.wooma.databinding.ActivityArchivePropertiesListingBinding
import com.wooma.model.Property
import com.wooma.sync.SyncScheduler
import kotlinx.coroutines.launch

class ArchivePropertiesActivity : BaseActivity() {
    private lateinit var binding: ActivityArchivePropertiesListingBinding

    private lateinit var propertyRepo: PropertyRepository
    private lateinit var adapter: ArchivePropertyAdapter
    private val properties = mutableListOf<Property>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        binding = ActivityArchivePropertiesListingBinding.inflate(layoutInflater)
        setContentView(binding.root)
        applyWindowInsetsToBinding(binding.root)

        propertyRepo = PropertyRepository(this)

        adapter = ArchivePropertyAdapter(
            this,
            properties,
            object : ArchivePropertyAdapter.OnItemClickInterface {
                override fun onItemClick(item: Property) {
                    Utils.showDialogBox(
                        this@ArchivePropertiesActivity,
                        "Restore Property",
                        "Are you sure you want to restore ${item.address}, ${item.city}, ${item.postcode} to your active properties?"
                    ) {
                        restoreProperty(item.id ?: "")
                    }
                }
            }
        )
        binding.rvArchiveProperties.adapter = adapter

        binding.ivBack.setOnClickListener { finish() }

        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                propertyRepo.observeArchivedProperties().collect { entities ->
                    val mapped = entities.map { it.toProperty() }
                    properties.clear()
                    properties.addAll(mapped)
                    adapter.updateList(properties)
                    if (properties.isEmpty()) {
                        binding.tvNoArchive.visibility = View.VISIBLE
                        binding.rvArchiveProperties.visibility = View.GONE
                    } else {
                        binding.tvNoArchive.visibility = View.GONE
                        binding.rvArchiveProperties.visibility = View.VISIBLE
                    }
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        lifecycleScope.launch {
            try {
                propertyRepo.refreshArchivedProperties()
            } catch (_: Exception) {}
        }
    }

    private fun restoreProperty(id: String) {
        lifecycleScope.launch {
            try {
                propertyRepo.restoreProperty(id)
                SyncScheduler.scheduleImmediateSync(this@ArchivePropertiesActivity)
            } catch (e: Exception) {
                showToast("Failed to restore: ${e.message}")
            }
        }
    }
}
