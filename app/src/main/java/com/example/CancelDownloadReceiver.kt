package com.example

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.example.network.AnikuViewModel

class CancelDownloadReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == "com.example.CANCEL_DOWNLOAD") {
            AnikuViewModel.instance?.cancelDownload()
        }
    }
}
