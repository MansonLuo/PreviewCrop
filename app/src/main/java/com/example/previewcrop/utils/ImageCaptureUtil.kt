package com.example.previewcrop.utils

import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.ImageProxy
import java.util.concurrent.Executor
import java.util.concurrent.Executors
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlin.coroutines.suspendCoroutine


suspend fun ImageCapture.takePhotoAsync(
    executor: Executor = Executors.newSingleThreadExecutor()
): ImageProxy = suspendCoroutine {  continuation ->

   this.takePicture(
       executor,
       object : ImageCapture.OnImageCapturedCallback() {
           override fun onCaptureSuccess(image: ImageProxy) {
               continuation.resume(image)
           }

           override fun onError(exception: ImageCaptureException) {
               continuation.resumeWithException(exception)
           }
       }
   )
}