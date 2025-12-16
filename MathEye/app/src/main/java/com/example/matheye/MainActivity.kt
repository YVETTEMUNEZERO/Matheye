package com.example.matheye

import android.Manifest
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.ImageDecoder
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.MediaStore
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AddAPhoto
import androidx.compose.material.icons.filled.AddPhotoAlternate
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Crop
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import androidx.navigation.NavController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.canhub.cropper.CropImageContract
import com.canhub.cropper.CropImageContractOptions
import com.canhub.cropper.CropImageOptions
import com.example.matheye.ui.theme.MathEyeTheme
import kotlinx.coroutines.launch
import java.io.File
import java.io.ByteArrayOutputStream

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            MathEyeTheme {
                val navController = rememberNavController()
                NavHost(navController = navController, startDestination = "splash") {
                    composable("splash") { SplashScreen(navController = navController) }
                    composable("main") { MainScreen(navController = navController) }
                    composable("history") { HistoryScreen(navController = navController) }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(navController: NavController, modifier: Modifier = Modifier) {
    var bitmap by remember { mutableStateOf<Bitmap?>(null) }
    var recognizedText by remember { mutableStateOf<String?>(null) }
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val database = AppDatabase.getDatabase(context)
    val clipboardManager = LocalClipboardManager.current
    var tempImageUri by remember { mutableStateOf<Uri?>(null) }

    val classifier = remember { TFLiteClassifier(context) }
    var isClassifierInitialized by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        classifier.initialize()
        isClassifierInitialized = true
    }

    DisposableEffect(Unit) {
        onDispose {
            classifier.close()
        }
    }

    val onImageSelected: (Bitmap) -> Unit = { newBitmap ->
        bitmap = newBitmap.copy(Bitmap.Config.ARGB_8888, true)
        recognizedText = null // Reset previous recognition
    }

    val cropImageLauncher = rememberLauncherForActivityResult(CropImageContract()) { result ->
        if (result.isSuccessful) {
            result.uriContent?.let {
                onImageSelected(uriToBitmap(context, it))
            }
        }
    }

    val cameraLauncher = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { success ->
        if (success) {
            tempImageUri?.let { onImageSelected(uriToBitmap(context, it)) }
        }
    }

    val galleryLauncher = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        uri?.let { onImageSelected(uriToBitmap(context, it)) }
    }

    val cameraPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission(),
        onResult = { isGranted ->
            if (isGranted) {
                val uri = FileProvider.getUriForFile(context, "${context.packageName}.provider", File(context.cacheDir, "temp_image.jpg"))
                tempImageUri = uri
                cameraLauncher.launch(uri)
            }
        }
    )

    val saveAsTxtLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("text/plain"),
        onResult = { uri ->
            uri?.let {
                try {
                    context.contentResolver.openOutputStream(it)?.use { outputStream ->
                        outputStream.writer().use { writer ->
                            recognizedText?.let { text -> writer.write(text) }
                        }
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
        }
    )

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            painter = painterResource(id = R.drawable.ic_launcher),
                            contentDescription = "MathEye App Icon",
                            modifier = Modifier.size(40.dp).clip(RoundedCornerShape(8.dp)),
                            tint = Color.Unspecified
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "MathEye",
                            color = Color.White,
                            style = MaterialTheme.typography.headlineMedium
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color(0xFF388E3C)
                ),
                actions = {
                    IconButton(onClick = { navController.navigate("history") }) {
                        Icon(Icons.Filled.History, contentDescription = "History", tint = Color.White)
                    }
                }
            )
        },
        modifier = modifier.fillMaxSize()
    ) { innerPadding ->
        Box(modifier = Modifier.fillMaxSize().padding(innerPadding)) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(bottom = 48.dp), // Padding to avoid overlap with signature
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally
            ) {

                bitmap?.let {
                    Image(bitmap = it.asImageBitmap(), contentDescription = "Captured image", modifier = Modifier.padding(16.dp))
                }
                if (recognizedText != null) {
                    Text(text = recognizedText!!, modifier = Modifier.padding(16.dp))
                }

                Spacer(modifier = Modifier.height(16.dp))

                if (bitmap == null) {
                    Spacer(modifier = Modifier.height(24.dp))
                    Text(
                        text = "\uD83D\uDCF8 Capture or upload a photo of a math symbol to recognise it.",
                        style = MaterialTheme.typography.bodyMedium,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(horizontal = 16.dp)
                    )
                    Spacer(modifier = Modifier.height(24.dp))
                    // Initial state: No image selected
                    Row {
                        Button(onClick = { cameraPermissionLauncher.launch(Manifest.permission.CAMERA) }) {
                            Icon(Icons.Filled.AddAPhoto, contentDescription = "Take a picture")
                            Text(text = "Take a picture", modifier = Modifier.padding(start = 8.dp))
                        }
                        Spacer(modifier = Modifier.width(16.dp))
                        Button(onClick = { galleryLauncher.launch("image/*") }) {
                            Icon(Icons.Filled.AddPhotoAlternate, contentDescription = "Upload from gallery")
                            Text(text = "Upload", modifier = Modifier.padding(start = 8.dp))
                        }
                    }
                } else {


                    // An image is selected
                    if (recognizedText == null) {
                        // State: Image selected, ready for recognition
                        Row {
                            if (!isClassifierInitialized) {
                                CircularProgressIndicator()
                            } else {
                                Button(onClick = { recognizedText = classifier.recognizeImage(bitmap!!) }) {
                                    Text(text = "Recognise Math")
                                }
                            }
                            Spacer(modifier = Modifier.width(16.dp))
                            Button(onClick = {
                                val cropOptions = CropImageContractOptions(bitmapToUri(context, bitmap!!), CropImageOptions())
                                cropImageLauncher.launch(cropOptions)
                            }) {
                                Icon(Icons.Filled.Crop, contentDescription = "Crop")
                                Text(text = "Crop", modifier = Modifier.padding(start = 8.dp))
                            }
                        }
                    } else {
                        // State: Recognition complete
                        val isResultValid = recognizedText != " ⚠\uFE0F Error: No math symbol recognized. Please try again!" && !recognizedText!!.contains("Error")
                        if (isResultValid) {
                            // Success state: Show action buttons
                            Text(
                                text = "✅ Symbol recognized successfully.",
                                style = MaterialTheme.typography.bodyMedium,
                                textAlign = TextAlign.Center,
                                modifier = Modifier.padding(horizontal = 16.dp)
                            )
                            Spacer(modifier = Modifier.height(24.dp))
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Row {
                                    Button(onClick = {
                                        scope.launch {
                                            recognizedText?.let { database.historyDao().insert(HistoryItem(text = it)) }
                                        }
                                    }) {
                                        Icon(Icons.Filled.Save, contentDescription = "Save recognized text")
                                        Text(text = "Save", modifier = Modifier.padding(start = 8.dp))
                                    }

                                    Spacer(modifier = Modifier.width(8.dp))
                                    Button(onClick = { recognizedText?.let { clipboardManager.setText(AnnotatedString(it)) } }) {
                                        Icon(Icons.Filled.ContentCopy, contentDescription = "Copy")
                                        Text(text = "Copy", modifier = Modifier.padding(start = 8.dp))
                                    }
                                }
                                Row(modifier = Modifier.padding(top = 8.dp)) {
                                    Button(onClick = {
                                        val sendIntent: Intent = Intent().apply {
                                            action = Intent.ACTION_SEND
                                            putExtra(Intent.EXTRA_TEXT, recognizedText)
                                            type = "text/plain"
                                        }
                                        context.startActivity(Intent.createChooser(sendIntent, null))
                                    }) {
                                        Icon(Icons.Filled.Share, contentDescription = "Share")
                                        Text(text = "Share", modifier = Modifier.padding(start = 8.dp))
                                    }
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Button(onClick = { bitmap = null; recognizedText = null }) {
                                        Icon(Icons.Filled.Add, contentDescription = "New Recognition")
                                        Text(text = "New", modifier = Modifier.padding(start = 8.dp))
                                    }
                                }
                            }
                        } else {
                            // Failure state: Show discard button
                            Button(onClick = { bitmap = null; recognizedText = null }) {
                                Icon(Icons.Filled.Clear, contentDescription = "Discard")
                                Text(text = "Discard", modifier = Modifier.padding(start = 8.dp))
                            }
                        }
                    }
                }
                Spacer(modifier = Modifier.height(64.dp)) // Extra space at the bottom
            }
            Text(
                text = "from Yvette&Obed",
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(16.dp),
                style = MaterialTheme.typography.bodySmall,
                color = Color.DarkGray
            )
        }
    }
}

private fun bitmapToUri(context: Context, bitmap: Bitmap): Uri {
    val file = File(context.cacheDir, "temp_image_for_crop.jpg")
    file.delete() // Delete any previous temp image
    file.createNewFile()
    val bytes = ByteArrayOutputStream()
    bitmap.compress(Bitmap.CompressFormat.JPEG, 100, bytes)
    file.writeBytes(bytes.toByteArray())
    return FileProvider.getUriForFile(context, "${context.packageName}.provider", file)
}

private fun uriToBitmap(context: Context, uri: Uri): Bitmap {
    return if (Build.VERSION.SDK_INT < 28) {
        @Suppress("DEPRECATION")
        MediaStore.Images.Media.getBitmap(context.contentResolver, uri)
    } else {
        val source = ImageDecoder.createSource(context.contentResolver, uri)
        ImageDecoder.decodeBitmap(source)
    }
}

@Preview(showBackground = true)
@Composable
fun MainScreenPreview() {
    MathEyeTheme {
        MainScreen(navController = rememberNavController())
    }
}
