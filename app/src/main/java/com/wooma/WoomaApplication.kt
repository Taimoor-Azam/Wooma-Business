package com.wooma

import androidx.multidex.MultiDexApplication
import im.crisp.client.external.Crisp

class WoomaApplication : MultiDexApplication() {

    override fun onCreate() {
        super.onCreate()
        Crisp.configure(applicationContext, "461ed07b-d05d-4e7e-a6de-1826de6afe22")
    }
}
