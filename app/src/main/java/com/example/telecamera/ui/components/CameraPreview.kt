package com.example.telecamera.ui.components

import android.graphics.PointF
import android.view.ViewGroup
import androidx.camera.view.PreviewView
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.viewinterop.AndroidView
import com.example.telecamera.domain.camera.ICameraManager

@Composable
fun CameraPreview(
    cameraManager: ICameraManager,
    onFocusTap: (PointF, Int, Int) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    var focusPoint by remember { mutableStateOf<FocusPoint?>(null) }
    var previewSize by remember { mutableStateOf(Pair(0, 0)) }

    val previewView = remember {
        PreviewView(context).apply {
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
            scaleType = PreviewView.ScaleType.FIT_CENTER
            implementationMode = PreviewView.ImplementationMode.COMPATIBLE
        }
    }

    DisposableEffect(lifecycleOwner) {
        cameraManager.bindCamera(lifecycleOwner, previewView)

        onDispose {
            cameraManager.unbindCamera()
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .pointerInput(Unit) {
                detectTapGestures { offset ->
                    val point = PointF(offset.x, offset.y)
                    focusPoint = FocusPoint(offset.x, offset.y)
                    onFocusTap(point, size.width, size.height)
                }
            }
    ) {
        AndroidView(
            factory = { previewView },
            modifier = Modifier.fillMaxSize(),
            update = { view ->
                previewSize = Pair(view.width, view.height)
            }
        )

        FocusIndicator(focusPoint = focusPoint)
    }
}

