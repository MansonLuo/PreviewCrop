package com.example.previewcrop.utils

import android.graphics.Bitmap
import android.net.Uri
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.ImageProxy
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.concurrent.Executor
import java.util.concurrent.Executors
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlin.coroutines.suspendCoroutine
import kotlin.reflect.KFunction1

@androidx.annotation.OptIn(androidx.camera.core.ExperimentalGetImage::class)
suspend fun ImageCapture.takeMyPhoto(
    scope: CoroutineScope,
    filenameFormat: String = "yyyy-MM-dd-HH-mm-ss-SSS",
    outputDirectory: File,
    executor: Executor = Executors.newSingleThreadExecutor(),
    cropTextImage: KFunction1<ImageProxy, Bitmap?>
): Uri? = suspendCoroutine { continuation ->

    val photoFile = File(
        outputDirectory,
        SimpleDateFormat(filenameFormat, Locale.US).format(System.currentTimeMillis()) + ".jpg"
    )

    this.takePicture(
        executor,
        createTakePictureCallback(
            onSuccess = continuation::resume,
            onError = continuation::resumeWithException
        ) { image ->

            if (image.image == null) {
                image.close()
                return@createTakePictureCallback null
            }

            val bitmap = cropTextImage(image) ?: return@createTakePictureCallback null

            saveBitmapToFile(bitmap, photoFile)

            image.close()

            Uri.fromFile(photoFile)
        }
    )
}

private fun createTakePictureCallback(
    onSuccess: (Uri?) -> Unit,
    onError: (ImageCaptureException) -> Unit,
    onProcessImage: (ImageProxy) -> Uri?,
) = object : ImageCapture.OnImageCapturedCallback() {
    override fun onCaptureSuccess(image: ImageProxy) {
        val uri = onProcessImage(image)

        onSuccess(uri)
    }

    override fun onError(exception: ImageCaptureException) {
        onError(exception)
    }
}

private fun saveBitmapToFile(
    bitmap: Bitmap,
    file: File
) {
    var fos: FileOutputStream? = null

    try {
        fos = FileOutputStream(file);
        bitmap.compress(Bitmap.CompressFormat.JPEG, 100, fos);
        fos.flush();
    } catch (e: IOException) {
        e.printStackTrace();
    } finally {
        try {
            if (fos != null) {
                fos.close();
            }
        } catch (e: IOException) {
            e.printStackTrace();
        }
    }
}