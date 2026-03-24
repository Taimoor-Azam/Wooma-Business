package com.wooma.business.customs

import android.content.Context
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraManager

data class Lens(
    val cameraId: String,
    val focalLength: Float
)

fun detectBackLenses(context: Context): List<Lens> {
    val manager = context.getSystemService(Context.CAMERA_SERVICE) as CameraManager
    val lenses = mutableListOf<Lens>()

    for (id in manager.cameraIdList) {
        val chars = manager.getCameraCharacteristics(id)

        if (chars.get(CameraCharacteristics.LENS_FACING)
            != CameraCharacteristics.LENS_FACING_BACK) continue

        val focals =
            chars.get(CameraCharacteristics.LENS_INFO_AVAILABLE_FOCAL_LENGTHS)
                ?: continue

        lenses.add(Lens(id, focals.minOrNull()!!))
    }
    return lenses.sortedBy { it.focalLength }
}

fun classifyLenses(lenses: List<Lens>): Map<String, String> {
    val result = mutableMapOf<String, String>()

    lenses.forEach {
        val label = when {
            it.focalLength < 2.3f -> "0.6x"
            it.focalLength < 7f -> "1x"
            else -> "2x"
        }
        result[label] = it.cameraId
    }
    return result
}

