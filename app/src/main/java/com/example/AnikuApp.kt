package com.example

import android.app.Application
import android.os.Build
import androidx.work.*
import coil.Coil
import coil.ImageLoader
import coil.decode.GifDecoder
import coil.decode.ImageDecoderDecoder
import coil.disk.DiskCache
import coil.memory.MemoryCache
import okhttp3.OkHttpClient
import java.io.File

class AnikuApp : Application() {
    override fun onCreate() {
        super.onCreate()

        // Jadwalkan background token refresh setiap 50 menit (tetap jalan walau app di-kill)
        TokenRefreshWorker.schedule(this)

        val imageLoader = ImageLoader.Builder(this)
            // Memory cache: 25% dari RAM app (default Coil cuma 20%)
            .memoryCache {
                MemoryCache.Builder(this)
                    .maxSizePercent(0.25)
                    .build()
            }
            // Disk cache: simpan gambar ke storage HP, max 150MB
            .diskCache {
                DiskCache.Builder()
                    .directory(File(cacheDir, "image_cache"))
                    .maxSizeBytes(150L * 1024 * 1024) // 150 MB
                    .build()
            }
            .components {
                // GIF animasi (misal banner profil): ImageDecoderDecoder lebih baru & performa
                // lebih baik di Android 9+ (API 28), GifDecoder buat versi di bawahnya.
                if (Build.VERSION.SDK_INT >= 28) {
                    add(ImageDecoderDecoder.Factory())
                } else {
                    add(GifDecoder.Factory())
                }
            }
            .crossfade(true) // animasi fade saat gambar muncul
            .build()

        Coil.setImageLoader(imageLoader)
    }
}
