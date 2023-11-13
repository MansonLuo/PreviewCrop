package com.example.previewcrop

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Typeface
import androidx.camera.core.*
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.animation.core.TweenSpec
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import kotlin.coroutines.resume
import kotlin.coroutines.suspendCoroutine


/*
 * 相机
 *
 * @author: zhhli
 * @date: 22/7/25
 */





// https://stackoverflow.com/a/70302763
@Composable
fun CameraView(
    modifier: Modifier = Modifier,
    preview: Preview,
    imageCapture: ImageCapture? = null,
    cameraSelector: CameraSelector = CameraSelector.DEFAULT_BACK_CAMERA,
    scaleType: PreviewView.ScaleType = PreviewView.ScaleType.FILL_CENTER,
    enableTorchProvider: () -> Boolean = { false },
    focusOnTap: Boolean = true
) {

    val context = LocalContext.current

    //1
    val previewView = remember { PreviewView(context) }
    val lifecycleOwner = LocalLifecycleOwner.current

    val cameraProvider by produceState<ProcessCameraProvider?>(initialValue = null) {
        value = context.getCameraProvider()
    }

    val camera = remember(cameraProvider) {
        cameraProvider?.let {
            it.unbindAll()
            it.bindToLifecycle(
                lifecycleOwner,
                cameraSelector,
                //*listOfNotNull(preview, imageAnalysis, imageCapture).toTypedArray()
                *listOfNotNull(preview, imageCapture).toTypedArray()
            )
        }
    }


    LaunchedEffect(true) {
        preview.setSurfaceProvider(previewView.surfaceProvider)
        previewView.scaleType = scaleType
    }


    LaunchedEffect(camera, enableTorchProvider()) {
        camera?.let {
            if (it.cameraInfo.hasFlashUnit()) {
                it.cameraControl.enableTorch(context, enableTorchProvider())
            }
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            cameraProvider?.unbindAll()
        }
    }

    // 3
    AndroidView(
        { previewView },
        modifier = modifier
            .fillMaxSize()
            .pointerInput(camera, focusOnTap) {
                if (!focusOnTap) return@pointerInput

                detectTapGestures {
                    val meteringPointFactory = SurfaceOrientedMeteringPointFactory(
                        size.width.toFloat(),
                        size.height.toFloat()
                    )

                    val meteringAction = FocusMeteringAction
                        .Builder(
                            meteringPointFactory.createPoint(it.x, it.y),
                            FocusMeteringAction.FLAG_AF
                        )
                        .disableAutoCancel()
                        .build()

                    camera?.cameraControl?.startFocusAndMetering(meteringAction)
                }
            },
    )
}


/**
 * 扫描框：按屏幕比例
 *
 * 当 sizeScale.height == 0f 或 sizeScale.width == 0f 为方形
 *
 */
@Composable
fun DrawCropScan(
    topLeftScaleProvider: () -> Offset,
    sizeScaleProvider: () -> Size,
    color: Color = MaterialTheme.colorScheme.primary,
) {

    var lineBottomY by remember { mutableStateOf(0f) }


    var isAnimated by remember { mutableStateOf(true) }


    val lineYAnimation by animateFloatAsState(
        targetValue = if (isAnimated) 0f else lineBottomY,
        animationSpec = infiniteRepeatable(animation = TweenSpec(durationMillis = 1500)), label = ""
    )

    LaunchedEffect(true) {
        isAnimated = !isAnimated
    }

    Canvas(
        modifier = Modifier
            .fillMaxSize()
            .onGloballyPositioned {
            }
    ) {

        val paint = Paint().asFrameworkPaint()

        paint.apply {
            isAntiAlias = true
            textSize = 24.sp.toPx()
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        }


        drawRect(Color.Transparent.copy(alpha = 0.1f))


        // 扫描框 高度、宽度
        var height = size.height * sizeScaleProvider().height
        var with = size.width * sizeScaleProvider().width

        // square 方形
        if (sizeScaleProvider().height == 0f) {
            height = with
        }
        if (sizeScaleProvider().width == 0f) {
            with = height
        }


        val topLeft = Offset(x = size.width * topLeftScaleProvider().x, y = size.height * topLeftScaleProvider().y)


        // 扫描框 矩形
        val rectF = Rect(offset = topLeft, size = Size(with, height))

//        Log.d("rectF", " width-height: ${rectF.width} * ${rectF.height}")
//        Log.d("rectF", "$rectF")
//        Log.d("size", "${size.toRect()}")


        drawRoundRect(
            color = Color.Transparent,
            topLeft = rectF.topLeft, size = rectF.size,
            blendMode = BlendMode.Clear
        )

        // 扫描线 可到达的最大位置
        lineBottomY = height - 5.dp.toPx()


        val padding = 10.dp.toPx()

        // 扫描线
        val rectLine = Rect(
            offset = topLeft.plus(Offset(x = padding, y = lineYAnimation)),
            size = Size(with - 2 * padding, 3.dp.toPx())
        )

        // 画扫描线
        drawOval(color, rectLine.topLeft, rectLine.size)

        // 边框
        val lineWith = 3.dp.toPx()
        val lineLength = 12.dp.toPx()

        val lSizeH = Size(lineLength, lineWith)
        val lSizeV = Size(lineWith, lineLength)

        val path = Path()
        // 左上角
        path.addRect(Rect(offset = rectF.topLeft, lSizeH))
        path.addRect(Rect(offset = rectF.topLeft, lSizeV))

        // 左下角
        path.addRect(Rect(offset = rectF.bottomLeft.minus(Offset(x = 0f, y = lineWith)), lSizeH))
        path.addRect(Rect(offset = rectF.bottomLeft.minus(Offset(x = 0f, y = lineLength)), lSizeV))


        // 右上角
        path.addRect(Rect(offset = rectF.topRight.minus(Offset(x = lineLength, y = 0f)), lSizeH))
        path.addRect(Rect(offset = rectF.topRight.minus(Offset(x = lineWith, y = 0f)), lSizeV))

        // 右下角
        path.addRect(
            Rect(offset = rectF.bottomRight.minus(Offset(x = lineLength, y = lineWith)), lSizeH)
        )
        path.addRect(
            Rect(offset = rectF.bottomRight.minus(Offset(x = lineWith, y = lineLength)), lSizeV)
        )

        drawPath(path = path, color = Color.White)


//        Log.d("zzzz topLeft ", topLeft.toString())
//        Log.d("zzzz canvas ", target2.toString())
//
//        Log.d("zzzz screenHeight ", screenHeight.toPx().toString())
//        Log.d("zzzz size ", size.toString())


    }
}


@Composable
fun ShowAfterCropImageToAnalysis(
    bitmapProvider: () -> Bitmap
) {

    Image(bitmap = bitmapProvider().asImageBitmap(), contentDescription = null,
        contentScale = ContentScale.FillWidth,
        modifier = Modifier
            .fillMaxWidth()
            .drawWithContent {
                drawContent()

                drawRect(
                    Color.Red,
                    Offset.Zero,
                    Size(height = size.height, width = size.width),
                    style = Stroke(width = 2.dp.toPx())
                )
            }
    )
}

private suspend fun Context.getCameraProvider(): ProcessCameraProvider =
    suspendCoroutine { continuation ->
        ProcessCameraProvider.getInstance(this).also { cameraProvider ->
            cameraProvider.addListener({
                continuation.resume(cameraProvider.get())
            }, ContextCompat.getMainExecutor(this))
        }
    }

private suspend fun CameraControl.enableTorch(context: Context, torch: Boolean): Unit =
    suspendCoroutine {
        enableTorch(torch).addListener(
            {},
            ContextCompat.getMainExecutor(context)

        )
    }




