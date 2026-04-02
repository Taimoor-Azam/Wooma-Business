package com.wooma.business.activities


import android.app.ProgressDialog
import android.graphics.Color
import android.os.Bundle
import android.util.TypedValue
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.wooma.business.R
import java.util.Locale

open class BaseActivity : AppCompatActivity() {

    var activeProgressDialog: ProgressDialog? = null

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
        val window = window

        // Try multiple ways to get your yellow color
        val typedValue = TypedValue()
        var statusBarColor = Color.WHITE // fallback

        // Try colorPrimary first
        statusBarColor = Color.parseColor("#FFC107") // Your yellow color
           /* if (theme.resolveAttribute(androidx.appcompat.R.attr.colorPrimary, typedValue, true)) {
                typedValue.data
            }
            // Try colorPrimaryVariant
            else if (theme.resolveAttribute(R.attr.colorPrimaryVariant, typedValue, true)) {
                typedValue.data
            }
            // Or set your yellow color directly
            else {
                Color.parseColor("#FFC107") // Your yellow color
            }*/

        window.statusBarColor = statusBarColor

        // Set light status bar for dark icons on yellow background
        window.decorView.systemUiVisibility = View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR
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
        androidx.appcompat.app.AlertDialog.Builder(this)
            .setTitle("Unsaved changes")
            .setMessage("You have unsaved changes. Are you sure you want to go back?")
            .setPositiveButton("Discard") { _, _ -> onDiscard() }
            .setNegativeButton("Keep editing", null)
            .show()
    }

    //check internet connection

    fun String.capitalizeFirstLetter(): String {
        return this.replaceFirstChar {
            if (it.isLowerCase()) it.titlecase(Locale.getDefault())
            else it.toString()
        }
    }
}