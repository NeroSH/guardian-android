/*
 * Copyright 2026 NeroSH
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.shdev.guardian.feature.child.impl

import androidx.annotation.OptIn
import androidx.camera.core.CameraSelector
import androidx.camera.core.ExperimentalGetImage
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.mlkit.vision.common.InputImage
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean

/**
 * CameraX preview + ML Kit barcode analysis. Fires [onScanned] exactly once with the first decoded
 * QR value (guarded by [handled]) so the caller can navigate away without racing extra frames.
 */
@Composable
fun QrScannerView(
    onScanned: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val lifecycleOwner = LocalLifecycleOwner.current
    val handled = remember { AtomicBoolean(false) }
    val analysisExecutor = remember { Executors.newSingleThreadExecutor() }

    AndroidView(
        modifier = modifier,
        factory = { ctx ->
            val previewView = PreviewView(ctx)
            val providerFuture = ProcessCameraProvider.getInstance(ctx)
            providerFuture.addListener({
                val provider = providerFuture.get()
                val preview = Preview.Builder().build().also {
                    it.surfaceProvider = previewView.surfaceProvider
                }
                val analysis = ImageAnalysis.Builder()
                    .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                    .build()
                    .also { it.setAnalyzer(analysisExecutor, buildAnalyzer(handled, onScanned)) }

                provider.unbindAll()
                provider.bindToLifecycle(
                    lifecycleOwner = lifecycleOwner,
                    cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA,
                    preview,
                    analysis
                )
            }, ContextCompat.getMainExecutor(ctx))
            previewView
        },
    )
}

/**
 * Decodes a QR code from a picked image (e.g. a screenshot of the parent's QR). Returns the first
 * QR value via [onResult], or null if the image has no QR / can't be read.
 */
fun scanQrFromImage(
    context: android.content.Context,
    uri: android.net.Uri,
    onResult: (String?) -> Unit
) {
    val input = try {
        InputImage.fromFilePath(context, uri)
    } catch (_: Exception) {
        onResult(null)
        return
    }
    BarcodeScanning.getClient().process(input)
        .addOnSuccessListener { barcodes ->
            onResult(barcodes.firstOrNull { it.format == Barcode.FORMAT_QR_CODE }?.rawValue)
        }
        .addOnFailureListener { onResult(null) }
}

private fun buildAnalyzer(handled: AtomicBoolean, onScanned: (String) -> Unit) =
    ImageAnalysis.Analyzer { imageProxy ->
        processFrame(imageProxy, handled, onScanned)
    }

@OptIn(ExperimentalGetImage::class)
private fun processFrame(
    imageProxy: androidx.camera.core.ImageProxy,
    handled: AtomicBoolean,
    onScanned: (String) -> Unit,
) {
    val mediaImage = imageProxy.image
    if (mediaImage == null || handled.get()) {
        imageProxy.close()
        return
    }
    val input = InputImage.fromMediaImage(mediaImage, imageProxy.imageInfo.rotationDegrees)
    val scanner = BarcodeScanning.getClient()
    scanner.process(input)
        .addOnSuccessListener { barcodes ->
            barcodes.firstOrNull { it.format == Barcode.FORMAT_QR_CODE }?.rawValue?.let { value ->
                if (handled.compareAndSet(false, true)) onScanned(value)
            }
        }
        .addOnCompleteListener { imageProxy.close() }
}
