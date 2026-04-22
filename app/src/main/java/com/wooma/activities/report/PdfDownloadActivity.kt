package com.wooma.activities.report

import android.app.DownloadManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.os.Handler
import android.os.Looper
import android.view.View
import androidx.core.content.FileProvider
import com.wooma.activities.BaseActivity
import com.wooma.databinding.ActivityPdfDownloadBinding
import java.io.File

class PdfDownloadActivity : BaseActivity() {

    private lateinit var binding: ActivityPdfDownloadBinding
    private var downloadReceiver: BroadcastReceiver? = null
    private var downloadId: Long = -1
    private val progressHandler = Handler(Looper.getMainLooper())
    private var progressRunnable: Runnable? = null

    private var pdfUrl = ""
    private var reportId = ""

    companion object {
        const val EXTRA_PDF_URL = "pdf_url"
        const val EXTRA_REPORT_ID = "report_id"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityPdfDownloadBinding.inflate(layoutInflater)
        setContentView(binding.root)
        applyWindowInsetsToBinding(binding.root)

        pdfUrl = intent.getStringExtra(EXTRA_PDF_URL) ?: ""
        reportId = intent.getStringExtra(EXTRA_REPORT_ID) ?: ""

        binding.ivBack.setOnClickListener { finish() }
        binding.btnRetry.setOnClickListener { startDownload() }

        val fileName = "report_${reportId}.pdf"
        val file = File(getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS), fileName)
        if (file.exists()) {
            openPdf(file)
            finish()
        } else {
            startDownload()
        }
    }

    private fun startDownload() {
        if (pdfUrl.isEmpty()) {
            showError("PDF not available.")
            return
        }
        if (!isNetworkAvailable()) {
            showError("No internet connection. Please check your network and try again.")
            return
        }

        showDownloading()

        val fileName = "report_${reportId}.pdf"
        val request = DownloadManager.Request(Uri.parse(pdfUrl))
            .setTitle("Report PDF")
            .setDescription("Downloading...")
            .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
            .setDestinationInExternalFilesDir(this, Environment.DIRECTORY_DOWNLOADS, fileName)
            .setMimeType("application/pdf")

        val dm = getSystemService(DOWNLOAD_SERVICE) as DownloadManager
        downloadId = dm.enqueue(request)
        startProgressPolling(dm)

        downloadReceiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) {
                val id = intent.getLongExtra(DownloadManager.EXTRA_DOWNLOAD_ID, -1)
                if (id != downloadId) return

                stopProgressPolling()
                unregisterReceiver(this)
                downloadReceiver = null

                val query = DownloadManager.Query().setFilterById(downloadId)
                val cursor = dm.query(query)
                if (cursor.moveToFirst()) {
                    val statusCol = cursor.getColumnIndex(DownloadManager.COLUMN_STATUS)
                    val status = if (statusCol >= 0) cursor.getInt(statusCol) else -1
                    if (status == DownloadManager.STATUS_SUCCESSFUL) {
                        binding.progressBar.progress = 100
                        binding.tvPercent.text = "100%"
                        val file = File(getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS), fileName)
                        openPdf(file)
                    } else {
                        val reasonCol = cursor.getColumnIndex(DownloadManager.COLUMN_REASON)
                        val reason = if (reasonCol >= 0) cursor.getInt(reasonCol) else -1
                        showError(downloadErrorMessage(reason))
                    }
                } else {
                    showError("Download failed. Please try again.")
                }
                cursor.close()
            }
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(
                downloadReceiver,
                IntentFilter(DownloadManager.ACTION_DOWNLOAD_COMPLETE),
                RECEIVER_NOT_EXPORTED
            )
        } else {
            @Suppress("UnspecifiedRegisterReceiverFlag")
            registerReceiver(downloadReceiver, IntentFilter(DownloadManager.ACTION_DOWNLOAD_COMPLETE))
        }
    }

    private fun openPdf(file: File) {
        val uri = FileProvider.getUriForFile(this, "${packageName}.provider", file)
        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, "application/pdf")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            addFlags(Intent.FLAG_ACTIVITY_NO_HISTORY)
        }
        try {
            startActivity(intent)
            finish()
        } catch (e: Exception) {
            showError("No PDF viewer found. Please install a PDF viewer app.")
        }
    }

    private fun startProgressPolling(dm: DownloadManager) {
        val fileName = "report_${reportId}.pdf"
        progressRunnable = object : Runnable {
            override fun run() {
                val query = DownloadManager.Query().setFilterById(downloadId)
                val cursor = dm.query(query)
                if (!cursor.moveToFirst()) {
                    cursor.close()
                    progressHandler.postDelayed(this, 500)
                    return
                }

                val statusCol = cursor.getColumnIndex(DownloadManager.COLUMN_STATUS)
                val status = if (statusCol >= 0) cursor.getInt(statusCol) else -1

                when (status) {
                    DownloadManager.STATUS_SUCCESSFUL -> {
                        cursor.close()
                        stopProgressPolling()
                        binding.progressBar.progress = 100
                        binding.tvPercent.text = "100%"
                        val file = File(getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS), fileName)
                        openPdf(file)
                    }
                    DownloadManager.STATUS_FAILED -> {
                        val reasonCol = cursor.getColumnIndex(DownloadManager.COLUMN_REASON)
                        val reason = if (reasonCol >= 0) cursor.getInt(reasonCol) else -1
                        cursor.close()
                        stopProgressPolling()
                        showError(downloadErrorMessage(reason))
                    }
                    else -> {
                        val downloadedCol = cursor.getColumnIndex(DownloadManager.COLUMN_BYTES_DOWNLOADED_SO_FAR)
                        val totalCol = cursor.getColumnIndex(DownloadManager.COLUMN_TOTAL_SIZE_BYTES)
                        val downloaded = if (downloadedCol >= 0) cursor.getLong(downloadedCol) else 0L
                        val total = if (totalCol >= 0) cursor.getLong(totalCol) else 0L
                        cursor.close()
                        if (total > 0) {
                            val percent = (downloaded * 100 / total).toInt()
                            binding.progressBar.progress = percent
                            binding.tvPercent.text = "$percent%"
                        }
                        progressHandler.postDelayed(this, 500)
                    }
                }
            }
        }
        progressHandler.post(progressRunnable!!)
    }

    private fun stopProgressPolling() {
        progressRunnable?.let { progressHandler.removeCallbacks(it) }
        progressRunnable = null
    }

    private fun showDownloading() {
        binding.downloadingLayout.visibility = View.VISIBLE
        binding.errorLayout.visibility = View.GONE
    }

    private fun showError(message: String) {
        binding.downloadingLayout.visibility = View.GONE
        binding.errorLayout.visibility = View.VISIBLE
        binding.tvErrorMessage.text = message
    }

    private fun isNetworkAvailable(): Boolean {
        val cm = getSystemService(CONNECTIVITY_SERVICE) as ConnectivityManager
        val network = cm.activeNetwork ?: return false
        val caps = cm.getNetworkCapabilities(network) ?: return false
        return caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
    }

    private fun downloadErrorMessage(reason: Int): String = when (reason) {
        DownloadManager.ERROR_CANNOT_RESUME -> "Download cannot be resumed. Please retry."
        DownloadManager.ERROR_DEVICE_NOT_FOUND -> "Storage not found. Please check your device storage."
        DownloadManager.ERROR_FILE_ALREADY_EXISTS -> "File already exists."
        DownloadManager.ERROR_FILE_ERROR -> "Storage error. Please check available space."
        DownloadManager.ERROR_HTTP_DATA_ERROR -> "Network error. Please check your connection and try again."
        DownloadManager.ERROR_INSUFFICIENT_SPACE -> "Not enough storage space."
        DownloadManager.ERROR_TOO_MANY_REDIRECTS -> "Server error. Please try again later."
        DownloadManager.ERROR_UNHANDLED_HTTP_CODE -> "Server error. Please try again later."
        DownloadManager.ERROR_UNKNOWN -> "Unknown error. Please try again."
        else -> "Download failed. Please try again."
    }

    override fun onDestroy() {
        super.onDestroy()
        stopProgressPolling()
        downloadReceiver?.let {
            try { unregisterReceiver(it) } catch (_: Exception) {}
        }
    }
}
