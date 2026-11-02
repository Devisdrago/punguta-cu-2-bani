package ro.punguta.app.ui.scan

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.view.CameraController
import androidx.camera.view.LifecycleCameraController
import androidx.camera.view.PreviewView
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Camera
import androidx.compose.material.icons.filled.FlashOff
import androidx.compose.material.icons.filled.FlashOn
import androidx.compose.material.icons.filled.NoPhotography
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import ro.punguta.app.PungutaApp
import ro.punguta.app.R

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ScanScreen(
    onBack: () -> Unit,
    onReview: (String) -> Unit,
    vm: ScanViewModel = viewModel(factory = ScanViewModel.Factory),
) {
    val context = LocalContext.current
    val state by vm.state.collectAsStateWithLifecycle()
    val hasCamera = remember { context.packageManager.hasSystemFeature(PackageManager.FEATURE_CAMERA_ANY) }
    var granted by remember { mutableStateOf(isCameraGranted(context)) }
    var askedOnce by rememberSaveable { mutableStateOf(false) }

    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
        granted = it
        askedOnce = true
    }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) vm.onPicked(uri)
    }
    val lock = (context.applicationContext as PungutaApp).container.appLock
    val pickImage = {
        lock.expectReturn()
        picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
    }

    // Returning from system settings may have changed the permission.
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { granted = isCameraGranted(context) }
    LaunchedEffect(Unit) {
        if (hasCamera && !granted && !askedOnce) permission.launch(Manifest.permission.CAMERA)
    }
    LaunchedEffect(state) {
        (state as? ScanState.Ready)?.let {
            vm.onNavigated()
            onReview(it.draftKey)
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.receipt_scan)) },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.back)) }
                },
                actions = {
                    TextButton(onClick = pickImage) {
                        Icon(Icons.Default.PhotoLibrary, null)
                        Spacer(Modifier.width(4.dp))
                        Text(stringResource(R.string.scan_gallery))
                    }
                },
            )
        },
    ) { padding ->
        Box(Modifier.padding(padding).fillMaxSize()) {
            when (val s = state) {
                ScanState.Processing, is ScanState.Ready -> Processing()
                is ScanState.Failed -> Failure(s.error, onRetry = vm::retry, onGallery = pickImage, onManual = vm::enterManually)
                ScanState.Camera -> when {
                    !hasCamera -> NoCamera(
                        message = stringResource(R.string.scan_no_camera),
                        primaryLabel = null,
                        onPrimary = {},
                        onGallery = pickImage,
                        onManual = vm::enterManually,
                    )
                    !granted -> {
                        val activity = context.findActivity()
                        val canAskAgain = !askedOnce || (activity != null &&
                            ActivityCompat.shouldShowRequestPermissionRationale(activity, Manifest.permission.CAMERA))
                        NoCamera(
                            message = stringResource(R.string.scan_permission_explainer),
                            primaryLabel = stringResource(if (canAskAgain) R.string.scan_allow_camera else R.string.scan_open_settings),
                            onPrimary = {
                                if (canAskAgain) {
                                    permission.launch(Manifest.permission.CAMERA)
                                } else {
                                    lock.expectReturn()
                                    openAppSettings(context)
                                }
                            },
                            onGallery = pickImage,
                            onManual = vm::enterManually,
                        )
                    }
                    else -> CameraPreview(vm)
                }
            }
        }
    }
}

@Composable
private fun CameraPreview(vm: ScanViewModel) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    var torch by remember { mutableStateOf(false) }
    val controller = remember {
        LifecycleCameraController(context).apply {
            setEnabledUseCases(CameraController.IMAGE_CAPTURE)
            imageCaptureMode = ImageCapture.CAPTURE_MODE_MAXIMIZE_QUALITY
        }
    }
    DisposableEffect(lifecycleOwner) {
        controller.bindToLifecycle(lifecycleOwner)
        onDispose { controller.unbind() }
    }

    Box(Modifier.fillMaxSize().background(Color.Black)) {
        AndroidView(
            factory = { ctx ->
                PreviewView(ctx).apply {
                    this.controller = controller
                    scaleType = PreviewView.ScaleType.FIT_CENTER
                }
            },
            modifier = Modifier.fillMaxSize(),
        )
        Text(
            stringResource(R.string.scan_tip),
            color = Color.White,
            textAlign = TextAlign.Center,
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.align(Alignment.TopCenter).fillMaxWidth()
                .background(Color.Black.copy(alpha = 0.5f)).padding(12.dp),
        )
        Row(
            Modifier.align(Alignment.BottomCenter).fillMaxWidth().padding(24.dp),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = {
                torch = !torch
                controller.enableTorch(torch)
            }) {
                Icon(
                    if (torch) Icons.Default.FlashOn else Icons.Default.FlashOff,
                    stringResource(R.string.scan_torch),
                    tint = Color.White,
                )
            }
            FilledIconButton(
                onClick = {
                    val file = vm.newCaptureFile()
                    controller.takePicture(
                        ImageCapture.OutputFileOptions.Builder(file).build(),
                        ContextCompat.getMainExecutor(context),
                        object : ImageCapture.OnImageSavedCallback {
                            override fun onImageSaved(output: ImageCapture.OutputFileResults) {
                                vm.onCaptured()
                            }

                            override fun onError(exception: ImageCaptureException) {
                                vm.onCaptureFailed()
                            }
                        },
                    )
                },
                modifier = Modifier.size(72.dp),
                colors = IconButtonDefaults.filledIconButtonColors(containerColor = Color.White, contentColor = Color.Black),
            ) {
                Icon(Icons.Default.Camera, stringResource(R.string.scan_capture), Modifier.size(36.dp))
            }
            Spacer(Modifier.size(48.dp))
        }
    }
}

@Composable
private fun Processing() {
    Column(Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
        CircularProgressIndicator()
        Spacer(Modifier.height(16.dp))
        Text(stringResource(R.string.scan_processing), style = MaterialTheme.typography.titleMedium)
        Text(
            stringResource(R.string.scan_processing_hint),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun Failure(error: ScanError, onRetry: () -> Unit, onGallery: () -> Unit, onManual: () -> Unit) {
    val message = stringResource(
        when (error) {
            ScanError.CAPTURE_FAILED -> R.string.scan_error_capture
            ScanError.IMAGE_UNREADABLE -> R.string.scan_error_image
            ScanError.NO_TEXT -> R.string.scan_error_no_text
            ScanError.OCR_FAILED -> R.string.scan_error_ocr
        },
    )
    Column(
        Modifier.fillMaxSize().padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterVertically),
    ) {
        Icon(Icons.Default.NoPhotography, null, Modifier.size(48.dp), tint = MaterialTheme.colorScheme.error)
        Text(message, textAlign = TextAlign.Center)
        Button(onClick = onRetry, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.scan_retry)) }
        OutlinedButton(onClick = onGallery, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.scan_gallery)) }
        TextButton(onClick = onManual) { Text(stringResource(R.string.scan_manual)) }
    }
}

@Composable
private fun NoCamera(
    message: String,
    primaryLabel: String?,
    onPrimary: () -> Unit,
    onGallery: () -> Unit,
    onManual: () -> Unit,
) {
    Column(
        Modifier.fillMaxSize().padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterVertically),
    ) {
        Icon(Icons.Default.NoPhotography, null, Modifier.size(48.dp), tint = MaterialTheme.colorScheme.outline)
        Text(message, textAlign = TextAlign.Center)
        if (primaryLabel != null) {
            Button(onClick = onPrimary, modifier = Modifier.fillMaxWidth()) { Text(primaryLabel) }
        }
        OutlinedButton(onClick = onGallery, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.scan_gallery)) }
        TextButton(onClick = onManual) { Text(stringResource(R.string.scan_manual)) }
    }
}

private fun isCameraGranted(context: Context) =
    ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED

private fun openAppSettings(context: Context) {
    context.startActivity(
        Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", context.packageName, null))
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
    )
}

private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}
