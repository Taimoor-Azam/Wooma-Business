package com.wooma

import androidx.multidex.MultiDexApplication
import com.wooma.sync.ConnectivityObserver
import com.wooma.sync.SyncScheduler
import im.crisp.client.external.Crisp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.launch

class WoomaApplication : MultiDexApplication() {

    val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    override fun onCreate() {
        super.onCreate()
        Crisp.configure(applicationContext, "461ed07b-d05d-4e7e-a6de-1826de6afe22")

        SyncScheduler.schedulePeriodicSync(this)

        // Trigger an immediate sync whenever the device comes online
        applicationScope.launch {
            ConnectivityObserver(this@WoomaApplication)
                .observeConnectivity()
                .filter { it }
                .collect { SyncScheduler.scheduleImmediateSync(this@WoomaApplication) }
        }
    }
}
