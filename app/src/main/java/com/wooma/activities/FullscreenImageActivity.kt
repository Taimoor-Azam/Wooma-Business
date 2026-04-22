package com.wooma.activities

import android.graphics.Color
import android.os.Bundle
import android.graphics.drawable.Drawable
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.ProgressBar
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.recyclerview.widget.RecyclerView
import androidx.viewpager2.widget.ViewPager2
import com.bumptech.glide.Glide
import com.bumptech.glide.load.DataSource
import com.bumptech.glide.load.engine.GlideException
import com.bumptech.glide.request.RequestListener
import com.bumptech.glide.request.target.Target
import com.wooma.R
import com.wooma.customs.Utils
import com.wooma.databinding.ActivityFullscreenImageBinding
import com.wooma.model.ImageItem

class FullscreenImageActivity : BaseActivity() {

    private lateinit var binding: ActivityFullscreenImageBinding

    companion object {
        var pendingImages: List<ImageItem> = emptyList()
        var pendingOnDelete: ((position: Int, item: ImageItem, onSuccess: () -> Unit) -> Unit)? = null
        const val EXTRA_START_POSITION = "startPosition"
        const val EXTRA_TITLE = "title"
        const val EXTRA_SHOW_DELETE = "showDelete"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityFullscreenImageBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // Override BaseActivity amber status bar — go transparent/black for fullscreen
        window.statusBarColor = Color.TRANSPARENT
        window.navigationBarColor = Color.TRANSPARENT
        WindowInsetsControllerCompat(window, window.decorView).apply {
            isAppearanceLightStatusBars = false
            isAppearanceLightNavigationBars = false
        }

        // Pad topBar by status bar height so content clears the notch
        ViewCompat.setOnApplyWindowInsetsListener(binding.topBar) { v, insets ->
            val statusBarHeight = insets.getInsets(WindowInsetsCompat.Type.statusBars()).top
            v.setPadding(v.paddingLeft, statusBarHeight + resources.getDimensionPixelSize(com.intuit.sdp.R.dimen._12sdp), v.paddingRight, v.paddingBottom)
            insets
        }

        val startPosition = intent.getIntExtra(EXTRA_START_POSITION, 0)
        val title = intent.getStringExtra(EXTRA_TITLE) ?: ""
        val showDelete = intent.getBooleanExtra(EXTRA_SHOW_DELETE, false)

        val mutableImages = pendingImages.toMutableList()

        binding.tvTitle.text = title
        binding.ivDelete.visibility = if (showDelete && pendingOnDelete != null) View.VISIBLE else View.GONE

        fun updateCounter(pos: Int) {
            binding.tvCounter.text = "${pos + 1} of ${mutableImages.size}"
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
                val pb = holder.itemView.findViewById<ProgressBar>(R.id.progressBar)
                pb.visibility = View.VISIBLE

                val listener = object : RequestListener<Drawable> {
                    override fun onLoadFailed(
                        e: GlideException?,
                        model: Any?,
                        target: Target<Drawable>,
                        isFirstResource: Boolean
                    ): Boolean {
                        pb.visibility = View.GONE
                        return false
                    }

                    override fun onResourceReady(
                        resource: Drawable,
                        model: Any,
                        target: Target<Drawable>?,
                        dataSource: DataSource,
                        isFirstResource: Boolean
                    ): Boolean {
                        pb.visibility = View.GONE
                        return false
                    }
                }

                when (val item = mutableImages[position]) {
                    is ImageItem.Remote -> Glide.with(this@FullscreenImageActivity).load(item.url).listener(listener).into(iv)
                    is ImageItem.Local -> Glide.with(this@FullscreenImageActivity).load(item.uri).listener(listener).into(iv)
                }
            }
        }

        binding.viewPager.adapter = pagerAdapter
        binding.viewPager.setCurrentItem(startPosition, false)
        updateCounter(startPosition)

        binding.viewPager.registerOnPageChangeCallback(object : ViewPager2.OnPageChangeCallback() {
            override fun onPageSelected(position: Int) = updateCounter(position)
        })

        binding.ivBack.setOnClickListener { finish() }

        binding.ivDelete.setOnClickListener {
            val pos = binding.viewPager.currentItem
            val item = mutableImages[pos]
            Utils.showDialogBox(this, "Delete Image", "Are you sure you want to delete this image?") {
                pendingOnDelete?.invoke(pos, item) {
                    mutableImages.removeAt(pos)
                    pagerAdapter.notifyItemRemoved(pos)
                    if (mutableImages.isEmpty()) {
                        finish()
                    } else {
                        val newPos = if (pos >= mutableImages.size) mutableImages.size - 1 else pos
                        binding.viewPager.setCurrentItem(newPos, false)
                        updateCounter(newPos)
                    }
                }
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        pendingImages = emptyList()
        pendingOnDelete = null
    }
}
