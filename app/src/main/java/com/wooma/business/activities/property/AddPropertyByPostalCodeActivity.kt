package com.wooma.business.activities.property

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.util.Log
import android.view.View
import android.view.inputmethod.EditorInfo
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.IntentCompat
import com.wooma.business.activities.BaseActivity
import com.wooma.business.adapter.PostalAddressAdapter
import com.wooma.business.data.network.ApiResponseListener
import com.wooma.business.data.network.MyApi
import com.wooma.business.data.network.makeApiRequest
import com.wooma.business.data.network.showToast
import com.wooma.business.databinding.ActivityAddPropertyByPostalBinding
import com.wooma.business.model.ApiResponse
import com.wooma.business.model.ErrorResponse
import com.wooma.business.model.PostalAddress
import com.wooma.business.model.Property

class AddPropertyByPostalCodeActivity : BaseActivity() {
    private lateinit var binding: ActivityAddPropertyByPostalBinding

    var postalAddress = ArrayList<PostalAddress>()

    private lateinit var adapter: PostalAddressAdapter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        binding = ActivityAddPropertyByPostalBinding.inflate(layoutInflater)
        setContentView(binding.root)
        applyWindowInsetsToBinding(binding.root)

        adapter = PostalAddressAdapter(
            this,
            postalAddress,
            object : PostalAddressAdapter.OnItemClickInterface {
                override fun onItemClick(item: PostalAddress) {
                    val intent = Intent(
                        this@AddPropertyByPostalCodeActivity,
                        AddPropertyActivity::class.java
                    ).putExtra("postalAddress", item)

                    getResult.launch(intent)

                }
            })

        binding.rvArchiveProperties.adapter = adapter

        binding.btnManualAddress.setOnClickListener {
            val intent = Intent(
                this@AddPropertyByPostalCodeActivity,
                AddPropertyActivity::class.java
            )
            // Handle manual address button click
            getResult.launch(intent)
        }

        binding.ivBack.setOnClickListener { finish() }

        /*binding.etPostalCode.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {

            }

            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                getPostCodesApi()
            }

            override fun afterTextChanged(s: Editable?) {
            }
        })*/

        binding.etPostalCode.setOnEditorActionListener { v, actionId, event ->
            if (actionId == EditorInfo.IME_ACTION_SEARCH) {
                // Perform your search operation here
                getPostCodesApi()
                true // Consume the event
            } else {
                false // Event not consumed
            }
        }

    }

    private fun getPostCodesApi() {
        makeApiRequest(
            apiServiceClass = MyApi::class.java,
            context = this,
            showLoading = true,
            requestAction = { apiService -> apiService.getPostCodes(binding.etPostalCode.text.toString()) },
            listener = object : ApiResponseListener<ApiResponse<ArrayList<PostalAddress>>> {
                override fun onSuccess(response: ApiResponse<ArrayList<PostalAddress>>) {
                    if (response.success && response.data.isNotEmpty()) {
                        binding.searchLayout.visibility = View.VISIBLE
                        postalAddress.clear()
                        postalAddress.addAll(response.data)
                        adapter.updateList(postalAddress)
                    } else {
                        binding.searchLayout.visibility = View.GONE
                    }
                }

                override fun onFailure(errorMessage: ErrorResponse?) {
                    // Handle API error
                    Log.e("API", errorMessage?.error?.message ?: "")
                    showToast(errorMessage?.error?.message ?: "")
                }

                override fun onError(throwable: Throwable) {
                    // Handle network error
                    Log.e("API", "Error: ${throwable.message}")
                    showToast("Error: ${throwable.message}")
                }
            }
        )
    }

    private val getResult = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            val data: Intent? = result.data
            val isAdded = data?.getBooleanExtra("propertyAdded", false)
            if (isAdded == true) {
                finish()
            }
        }
    }
}