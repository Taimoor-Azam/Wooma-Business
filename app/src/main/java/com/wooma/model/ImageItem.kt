package com.wooma.model

import android.net.Uri

sealed class ImageItem {
    data class Local(val uri: Uri) : ImageItem()
    data class Remote(val id: String, val url: String) : ImageItem()
}
