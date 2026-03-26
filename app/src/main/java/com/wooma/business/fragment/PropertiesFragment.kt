package com.wooma.business.fragment

import android.content.Intent
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import com.wooma.business.activities.property.AddPropertyByPostalCodeActivity
import com.wooma.business.activities.property.ArchivePropertiesActivity
import com.wooma.business.activities.report.SelectPropertyForReportActivity
import com.wooma.business.adapter.PropertyAdapter
import com.wooma.business.data.network.ApiResponseListener
import com.wooma.business.data.network.MyApi
import com.wooma.business.data.network.makeApiRequest
import com.wooma.business.data.network.showToast
import com.wooma.business.databinding.FragmentPropertiesBinding
import com.wooma.business.model.ApiResponse
import com.wooma.business.model.ErrorResponse
import com.wooma.business.model.Property
import com.wooma.business.model.TenantPropertiesWrapper

class PropertiesFragment : Fragment() {

    private lateinit var adapter: PropertyAdapter
    private val properties = mutableListOf<Property>()

    private lateinit var binding: FragmentPropertiesBinding

    private var page: Int = 1

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

        adapter = PropertyAdapter(requireActivity(), properties)
        binding.rvProperties.adapter = adapter

        binding.ivArchive.setOnClickListener {
            startActivity(Intent(requireActivity(), ArchivePropertiesActivity::class.java))
        }

        binding.btnCreateReport.setOnClickListener {
            startActivity(Intent(requireActivity(), SelectPropertyForReportActivity::class.java).putExtra("isFromProperty",true))
        }

        binding.ivAddProperty.setOnClickListener {
            startActivity(Intent(requireActivity(), AddPropertyByPostalCodeActivity::class.java))
        }

        binding.searchView.addTextChangedListener(object : TextWatcher{
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
        getPropertiesList()
    }

    private fun getPropertiesList() {
        val queryMap = mutableMapOf<String, Any>().apply {
            put("page", page)
            put("limit", 50)
            put("search", binding.searchView.text.toString())
            put("is_active", true)
        }

        requireActivity().makeApiRequest(
            apiServiceClass = MyApi::class.java,
            context = requireActivity(),
            showLoading = true,
            requestAction = { apiService -> apiService.getPropertiesList(queryMap) },
            listener = object : ApiResponseListener<ApiResponse<TenantPropertiesWrapper>> {
                override fun onSuccess(response: ApiResponse<TenantPropertiesWrapper>) {
                    if (response.data.data.isNotEmpty()) {
                        properties.clear()
                        properties.addAll(response.data.data)
                        adapter.updateList(properties)
                    }
                }

                override fun onFailure(errorMessage: ErrorResponse?) {
                    // Handle API error
                    Log.e("API", errorMessage?.error?.message ?: "")
                    requireActivity().showToast(errorMessage?.error?.message ?: "")
                }

                override fun onError(throwable: Throwable) {
                    // Handle network error
                    Log.e("API", "Error: ${throwable.message}")
                    requireActivity().showToast("Error: ${throwable.message}")
                }
            }
        )
    }
}