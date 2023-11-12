package com.example.previewcrop.utils

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import com.google.accompanist.permissions.ExperimentalPermissionsApi

import com.google.accompanist.permissions.rememberMultiplePermissionsState


/*
 * 权限请求
 *
 * @author: zhhli
 * @date: 22/7/26
 */

@OptIn(ExperimentalPermissionsApi::class)
@Composable
fun PermissionView(
    permissions: List<String> = listOf(android.Manifest.permission.CAMERA),
    content: @Composable () -> Unit = { }
) {
    val state = rememberMultiplePermissionsState(permissions = permissions)

    if (state.allPermissionsGranted) {
        content()
    } else {
        RequiredPermission(state = state, content)
    }
}

@Composable
private fun Rationale(
    text: String,
    onRequestPermission: () -> Unit
) {
    AlertDialog(
        onDismissRequest = { /* Don't */ },
        title = {
            Text(text = "请求权限")
        },
        text = {
            Text(text)
        },
        confirmButton = {
            TextButton(onClick = onRequestPermission) {
                Text("确定")
            }
        }
    )
}

fun openSettingsPermission(context: Context) {
    context.startActivity(
        Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
            data = Uri.fromParts("package", context.packageName, null)
        }
    )
}
