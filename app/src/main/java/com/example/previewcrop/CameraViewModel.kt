package com.example.previewcrop

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import android.provider.MediaStore
import android.util.Log
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.lifecycle.ViewModel
import com.example.previewcrop.utils.ImageUtils
import com.example.previewcrop.utils.takePhotoAsync
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.TextRecognizer
import com.google.mlkit.vision.text.chinese.ChineseTextRecognizerOptions
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.text.SimpleDateFormat
import java.util.Locale
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlin.coroutines.suspendCoroutine
import kotlin.math.roundToInt


/*
 *
 *
 * @author: zhhli
 * @date: 22/7/29
 */
class CameraViewModel(config: CameraConfig) : ViewModel() {

    val preview = config.options(Preview.Builder())
    val imageCapture: ImageCapture = config.options(ImageCapture.Builder())
    //val imageAnalysis: ImageAnalysis = config.options(ImageAnalysis.Builder())

    // We only need to analyze the part of the image that has text, so we set crop percentages
    // to avoid analyze the entire image from the live camera feed.
    // 裁剪区域 比例
    val cropTopLeftScale = mutableStateOf(Offset(x = 0.025f, y = 0.3f))
    val cropSizeScale = mutableStateOf(Size(width = 0.95f, height = 0.1f))


    private val textRecognizer: TextRecognizer = TextRecognition.getClient(
        ChineseTextRecognizerOptions.Builder().build()
    )

    var scanText = mutableStateOf("")

    //var bitmapR = mutableStateOf(Bitmap.createBitmap(10, 10, Bitmap.Config.RGB_565))
    var bitmapR = mutableStateOf<Bitmap?>(null)

    var enableTorch: MutableState<Boolean> = mutableStateOf(false)

    fun toggleTorch() {
        enableTorch.value = !enableTorch.value
    }

    // Refactory Start
    @androidx.annotation.OptIn(androidx.camera.core.ExperimentalGetImage::class)
    suspend fun  takePictureAsync(
        filenameFormat: String = "yyyy-MM-dd-HH-mm-ss-SSS",
        outputDirectory: File,
    ): Uri? {
        val imageProxy = imageCapture.takePhotoAsync()
        val photoFile = File(
            outputDirectory,
            SimpleDateFormat(filenameFormat, Locale.US).format(System.currentTimeMillis()) + ".jpg"
        )

        if (imageProxy.image == null) {
            imageProxy.close()

            return null
        }

        val bitmap = cropTextImage(imageProxy) ?: return null

        withContext(Dispatchers.IO) {
            async {
                bitmap.saveToFile(photoFile)
            }
        }

        imageProxy.close()

        return Uri.fromFile(photoFile)
    }

    suspend fun recognizeTextAsync(
        context: Context,
        imageUri: Uri
    ): String = suspendCoroutine { continuation ->


        val bitmap = MediaStore.Images.Media.getBitmap(context.contentResolver, imageUri)
        Log.e("Main", (bitmap == null).toString())
        val inputImageCrop = InputImage.fromBitmap(bitmap, 0)

        textRecognizer.process(inputImageCrop)
            .addOnSuccessListener {
                val text = it.text

                Log.d("zzz", "textRecognizer onSuccess")
                Log.d("zzzzzz OCR result", "ocr result: $text")
                bitmapR.value = bitmap

                continuation.resume(text)
            }.addOnFailureListener { exception ->
                Log.d("zzz", "onFailure")
                bitmapR.value = bitmap
                scanText.value = "onFailure"

                continuation.resumeWithException(exception)
            }
    }

    fun updateRecognizedText(newText: String) {
        scanText.value = newText
    }

    // Refactory End

    fun getOutputDirectory(context: Context): File {


        val mediaDir = File(context.getExternalFilesDir(null), "image").apply {
            mkdir()
        }

        return mediaDir
    }

    @SuppressLint("UnsafeOptInUsageError")
    private fun cropTextImage(imageProxy: ImageProxy): Bitmap? {
        val mediaImage = imageProxy.image ?: return null

        val rotationDegrees = imageProxy.imageInfo.rotationDegrees


        val imageHeight = mediaImage.height
        val imageWidth = mediaImage.width

        val cropRect = when (rotationDegrees) {
            // for ImageAnalyze, pass heigh, width
            //90, 270 -> getCropRect90(imageHeight.toFloat(), imageWidth.toFloat()).toAndroidRect()
            //else -> getCropRect(imageHeight.toFloat(), imageWidth.toFloat()).toAndroidRect()

            // for imageCapture.onImageCapture, pass width, height
            90, 270 -> getCropRect90(
                imageWidth.toFloat(),
                imageHeight.toFloat()
            ).roundToAndroidRect()

            else -> getCropRect(
                imageWidth.toFloat(),
                imageHeight.toFloat()
            ).roundToAndroidRect()
        }


        //val convertImageToBitmap = ImageUtils.convertYuv420888ImageToBitmap(mediaImage)
        val convertImageToBitmap = ImageUtils.convertJpegImageToBitmap(mediaImage)

        val croppedBitmap =
            ImageUtils.rotateAndCrop(convertImageToBitmap, rotationDegrees, cropRect)

//        Log.d("===", "====================================")
//        Log.d("mediaImage", "$rotationDegrees width-height: $imageWidth * $imageHeight")
//        Log.d("cropRect", "$rotationDegrees width-height: ${cropRect.width()} * ${cropRect.height()}")
//        Log.d("cropRect", "$rotationDegrees ltrb: $cropRect")
//
//        Log.d("convertImageToBitmap", "width-height: ${convertImageToBitmap.width} * ${convertImageToBitmap.height}")
//        Log.d("croppedBitmap", "width-height: ${croppedBitmap.width} * ${croppedBitmap.height}")


        return croppedBitmap

    }


    private fun getCropRect(
        surfaceHeight: Float,
        surfaceWidth: Float,
        topLeftScale: Offset = cropTopLeftScale.value,
        sizeScale: Size = cropSizeScale.value,
    ): Rect {

        val height = surfaceHeight * sizeScale.height
        val with = surfaceWidth * sizeScale.width
        val topLeft = Offset(x = surfaceWidth * topLeftScale.x, y = surfaceHeight * topLeftScale.y)

        return Rect(offset = topLeft, size = Size(with, height))

    }

    private fun getCropRect90(
        surfaceHeight: Float,
        surfaceWidth: Float,
        topLeftScale: Offset = Offset(x = cropTopLeftScale.value.y, y = cropTopLeftScale.value.x),
        sizeScale: Size = Size(
            width = cropSizeScale.value.height,
            height = cropSizeScale.value.width
        ),
    ): Rect {

        val height = surfaceHeight * sizeScale.height
        val with = surfaceWidth * sizeScale.width
        val topLeft = Offset(x = surfaceWidth * topLeftScale.x, y = surfaceHeight * topLeftScale.y)

        return Rect(offset = topLeft, size = Size(with, height))

    }

    private fun Rect.roundToAndroidRect(): android.graphics.Rect {
        return android.graphics.Rect(
            left.roundToInt(),
            top.roundToInt(),
            right.roundToInt(),
            bottom.roundToInt()
        )
    }
}

private fun Bitmap.saveToFile(file: File) {
    var fos: FileOutputStream? = null

    try {
        fos = FileOutputStream(file)
        this.compress(Bitmap.CompressFormat.JPEG, 100, fos);
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