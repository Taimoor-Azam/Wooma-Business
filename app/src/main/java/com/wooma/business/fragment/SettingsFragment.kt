package com.wooma.business.fragment

import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.TextView
import androidx.fragment.app.Fragment
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.wooma.business.R
import com.wooma.business.activities.auth.GetStartedActivity
import com.wooma.business.customs.Utils
import com.wooma.business.data.network.ApiResponseListener
import com.wooma.business.data.network.MyApi
import com.wooma.business.data.network.makeApiRequest
import com.wooma.business.data.network.showToast
import com.wooma.business.databinding.FragmentSettingsBinding
import com.wooma.business.model.ApiResponse
import com.wooma.business.model.ErrorResponse
import com.wooma.business.model.TenantResponse
import com.wooma.business.storage.Prefs
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone

class SettingsFragment : Fragment() {

    private var _binding: FragmentSettingsBinding? = null
    private val binding get() = _binding!!
    var isOwner = false
    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        return FragmentSettingsBinding.inflate(inflater, container, false)
            .also { _binding = it }
            .root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // App version
        val version = requireContext().packageManager
            .getPackageInfo(requireContext().packageName, 0).versionName
        binding.tvAppVersion.text = "v$version"

        // Logged in as
        val user = Prefs.getUser(requireContext())
        binding.tvLoggedInAs.text = user?.email ?: ""

        val role = user?.role?.lowercase()
        isOwner  = role == "owner"

        if (isOwner) {
            binding.llDeleteAccount.visibility = View.VISIBLE
        }
        loadTenantInfo(showRestore = isOwner)

        // Contact support
        binding.llContactSupport.setOnClickListener {
            val u = Prefs.getUser(requireContext())
            if (u != null) {
                im.crisp.client.external.Crisp.setUserEmail(u.email)
                im.crisp.client.external.Crisp.setUserNickname("${u.first_name} ${u.last_name}".trim())
            }
            startActivity(Intent(requireContext(), im.crisp.client.external.ChatActivity::class.java))
        }

        // Give feedback
        binding.llGiveFeedback.setOnClickListener {
            val intent = Intent(Intent.ACTION_SENDTO).apply {
                data = Uri.parse("mailto:support@wooma.com")
                putExtra(Intent.EXTRA_SUBJECT, "Feedback - Wooma Business")
            }
            startActivity(Intent.createChooser(intent, "Send Feedback"))
        }

        // Rate on App Store
        binding.llRateAppStore.setOnClickListener {
            val pkg = requireContext().packageName
            try {
                startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("market://details?id=$pkg")))
            } catch (e: ActivityNotFoundException) {
                startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://play.google.com/store/apps/details?id=$pkg")))
            }
        }

        // Terms of service
        binding.llTermsOfService.setOnClickListener {
            startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://wooma.com/terms")))
        }

        // Privacy policy
        binding.llPrivacyPolicy.setOnClickListener {
            startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://wooma.com/privacy")))
        }

        // Log out
        binding.llLogout.setOnClickListener {
            Utils.showDialogBox(
                requireContext(),
                "Logout",
                "Are you sure you want to log out?"
            ) {
                Prefs.clearUser(requireContext())
                startActivity(Intent(requireContext(), GetStartedActivity::class.java).apply {
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                })
            }
        }

        // Delete my account
        binding.llDeleteAccount.setOnClickListener {
            showDeleteAccountBottomSheet()
        }

        // Restore account
        binding.btnRestoreAccount.setOnClickListener {
            restoreAccount()
        }
    }

    private fun loadTenantInfo(showRestore: Boolean) {
        requireActivity().makeApiRequest(
            apiServiceClass = MyApi::class.java,
            context = requireActivity(),
            showLoading = false,
            requestAction = { api -> api.getTenantMe() },
            listener = object : ApiResponseListener<ApiResponse<TenantResponse>> {
                override fun onSuccess(response: ApiResponse<TenantResponse>) {
                    if (response.success && _binding != null) {
                        val deleteAfter = response.data?.delete_after
                        if (!deleteAfter.isNullOrEmpty()) {
                            binding.llDeletionScheduled.visibility = View.VISIBLE
                            binding.tvDeleteAfterDate.text = formatDeleteAfterDate(deleteAfter)
                            binding.btnRestoreAccount.visibility = if (showRestore) View.VISIBLE else View.GONE
                            binding.llDeleteAccount.visibility = View.GONE
                        } else {
                            binding.llDeletionScheduled.visibility = View.GONE
                        }
                    }
                }

                override fun onFailure(errorMessage: ErrorResponse?) {}
                override fun onError(throwable: Throwable) {}
            }
        )
    }

    private fun formatDeleteAfterDate(isoDate: String): String {
        return try {
            val inputFmt = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.getDefault())
            inputFmt.timeZone = TimeZone.getTimeZone("UTC")
            val date = inputFmt.parse(isoDate) ?: return isoDate
            val outputFmt = SimpleDateFormat("dd MMM yyyy 'at' h:mm a", Locale.getDefault())
            outputFmt.format(date)
        } catch (e: Exception) {
            isoDate
        }
    }

    private fun showDeleteAccountBottomSheet() {
        val bottomSheet = BottomSheetDialog(requireContext())
        val sheetView = layoutInflater.inflate(R.layout.bottom_sheet_delete_account, null)
        bottomSheet.setContentView(sheetView)

        val ivClose = sheetView.findViewById<View>(R.id.ivClose)
        val etConfirm = sheetView.findViewById<EditText>(R.id.etConfirm)
        val btnCancel = sheetView.findViewById<TextView>(R.id.btnCancel)
        val btnDelete = sheetView.findViewById<TextView>(R.id.btnDelete)

        btnDelete.alpha = 0.5f
        btnDelete.isClickable = false

        etConfirm.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                val matches = s?.toString()?.trim() == "delete my account"
                btnDelete.alpha = if (matches) 1f else 0.5f
                btnDelete.isClickable = matches
            }
            override fun afterTextChanged(s: Editable?) {}
        })

        ivClose.setOnClickListener { bottomSheet.dismiss() }
        btnCancel.setOnClickListener { bottomSheet.dismiss() }

        btnDelete.setOnClickListener {
            bottomSheet.dismiss()
            scheduleDeletion()
        }

        bottomSheet.show()
    }

    private fun scheduleDeletion() {
        requireActivity().makeApiRequest(
            apiServiceClass = MyApi::class.java,
            context = requireActivity(),
            showLoading = true,
            requestAction = { api -> api.scheduleTenantDeletion() },
            listener = object : ApiResponseListener<ApiResponse<TenantResponse>> {
                override fun onSuccess(response: ApiResponse<TenantResponse>) {
                    if (response.success && _binding != null) {
                        loadTenantInfo(showRestore = isOwner)

                        /* val deleteAfter = response.data?.delete_after
                        if (!deleteAfter.isNullOrEmpty()) {
                            binding.llDeletionScheduled.visibility = View.VISIBLE
                            binding.tvDeleteAfterDate.text = formatDeleteAfterDate(deleteAfter)
                        }*/
                        requireContext().showToast("Account deletion scheduled")
                    }
                }

                override fun onFailure(errorMessage: ErrorResponse?) {
                    requireContext().showToast(errorMessage?.error?.message ?: "Failed to schedule deletion")
                }

                override fun onError(throwable: Throwable) {
                    requireContext().showToast("Error: ${throwable.message}")
                }
            }
        )
    }

    private fun restoreAccount() {
        requireActivity().makeApiRequest(
            apiServiceClass = MyApi::class.java,
            context = requireActivity(),
            showLoading = true,
            requestAction = { api -> api.restoreTenantDeletion() },
            listener = object : ApiResponseListener<ApiResponse<TenantResponse>> {
                override fun onSuccess(response: ApiResponse<TenantResponse>) {
                    if (response.success && _binding != null) {
                        binding.llDeletionScheduled.visibility = View.GONE
                        binding.llDeleteAccount.visibility = View.VISIBLE
                        loadTenantInfo(showRestore = isOwner)

                        requireContext().showToast("Account restored successfully")
                    }
                }

                override fun onFailure(errorMessage: ErrorResponse?) {
                    requireContext().showToast(errorMessage?.error?.message ?: "Failed to restore account")
                }

                override fun onError(throwable: Throwable) {
                    requireContext().showToast("Error: ${throwable.message}")
                }
            }
        )
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
