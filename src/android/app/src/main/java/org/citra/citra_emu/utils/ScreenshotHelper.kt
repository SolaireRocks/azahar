// Copyright 2026 Citra Emulator Project / Azahar Emulator Project
// Licensed under GPLv3 or any later version
// Refer to the LICENSE.txt file included.

package org.citra.citra_emu.utils

import android.content.ContentValues
import android.content.Context
import android.graphics.Bitmap
import android.os.Environment
import android.os.Handler
import android.os.Looper
import android.provider.MediaStore
import android.view.PixelCopy
import android.view.SurfaceView
import android.widget.Toast
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.concurrent.thread
import org.citra.citra_emu.NativeLibrary
import org.citra.citra_emu.R

object ScreenshotHelper {
    private const val ALBUM_NAME = "Azahar"

    fun capture(context: Context, surfaceView: SurfaceView?) {
        val mainHandler = Handler(Looper.getMainLooper())
        if (surfaceView == null ||
            surfaceView.width <= 0 ||
            surfaceView.height <= 0 ||
            !surfaceView.holder.surface.isValid
        ) {
            Toast.makeText(context, R.string.screenshot_failed_toast, Toast.LENGTH_SHORT).show()
            return
        }

        val bitmap = Bitmap.createBitmap(
            surfaceView.width,
            surfaceView.height,
            Bitmap.Config.ARGB_8888
        )

        try {
            PixelCopy.request(
                surfaceView,
                bitmap,
                { copyResult ->
                    if (copyResult == PixelCopy.SUCCESS) {
                        val appContext = context.applicationContext
                        thread(isDaemon = true, name = "azahar-screenshot") {
                            val saved = saveScreenshotBitmap(appContext, bitmap)
                            bitmap.recycle()
                            mainHandler.post {
                                val msgRes = if (saved) {
                                    R.string.screenshot_saved_toast
                                } else {
                                    R.string.screenshot_failed_toast
                                }
                                Toast.makeText(appContext, msgRes, Toast.LENGTH_SHORT).show()
                            }
                        }
                    } else {
                        bitmap.recycle()
                        Toast.makeText(
                            context,
                            R.string.screenshot_failed_toast,
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                },
                mainHandler
            )
        } catch (e: Exception) {
            bitmap.recycle()
            Log.error("[ScreenshotHelper] Failed to capture screenshot: ${e.message}")
            Toast.makeText(context, R.string.screenshot_failed_toast, Toast.LENGTH_SHORT).show()
        }
    }

    private fun saveScreenshotBitmap(context: Context, bitmap: Bitmap): Boolean {
        val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss_SSS", Locale.US).format(Date())
        val fileName = "Azahar_$timestamp.png"
        var publishedToGallery = false

        runCatching {
            val values = ContentValues().apply {
                put(MediaStore.Images.Media.DISPLAY_NAME, fileName)
                put(MediaStore.Images.Media.MIME_TYPE, "image/png")
                put(
                    MediaStore.Images.Media.RELATIVE_PATH,
                    "${Environment.DIRECTORY_PICTURES}/$ALBUM_NAME"
                )
                put(MediaStore.Images.Media.IS_PENDING, 1)
            }
            val resolver = context.contentResolver
            val uri = resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values)
            if (uri != null) {
                resolver.openOutputStream(uri)?.use { out ->
                    bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
                }
                values.clear()
                values.put(MediaStore.Images.Media.IS_PENDING, 0)
                resolver.update(uri, values, null, null)
                publishedToGallery = true
            }
        }.onFailure {
            Log.warning("[ScreenshotHelper] MediaStore save failed: ${it.message}")
        }

        var savedToUserDir = false
        runCatching {
            val userDir = NativeLibrary.getUserDirectory()
            if (userDir.isNotEmpty()) {
                val screenshotsDir = File(userDir, "screenshots")
                if (screenshotsDir.exists() || screenshotsDir.mkdirs()) {
                    val outFile = File(screenshotsDir, fileName)
                    FileOutputStream(outFile).use { out ->
                        bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
                    }
                    savedToUserDir = true
                }
            }
        }.onFailure {
            Log.warning("[ScreenshotHelper] UserDir save failed: ${it.message}")
        }

        return publishedToGallery || savedToUserDir
    }
}
