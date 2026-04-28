package com.wooma.activities


import android.app.ProgressDialog
import android.content.res.Configuration
import android.graphics.Color
import android.os.Bundle
import android.view.View
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.core.content.ContextCompat
import com.wooma.R
import java.util.Locale

open class BaseActivity : AppCompatActivity() {

    var activeProgressDialog: ProgressDialog? = null

    fun showLoading(message: String = "Loading...") {
        try {
            if (activeProgressDialog?.isShowing == true) activeProgressDialog?.dismiss()
            activeProgressDialog = ProgressDialog(this).apply {
                setMessage(message)
                setCancelable(false)
                show()
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun hideLoading() {
        try {
            if (activeProgressDialog?.isShowing == true) activeProgressDialog?.dismiss()
        } catch (e: Exception) {
            e.printStackTrace()
        } finally {
            activeProgressDialog = null
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setupWindowInsets()
    }

    override fun onDestroy() {
        try {
            if (activeProgressDialog?.isShowing == true) activeProgressDialog?.dismiss()
        } catch (e: Exception) { }
        activeProgressDialog = null
        super.onDestroy()
    }

    override fun setContentView(layoutResID: Int) {
        super.setContentView(layoutResID)
        applyWindowInsets()
    }

    private fun setupWindowInsets() {
        window.statusBarColor = Color.parseColor("#FFC107")
        val isLightMode = (resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_NO
        WindowInsetsControllerCompat(window, window.decorView).apply {
            isAppearanceLightStatusBars = true
            isAppearanceLightNavigationBars = isLightMode
        }
    }

    private fun applyWindowInsets() {
        val rootView = findViewById<View>(android.R.id.content)
        rootView?.let {
            ViewCompat.setOnApplyWindowInsetsListener(it) { v, insets ->
                val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
                v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
                insets
            }
        }
    }

   /* // Call this method after setting up your data binding
    protected fun applyWindowInsetsToBinding(rootView: View) {
        ViewCompat.setOnApplyWindowInsetsListener(rootView) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }
    }*/
   protected fun applyWindowInsetsToBinding(rootView: View) {
       ViewCompat.setOnApplyWindowInsetsListener(rootView) { v, insets ->

           val systemBarsInsets =
               insets.getInsets(WindowInsetsCompat.Type.systemBars())

           val imeInsets =
               insets.getInsets(WindowInsetsCompat.Type.ime())

           // Use IME bottom when keyboard is visible, otherwise system bar bottom
           val bottomInset = if (imeInsets.bottom > 0)
               imeInsets.bottom
           else
               systemBarsInsets.bottom

           v.setPadding(
               systemBarsInsets.left,
               systemBarsInsets.top,
               systemBarsInsets.right,
               bottomInset
           )

           insets
       }
   }

    fun showUnsavedChangesDialog(onDiscard: () -> Unit) {
        val dialog = AlertDialog.Builder(this)
            .setTitle("Unsaved changes")
            .setMessage("You have unsaved changes. Are you sure you want to go back?")
            .setPositiveButton("Discard") { _, _ -> onDiscard() }
            .setNegativeButton("Keep editing", null)
            .show()

        val discardColor = ContextCompat.getColor(this, R.color.red)
        dialog.getButton(AlertDialog.BUTTON_POSITIVE)?.setTextColor(discardColor)
    }

    //check internet connection

    fun String.capitalizeFirstLetter(): String {
        return this.replaceFirstChar {
            if (it.isLowerCase()) it.titlecase(Locale.getDefault())
            else it.toString()
        }
    }
}