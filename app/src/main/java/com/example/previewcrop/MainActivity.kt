package com.example.previewcrop

import android.annotation.SuppressLint
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Highlight
import androidx.compose.material.icons.outlined.Highlight
import androidx.compose.material.icons.sharp.Lens
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.example.previewcrop.ui.theme.PreviewCropTheme
import com.example.previewcrop.utils.RequiredPermission
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.rememberMultiplePermissionsState
import kotlinx.coroutines.async
import kotlinx.coroutines.launch


/*
 * CameraView
 *
 * @author: zhhli
 * @date: 22/7/26
 */

class MainActivity : ComponentActivity() {

    @OptIn(ExperimentalPermissionsApi::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val permissions = listOf(android.Manifest.permission.CAMERA)

        setContent {
            PreviewCropTheme {
                val permissionsState = rememberMultiplePermissionsState(permissions = permissions)
                
                if (permissionsState.allPermissionsGranted) {
                    CameraExample()
                } else {
                    RequiredPermission(state = permissionsState) {
                        CameraExample()
                    }
                }
            }
        }
    }
}


@SuppressLint("UnsafeOptInUsageError")
@Composable
fun CameraExample() {

    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    val viewModel = remember {
        val config = AspectRatioCameraConfig(context)
        val model = CameraViewModel(config)
        model
    }


    Box(
        contentAlignment = Alignment.TopCenter,
        modifier = Modifier
            .fillMaxSize()
    ) {

        // Camera Preview
        CameraView(
            preview = viewModel.preview,
            imageCapture = viewModel.imageCapture,
            enableTorchProvider = { viewModel.enableTorch.value },
            modifier = Modifier
                .fillMaxSize()
        )

        // 裁剪区域
        DrawCropScan(
            topLeftScaleProvider = { viewModel.cropTopLeftScale.value },
            sizeScaleProvider = { viewModel.cropSizeScale.value }
        )

        // show cropped bitmap provided by taking picture
        if (viewModel.bitmapR.value != null) {
            ShowAfterCropImageToAnalysis(bitmapProvider = { viewModel.bitmapR.value!! })
        }


        // show recognized text
        Text(
            text = viewModel.scanText.value,
            modifier = Modifier
                .align(alignment = Alignment.BottomStart)
                .padding(horizontal = 10.dp, vertical = 100.dp)
                .heightIn(max = 150.dp)
                .widthIn(min = 100.dp)
                .background(Color.Transparent.copy(alpha = 0.6f)),
            color = Color.Red,
            textAlign = TextAlign.Left
        )

        // enableTorch
        IconButton(
            onClick = { viewModel.toggleTorch() },
            modifier = Modifier
                .align(alignment = Alignment.BottomEnd)
                .padding(bottom = 50.dp, end = 20.dp),
        ) {
            Icon(
                imageVector = if (viewModel.enableTorch.value) {
                    Icons.Filled.Highlight
                } else {
                    Icons.Outlined.Highlight
                },
                contentDescription = "enableTorch",
                tint = Color.White,
                modifier = Modifier
                    .size(60.dp)
                    .border(1.dp, Color.White, CircleShape)
                    .padding(10.dp),
            )
        }



        // Take picture
        IconButton(
            modifier = Modifier
                .padding(bottom = 40.dp)
                .align(alignment = Alignment.BottomCenter),
            onClick = {
                /*
                scope.launch {
                    viewModel.imageCapture.takePhoto(
                        outputDirectory = viewModel.getOutputDirectory(context),
                        onError = {

                        },
                        cropTextImage = viewModel::cropTextImage,
                        onImageCaptured = { cropedImageUri ->
                            /*
                        viewModel.recognizeText(
                            context,
                            cropedImageUri,
                        ) {

                        }

                         */
                        },
                        onBitmapCropped = { bitmap ->
                            viewModel.recognizeTextThroughBitmap(
                                bitmap
                            ) {

                            }
                        }
                    )
                }
                 */
                scope.launch {
                    val imageUri = async {  viewModel.takePictureAsync(context) }.await()
                    if (imageUri != null) {
                        val scannedText = async {  viewModel.recognizeTextAsync(context, imageUri) }.await()
                        viewModel.updateRecognizedText(scannedText)
                    }
                }
            },
            content = {
                Icon(
                    imageVector = Icons.Sharp.Lens,
                    contentDescription = "Take picture",
                    tint = Color.White,
                    modifier = Modifier
                        .size(80.dp)
                        .padding(1.dp)
                        .border(1.dp, Color.White, CircleShape)
                )
            }
        )



        // Resize cropBox
        IconButton(
            onClick = {
                viewModel.cropSizeScale.value = viewModel.cropSizeScale.value.copy(height = 0.3f)
            },
            modifier = Modifier
                .align(alignment = Alignment.BottomEnd)
                .padding(bottom = 200.dp, end = 20.dp),
        ) {
            Icon(
                imageVector = Icons.Filled.Add,
                contentDescription = "enableTorch",
                tint = Color.White,
                modifier = Modifier
                    .size(60.dp)
                    .border(1.dp, Color.White, CircleShape)
                    .padding(10.dp),
            )
        }

    }
}