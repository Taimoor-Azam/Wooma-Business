package com.wooma.customs

import android.app.Dialog
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.os.Build
import android.util.Patterns
import android.view.ViewGroup
import android.widget.TextView
import androidx.annotation.RequiresApi
import com.wooma.activities.FullscreenImageActivity
import com.wooma.R
import com.wooma.model.ImageItem
import java.text.SimpleDateFormat
import java.time.Instant
import java.time.temporal.ChronoUnit
import java.util.Locale
import java.util.TimeZone

object Utils {

    fun isValidEmail(email: String): Boolean {
        return Patterns.EMAIL_ADDRESS.matcher(email).matches()
    }

    fun formatDate(inputDate: String): String {
        return try {
            val inputFormat = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.getDefault())
            inputFormat.timeZone = TimeZone.getTimeZone("UTC")

            val outputFormat = SimpleDateFormat("dd MMM yy", Locale.getDefault())

            val date = inputFormat.parse(inputDate)
            outputFormat.format(date!!)
        } catch (e: Exception) {
            ""
        }
    }

    fun showReportCompletedDialog(
        context: Context,
        title: String? = null,
        message: String? = null,
        onOkClick: (() -> Unit)? = null
    ) {
        val dialog = Dialog(context)
        dialog.setContentView(R.layout.dialog_report_completed)
        dialog.setCancelable(false)

        if (title != null) dialog.findViewById<TextView>(R.id.tvTitle).text = title
        if (message != null) dialog.findViewById<TextView>(R.id.tvMessage).text = message

        dialog.findViewById<TextView>(R.id.btnOk).setOnClickListener {
            dialog.dismiss()
            onOkClick?.invoke()
        }

        dialog.window?.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
        dialog.window?.setLayout(
            (context.resources.displayMetrics.widthPixels * 0.85).toInt(),
            ViewGroup.LayoutParams.WRAP_CONTENT
        )
        dialog.show()
    }

    fun showDialogBox(
        context: Context,
        title: String,
        message: String,
        onOkClick: (() -> Unit)? = null
    ) {

        val dialog = Dialog(context)
        dialog.setContentView(R.layout.dialog_custom)

        val tvTitle = dialog.findViewById<TextView>(R.id.tvTitle)
        val tvMessage = dialog.findViewById<TextView>(R.id.tvMessage)
        val btnConfirm = dialog.findViewById<TextView>(R.id.btnConfirm)
        val btnCancel = dialog.findViewById<TextView>(R.id.btnCancel)

        dialog.setCancelable(false)

        tvTitle.text = title
        tvMessage.text = message

        btnConfirm.setOnClickListener {
            dialog.dismiss()
            onOkClick?.invoke()
        }
        btnCancel.setOnClickListener {
            dialog.dismiss()
        }

        dialog.window?.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
        dialog.show()
    }

    fun showFullScreenImage(
        context: Context,
        images: List<ImageItem>,
        startPosition: Int = 0,
        title: String = "",
        onDelete: ((position: Int, item: ImageItem, onSuccess: () -> Unit) -> Unit)? = null
    ) {
        FullscreenImageActivity.Companion.pendingImages = images
        FullscreenImageActivity.Companion.pendingOnDelete = onDelete
        val intent = Intent(context, FullscreenImageActivity::class.java)
            .putExtra(FullscreenImageActivity.Companion.EXTRA_START_POSITION, startPosition)
            .putExtra(FullscreenImageActivity.Companion.EXTRA_TITLE, title)
            .putExtra(FullscreenImageActivity.Companion.EXTRA_SHOW_DELETE, onDelete != null)
        context.startActivity(intent)
    }

    @RequiresApi(Build.VERSION_CODES.O)
    fun getDaysDifference(apiDate: String): Long {
        val apiInstant = Instant.parse(apiDate)
        val currentInstant = Instant.now()

        return ChronoUnit.DAYS.between(currentInstant, apiInstant)
    }

    @RequiresApi(Build.VERSION_CODES.O)
    fun addDaysToDate(apiDate: String, days: Long): String {
        val instant = Instant.parse(apiDate)
        val newInstant = instant.plus(days, ChronoUnit.DAYS)
        return newInstant.toString()
    }
}