package com.example.core

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ImageDecoder
import android.net.Uri
import android.os.Build
import android.util.Log
import androidx.core.content.FileProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import kotlin.math.max
import kotlin.math.roundToInt

private const val TAG = "ImageUtils"
private const val MAX_IMAGE_DIMENSION = 1600

object ImageUtils {

    fun createCameraPhotoUri(context: Context): Uri {
        val cameraDir = File(context.cacheDir, "camera_photos").apply { mkdirs() }
        val photoFile = File(cameraDir, "capture_${System.currentTimeMillis()}.jpg")
        return FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            photoFile
        )
    }

    suspend fun saveProblemImageLocally(context: Context, sourceUri: Uri): String? =
        withContext(Dispatchers.IO) {
            try {
                val bitmap = decodeScaledBitmapFromUri(context, sourceUri, MAX_IMAGE_DIMENSION)
                    ?: return@withContext null
                val destDir = File(context.filesDir, "problem_images").apply { mkdirs() }
                val destFile = File(destDir, "problem_${System.currentTimeMillis()}.jpg")
                FileOutputStream(destFile).use { out ->
                    bitmap.compress(Bitmap.CompressFormat.JPEG, 88, out)
                }
                bitmap.recycle()
                destFile.absolutePath
            } catch (e: Exception) {
                Log.e(TAG, "Failed to persist problem image locally", e)
                null
            }
        }

    suspend fun loadBitmapFromPath(path: String, maxDimension: Int = MAX_IMAGE_DIMENSION): Bitmap? =
        withContext(Dispatchers.IO) {
            try {
                val file = File(path)
                if (!file.exists()) return@withContext null
                val boundsOpts = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                BitmapFactory.decodeFile(path, boundsOpts)
                val sampleSize = calculateInSampleSize(boundsOpts.outWidth, boundsOpts.outHeight, maxDimension)
                val decodeOpts = BitmapFactory.Options().apply { inSampleSize = sampleSize }
                val raw = BitmapFactory.decodeFile(path, decodeOpts) ?: return@withContext null
                scaleDownIfNeeded(raw, maxDimension)
            } catch (e: Exception) {
                Log.e(TAG, "Failed to load bitmap from $path", e)
                null
            }
        }

    fun deleteProblemImage(path: String?) {
        if (path.isNullOrBlank()) return
        try {
            val file = File(path)
            if (file.exists()) file.delete()
        } catch (e: Exception) {
            Log.w(TAG, "Failed to delete problem image $path", e)
        }
    }

    private fun decodeScaledBitmapFromUri(
        context: Context,
        uri: Uri,
        maxDimension: Int
    ): Bitmap? {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            val source = ImageDecoder.createSource(context.contentResolver, uri)
            ImageDecoder.decodeBitmap(source) { decoder, info, _ ->
                decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
                val width = info.size.width
                val height = info.size.height
                val longest = max(width, height)
                if (longest > maxDimension) {
                    val ratio = maxDimension.toFloat() / longest.toFloat()
                    decoder.setTargetSize(
                        (width * ratio).roundToInt().coerceAtLeast(1),
                        (height * ratio).roundToInt().coerceAtLeast(1)
                    )
                }
            }
        } else {
            val boundsOpts = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            context.contentResolver.openInputStream(uri)?.use { stream ->
                BitmapFactory.decodeStream(stream, null, boundsOpts)
            }
            val sampleSize = calculateInSampleSize(boundsOpts.outWidth, boundsOpts.outHeight, maxDimension)
            val decodeOpts = BitmapFactory.Options().apply { inSampleSize = sampleSize }
            val raw = context.contentResolver.openInputStream(uri)?.use { stream ->
                BitmapFactory.decodeStream(stream, null, decodeOpts)
            } ?: return null
            scaleDownIfNeeded(raw, maxDimension)
        }
    }

    private fun scaleDownIfNeeded(bitmap: Bitmap, maxDimension: Int): Bitmap {
        val longest = max(bitmap.width, bitmap.height)
        if (longest <= maxDimension) return bitmap
        val ratio = maxDimension.toFloat() / longest.toFloat()
        val targetW = (bitmap.width * ratio).roundToInt().coerceAtLeast(1)
        val targetH = (bitmap.height * ratio).roundToInt().coerceAtLeast(1)
        val scaled = Bitmap.createScaledBitmap(bitmap, targetW, targetH, true)
        if (scaled !== bitmap) bitmap.recycle()
        return scaled
    }

    private fun calculateInSampleSize(width: Int, height: Int, maxDim: Int): Int {
        var inSampleSize = 1
        val longest = max(width, height)
        if (longest > maxDim) {
            while (longest / (inSampleSize * 2) >= maxDim) {
                inSampleSize *= 2
            }
        }
        return inSampleSize
    }
}
