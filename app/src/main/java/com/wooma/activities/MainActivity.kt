package com.wooma.activities

import android.content.Intent
import android.os.Bundle
import android.view.View
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.fragment.app.Fragment
import com.wooma.R
import com.wooma.databinding.ActivityMainBinding
import com.wooma.fragment.MessagesFragment
import com.wooma.fragment.PropertiesFragment
import com.wooma.fragment.SettingsFragment
import com.wooma.storage.Prefs
import im.crisp.client.external.ChatActivity
import im.crisp.client.external.Crisp

class MainActivity : BaseActivity() {

    private lateinit var binding: ActivityMainBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)
//        WindowCompat.setDecorFitsSystemWindows(window, true)
        applyWindowInsetsToBindingView(binding.root)

        if (savedInstanceState == null) {
            supportFragmentManager.beginTransaction()
                .replace(binding.fragmentContainer.id, PropertiesFragment())
                .commit()
        }

        binding.bottomNavigation.setOnItemSelectedListener {
            when (it.itemId) {
                R.id.nav_home -> loadFragment(PropertiesFragment())
                R.id.nav_settings -> loadFragment(SettingsFragment())
                R.id.nav_support -> /*openSupportChat()*/loadFragment(MessagesFragment())
            }
            true
        }

    }

    fun openSupportChat() {
        val user = Prefs.getUser(this)
        if (user != null) {
            Crisp.setUserEmail(user.email)
            Crisp.setUserNickname("${user.first_name} ${user.last_name}".trim())
        }

        startActivity(Intent(this, ChatActivity::class.java))
    }

    private fun loadFragment(fragment: Fragment) {
        supportFragmentManager.beginTransaction()
            .replace(R.id.fragmentContainer, fragment)
            .commit()
    }

    private fun applyWindowInsetsToBindingView(rootView: View) {
        ViewCompat.setOnApplyWindowInsetsListener(rootView) { v, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(bars.left, bars.top, bars.right, 0)

            val cardParams = binding.carView.layoutParams as ConstraintLayout.LayoutParams
            cardParams.bottomMargin = bars.bottom + resources.getDimensionPixelSize(com.intuit.sdp.R.dimen._15sdp)
            binding.carView.layoutParams = cardParams

            WindowInsetsCompat.CONSUMED
        }
    }
}
