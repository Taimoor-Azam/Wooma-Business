package com.wooma.business.fragment

import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import com.wooma.business.activities.auth.GetStartedActivity
import com.wooma.business.databinding.FragmentSettingsBinding
import com.wooma.business.storage.Prefs

class SettingsFragment : Fragment() {

    private var _binding: FragmentSettingsBinding? = null
    private val binding get() = _binding!!

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

        // Contact support
        binding.llContactSupport.setOnClickListener {
            startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://wooma.com/support")))
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
            Prefs.clearUser(requireContext())
            startActivity(Intent(requireContext(), GetStartedActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            })
        }

        // Delete my account
        binding.llDeleteAccount.setOnClickListener {
            android.app.AlertDialog.Builder(requireContext())
                .setTitle("Delete Account")
                .setMessage("Are you sure you want to delete your account? This action cannot be undone.")
                .setPositiveButton("Delete") { _, _ ->
                    Prefs.clearUser(requireContext())
                    startActivity(Intent(requireContext(), GetStartedActivity::class.java).apply {
                        flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                    })
                }
                .setNegativeButton("Cancel", null)
                .show()
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
