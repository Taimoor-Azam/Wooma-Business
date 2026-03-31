package com.wooma.business.customs

import android.app.Dialog
import android.content.Context
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.net.Uri
import android.os.Build
import android.util.Patterns
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.ImageView
import android.widget.TextView
import androidx.annotation.RequiresApi
import androidx.recyclerview.widget.RecyclerView
import androidx.viewpager2.widget.ViewPager2
import com.bumptech.glide.Glide
import com.wooma.business.R
import com.wooma.business.model.ImageItem
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
        val dialog = Dialog(context, android.R.style.Theme_Black_NoTitleBar_Fullscreen)
        dialog.setContentView(R.layout.dialog_fullscreen_image)
        dialog.window?.setLayout(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.MATCH_PARENT
        )

        val viewPager = dialog.findViewById<ViewPager2>(R.id.viewPager)
        val tvTitle = dialog.findViewById<TextView>(R.id.tvTitle)
        val tvCounter = dialog.findViewById<TextView>(R.id.tvCounter)
        val ivBack = dialog.findViewById<ImageView>(R.id.ivBack)
        val ivDelete = dialog.findViewById<ImageView>(R.id.ivDelete)

        val mutableImages = images.toMutableList()

        tvTitle.text = title
        ivDelete.visibility = if (onDelete != null) View.VISIBLE else View.GONE

        fun updateCounter(pos: Int) {
            tvCounter.text = "${pos + 1} of ${mutableImages.size}"
        }

        val pagerAdapter = object : RecyclerView.Adapter<RecyclerView.ViewHolder>() {
            override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
                val view = LayoutInflater.from(parent.context)
                    .inflate(R.layout.item_fullscreen_image_page, parent, false)
                return object : RecyclerView.ViewHolder(view) {}
            }

            override fun getItemCount() = mutableImages.size

            override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
                val iv = holder.itemView.findViewById<ImageView>(R.id.ivPage)
                when (val item = mutableImages[position]) {
                    is ImageItem.Remote -> Glide.with(context).load(item.url).into(iv)
                    is ImageItem.Local  -> Glide.with(context).load(item.uri).into(iv)
                }
            }
        }

        viewPager.adapter = pagerAdapter
        viewPager.setCurrentItem(startPosition, false)
        updateCounter(startPosition)

        viewPager.registerOnPageChangeCallback(object : ViewPager2.OnPageChangeCallback() {
            override fun onPageSelected(position: Int) {
                updateCounter(position)
            }
        })

        ivBack.setOnClickListener { dialog.dismiss() }

        ivDelete.setOnClickListener {
            val pos = viewPager.currentItem
            val item = mutableImages[pos]
            onDelete?.invoke(pos, item) {
                mutableImages.removeAt(pos)
                pagerAdapter.notifyItemRemoved(pos)
                if (mutableImages.isEmpty()) {
                    dialog.dismiss()
                } else {
                    val newPos = if (pos >= mutableImages.size) mutableImages.size - 1 else pos
                    viewPager.setCurrentItem(newPos, false)
                    updateCounter(newPos)
                }
            }
        }

        dialog.show()
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