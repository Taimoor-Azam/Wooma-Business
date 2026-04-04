package com.wooma.business

import android.app.Application
import im.crisp.client.external.Crisp

class WoomaApplication : Application() {

    override fun onCreate() {
        super.onCreate()
        Crisp.configure(applicationContext, "461ed07b-d05d-4e7e-a6de-1826de6afe22")
    }
}
