package com.wooma.business.activities

import android.content.Intent
import android.os.Bundle
import android.view.View
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.fragment.app.Fragment
import com.wooma.business.R
import com.wooma.business.databinding.ActivityMainBinding
import com.wooma.business.fragment.MessagesFragment
import com.wooma.business.fragment.PropertiesFragment
import com.wooma.business.fragment.SettingsFragment
import com.wooma.business.storage.Prefs
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

        ViewCompat.setOnApplyWindowInsetsListener(binding.bottomNavigation) { view, insets ->
            val systemBarsInsets =
                insets.getInsets(WindowInsetsCompat.Type.systemBars())

            view.setPadding(
                view.paddingLeft,
                view.paddingTop,
                view.paddingRight,
                systemBarsInsets.bottom
            )

            insets
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

            val systemBarsInsets =
                insets.getInsets(WindowInsetsCompat.Type.systemBars())

            v.setPadding(
                systemBarsInsets.left,
                systemBarsInsets.top,
                systemBarsInsets.right,
                0
            )

            insets
        }
    }
}
