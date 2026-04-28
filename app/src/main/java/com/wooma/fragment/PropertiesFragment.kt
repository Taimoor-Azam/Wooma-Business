package com.wooma.fragment

import android.content.Intent
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.wooma.activities.property.AddPropertyByPostalCodeActivity
import com.wooma.activities.property.ArchivePropertiesActivity
import com.wooma.activities.report.SelectPropertyForReportActivity
import com.wooma.adapter.PropertyAdapter
import com.wooma.data.local.mapper.toProperty
import com.wooma.data.repository.PropertyRepository
import com.wooma.databinding.FragmentPropertiesBinding
import com.wooma.model.Property
import kotlinx.coroutines.launch

class PropertiesFragment : Fragment() {

    private lateinit var adapter: PropertyAdapter
    private val properties = mutableListOf<Property>()

    private lateinit var binding: FragmentPropertiesBinding
    private lateinit var repo: PropertyRepository

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        binding = FragmentPropertiesBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        repo = PropertyRepository(requireContext())

        adapter = PropertyAdapter(requireActivity(), properties)
        binding.rvProperties.adapter = adapter

        binding.ivArchive.setOnClickListener {
            startActivity(Intent(requireActivity(), ArchivePropertiesActivity::class.java))
        }

        binding.btnCreateReport.setOnClickListener {
            startActivity(
                Intent(requireActivity(), SelectPropertyForReportActivity::class.java)
                    .putExtra("isFromProperty", true)
            )
        }

        binding.ivAddProperty.setOnClickListener {
            startActivity(Intent(requireActivity(), AddPropertyByPostalCodeActivity::class.java))
        }

        binding.btnAddProperty.setOnClickListener {
            startActivity(Intent(requireActivity(), AddPropertyByPostalCodeActivity::class.java))
        }

        binding.searchView.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                adapter.filter(s.toString())
            }
            override fun afterTextChanged(s: Editable?) {}
        })

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                repo.observeActiveProperties().collect { entities ->
                    val mapped = entities.map { it.toProperty() }
                    properties.clear()
                    properties.addAll(mapped)
                    adapter.updateList(properties)
                    if (properties.isNotEmpty()) {
                        binding.mainLayout.visibility = View.VISIBLE
                        binding.bottomLayout.visibility = View.VISIBLE
                        binding.emptyPropertyLayout.visibility = View.GONE
                    } else {
                        binding.mainLayout.visibility = View.GONE
                        binding.bottomLayout.visibility = View.GONE
                        binding.emptyPropertyLayout.visibility = View.VISIBLE
                    }
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        viewLifecycleOwner.lifecycleScope.launch {
            try {
                repo.refreshActiveProperties()
            } catch (_: Exception) {}
        }
    }
}
