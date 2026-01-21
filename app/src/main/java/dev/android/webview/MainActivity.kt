package dev.android.webview

import android.Manifest
import android.annotation.SuppressLint
import android.app.DownloadManager
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Context.DOWNLOAD_SERVICE
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.os.Environment
import android.os.Message
import android.view.View
import android.view.ViewGroup
import android.webkit.CookieManager
import android.webkit.GeolocationPermissions
import android.webkit.PermissionRequest
import android.webkit.URLUtil
import android.webkit.ValueCallback
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.FrameLayout
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.browser.customtabs.CustomTabsIntent
import androidx.browser.customtabs.CustomTabsIntent.SHARE_STATE_OFF
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.core.net.toUri
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import dev.android.webview.ui.theme.WebViewTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            WebViewTheme {
                Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                    HomeScreen(modifier = Modifier.padding(innerPadding))
                }
            }
        }
    }
}

@SuppressLint("NewApi")
@Composable
fun HomeScreen(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val activity = context as ComponentActivity

    var urlInput by remember { mutableStateOf("https://www.google.com") }
    var webViewInstance by remember { mutableStateOf<WebView?>(null) }
    var isLoading by remember { mutableStateOf(false) }
    var isCustomViewVisible by remember { mutableStateOf(false) }

    BackHandler(enabled = true) {
        when {
            isCustomViewVisible -> {
                webViewInstance?.webChromeClient?.onHideCustomView()
            }
            webViewInstance?.canGoBack() == true -> {
                webViewInstance?.goBack()
            }
            else -> {
                webViewInstance?.stopLoading()
                webViewInstance?.destroy()
                activity.finish()
            }
        }
    }

    fun performAction(web: WebView?, input: String, loading: Boolean) {
        web?.let {
            if (loading) {
                it.stopLoading()
                return
            }

            val trimmedInput = input.trim()
            if (trimmedInput.isEmpty()) return

            val targetUrl = when {
                trimmedInput.startsWith("http://") || trimmedInput.startsWith("https://") -> trimmedInput
                trimmedInput.contains(".") && !trimmedInput.contains(" ") -> "https://$trimmedInput"
                else -> "https://www.google.com/search?q=$trimmedInput"
            }

            it.loadUrl(targetUrl)
        }
    }

    Column(modifier = modifier.fillMaxSize().imePadding()) {

        Box(modifier = Modifier.weight(1f)) {
            WebViewWrapper(
                initialUrl = urlInput,
                onWebViewCreated = { webViewInstance = it },
                onLoadingStateChange = { loading -> isLoading = loading },
                onUrlChanged = { newUrl -> urlInput = newUrl },
                onCustomViewToggle = { visible -> isCustomViewVisible = visible },
                context = context,
                activity = activity
            )
        }

        Surface(
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(bottom = 8.dp)) {

                if (isLoading) {
                    LinearProgressIndicator(
                        modifier = Modifier.fillMaxWidth().height(2.dp),
                        color = MaterialTheme.colorScheme.primary,
                        trackColor = MaterialTheme.colorScheme.surfaceVariant,
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                } else {
                    Spacer(modifier = Modifier.height(8.dp))
                }

                Box(modifier = Modifier.padding(horizontal = 8.dp)) {
                    OutlinedTextField(
                        value = urlInput,
                        onValueChange = { urlInput = it },
                        modifier = Modifier.fillMaxWidth(),
                        placeholder = { Text("Search or type URL") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                        keyboardActions = KeyboardActions(
                            onSearch = {
                                performAction(webViewInstance, urlInput, isLoading)
                            }
                        ),
                        trailingIcon = {
                            IconButton(onClick = {
                                performAction(webViewInstance, urlInput, isLoading)
                            }) {
                                val icon = when {
                                    isLoading -> Icons.Default.Close
                                    urlInput != webViewInstance?.url -> Icons.Default.PlayArrow
                                    else -> Icons.Default.Refresh
                                }

                                Icon(
                                    imageVector = icon,
                                    contentDescription = null,
                                    tint = if (isLoading) MaterialTheme.colorScheme.error
                                    else MaterialTheme.colorScheme.primary
                                )
                            }
                        }
                    )
                }
            }
        }
    }
}

@SuppressLint("SetJavaScriptEnabled")
@Composable
fun WebViewWrapper(
    initialUrl: String,
    onWebViewCreated: (WebView) -> Unit,
    onLoadingStateChange: (Boolean) -> Unit,
    onUrlChanged: (String) -> Unit,
    onCustomViewToggle: (Boolean) -> Unit,
    context: Context,
    activity: ComponentActivity
) {

    fun openInCustomTab(context: Context, url: String) {
        val uri = url.toUri()
        try {
            val builder = CustomTabsIntent.Builder()
                .setShowTitle(true)
                .setShareState(SHARE_STATE_OFF)
                .setShareIdentityEnabled(true)
                .setInstantAppsEnabled(true)

            val customTabsIntent = builder.build()
            customTabsIntent.launchUrl(context, uri)
        } catch (_: Exception) {
            val intent = Intent(Intent.ACTION_VIEW, uri)
            if (intent.resolveActivity(context.packageManager) != null) {
                context.startActivity(intent)
            } else {
                Toast.makeText(context, "Unable to open this link", Toast.LENGTH_SHORT).show()
            }
        }
    }

    fun handleExternalUri(uri: Uri): Boolean {
        val urlStr = uri.toString()
        val scheme = uri.scheme ?: return false

        fun safeStart(intent: Intent): Boolean {
            return try {
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                context.startActivity(intent)
                true
            } catch (_: ActivityNotFoundException) {
                Toast.makeText(context, "Cannot open intent: $urlStr", Toast.LENGTH_SHORT).show()
                false
            }
        }

        return try {
            if (scheme == "intent") {
                try {
                    val intent = Intent.parseUri(urlStr, Intent.URI_INTENT_SCHEME)
                    if (safeStart(intent)) return true
                } catch (_: Exception) {
                    Toast.makeText(context, "Cannot open intent: $urlStr", Toast.LENGTH_SHORT).show()
                }
                uri.getQueryParameter("browser_fallback_url")?.let {
                    safeStart(Intent(Intent.ACTION_VIEW, it.toUri()))
                }
                true
            }
            else {
                val intent = when (scheme) {
                    "mailto" -> Intent(Intent.ACTION_SENDTO, uri)
                    "tel" -> Intent(Intent.ACTION_DIAL, uri)
                    "sms" -> Intent(Intent.ACTION_VIEW, uri)
                    "geo" -> Intent(Intent.ACTION_VIEW, uri)
                    else -> null
                }
                if (intent != null && safeStart(intent)) {
                    true
                } else {
                    openInCustomTab(context, urlStr)
                    true
                }
            }
        } catch (_: Exception) {
            Toast.makeText(context, "Cannot open: $urlStr", Toast.LENGTH_SHORT).show()
            true
        }
    }

    var pendingPermissionRequest by remember { mutableStateOf<PermissionRequest?>(null) }
    var pendingGeoCallback by remember { mutableStateOf<GeolocationPermissions.Callback?>(null) }
    var pendingGeoOrigin by remember { mutableStateOf<String?>(null) }
    val mediaPermissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { results ->
        val granted = results.values.all { it }
        pendingPermissionRequest?.let {
            if (granted) {
                it.grant(it.resources)
            } else {
                it.deny()
            }
            pendingPermissionRequest = null
        }
    }

    val geoPermissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { results ->
        val granted = results.values.all { it }
        pendingGeoCallback?.let {
            it.invoke(pendingGeoOrigin, granted, false)
            pendingGeoCallback = null
            pendingGeoOrigin = null
        }
    }

    var fileChooserCallback by remember { mutableStateOf<ValueCallback<Array<Uri>>?>(null) }
    val fileChooserLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        val data = result.data
        val resultCode = result.resultCode
        val results = WebChromeClient.FileChooserParams.parseResult(resultCode, data)
        fileChooserCallback?.onReceiveValue(results)
        fileChooserCallback = null
    }

    var customView by remember { mutableStateOf<View?>(null) }
    var customViewCallback by remember { mutableStateOf<WebChromeClient.CustomViewCallback?>(null) }
    val windowDecorView = remember { activity.window.decorView as FrameLayout }
    val windowInsetsController = remember { WindowCompat.getInsetsController(activity.window, windowDecorView) }

    AndroidView(
        factory = { context ->
            WebView(context).apply {

                layoutParams = ViewGroup.LayoutParams (
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT
                )

                val originalUA = settings.userAgentString
                val cleanedUA = originalUA
                    .replace(Regex("(Android \\d+)[^)]+"), "$1")
                    .replace(Regex("Version/\\d+\\.\\d+\\s?"), "")

                settings.apply {
                    javaScriptEnabled = true
                    domStorageEnabled = true
                    cacheMode = WebSettings.LOAD_DEFAULT
                    mixedContentMode = WebSettings.MIXED_CONTENT_ALWAYS_ALLOW
                    loadsImagesAutomatically = true
                    useWideViewPort = true
                    loadWithOverviewMode = true
                    mediaPlaybackRequiresUserGesture = false
                    javaScriptCanOpenWindowsAutomatically = true
                    blockNetworkLoads = false
                    allowFileAccess = true
                    allowContentAccess = true
                    builtInZoomControls = true
                    displayZoomControls = false
                    setGeolocationEnabled(true)
                    setSupportZoom(true)
                    setSupportMultipleWindows(true)
                    userAgentString = cleanedUA
                }

                webViewClient = object : WebViewClient() {
                    override fun onPageStarted(view: WebView?, url: String?, favicon: android.graphics.Bitmap?) {
                        super.onPageStarted(view, url, favicon)
                        onLoadingStateChange(true)
                        url?.let { onUrlChanged(it) }
                    }

                    override fun onPageFinished(view: WebView?, url: String?) {
                        super.onPageFinished(view, url)
                        onLoadingStateChange(false)
                    }

                    override fun shouldOverrideUrlLoading(view: WebView?, request: WebResourceRequest?): Boolean {
                        val uri = request?.url ?: return false
                        if (uri.scheme == "http" || uri.scheme == "https") {
                            return false
                        }
                        return handleExternalUri(uri)
                    }
                }

                webChromeClient = object : WebChromeClient() {
                    override fun onProgressChanged(view: WebView?, newProgress: Int) {
                        if (newProgress < 100) onLoadingStateChange(true)
                        else onLoadingStateChange(false)
                    }

                    override fun onPermissionRequest(request: PermissionRequest) {
                        val permissions = mutableListOf<String>()
                        request.resources.forEach {
                            when (it) {
                                PermissionRequest.RESOURCE_VIDEO_CAPTURE ->
                                    permissions.add(Manifest.permission.CAMERA)
                                PermissionRequest.RESOURCE_AUDIO_CAPTURE ->
                                    permissions.add(Manifest.permission.RECORD_AUDIO)
                            }
                        }
                        val missing = permissions.filter {
                            ContextCompat.checkSelfPermission(context, it) != PackageManager.PERMISSION_GRANTED
                        }
                        if (missing.isEmpty()) {
                            request.grant(request.resources)
                        } else {
                            pendingPermissionRequest = request
                            mediaPermissionLauncher.launch(missing.toTypedArray())
                        }
                    }

                    override fun onGeolocationPermissionsShowPrompt(origin: String, callback: GeolocationPermissions.Callback) {
                        if (ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
                            callback.invoke(origin, true, false)
                        } else {
                            pendingGeoOrigin = origin
                            pendingGeoCallback = callback
                            geoPermissionLauncher.launch(
                                arrayOf(
                                    Manifest.permission.ACCESS_FINE_LOCATION,
                                    Manifest.permission.ACCESS_COARSE_LOCATION
                                )
                            )
                        }
                    }

                    override fun onCreateWindow(view: WebView?, isDialog: Boolean, isUserGesture: Boolean, resultMsg: Message?): Boolean {
                        val transport = resultMsg?.obj as? WebView.WebViewTransport ?: return false
                        val tempWebView = WebView(view?.context ?: return false).apply {
                            webViewClient = object : WebViewClient() {
                                override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
                                    val uri = request.url
                                    if (uri.scheme == "http" || uri.scheme == "https") {
                                        openInCustomTab(context, uri.toString())
                                    } else {
                                        handleExternalUri(uri)
                                    }
                                    view.destroy()
                                    return true
                                }
                            }
                        }
                        transport.webView = tempWebView
                        resultMsg.sendToTarget()
                        return true
                    }

                    override fun onShowFileChooser(webView: WebView?, filePathCallback: ValueCallback<Array<Uri>>?, fileChooserParams: FileChooserParams?): Boolean {
                        fileChooserCallback = filePathCallback
                        val contentSelectionIntent = Intent(Intent.ACTION_GET_CONTENT).apply {
                            addCategory(Intent.CATEGORY_OPENABLE)
                            type = "*/*"
                        }
                        val chooserIntent = Intent(Intent.ACTION_CHOOSER).apply {
                            putExtra(Intent.EXTRA_INTENT, contentSelectionIntent)
                            putExtra(Intent.EXTRA_TITLE, "Choose file...")
                        }
                        try {
                            fileChooserLauncher.launch(chooserIntent)
                        } catch (_: Exception) {
                            fileChooserCallback = null
                            return false
                        }
                        return true
                    }

                    override fun onShowCustomView(view: View, callback: CustomViewCallback) {
                        if (customView != null) {
                            callback.onCustomViewHidden()
                            return
                        }
                        customView = view
                        customViewCallback = callback
                        windowInsetsController.hide(WindowInsetsCompat.Type.systemBars())
                        windowInsetsController.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
                        windowDecorView.addView(view, FrameLayout.LayoutParams(
                            FrameLayout.LayoutParams.MATCH_PARENT,
                            FrameLayout.LayoutParams.MATCH_PARENT
                        ))
                        onCustomViewToggle(true)
                    }

                    override fun onHideCustomView() {
                        customView?.let {
                            windowDecorView.removeView(it)
                        }
                        customView = null
                        customViewCallback?.onCustomViewHidden()
                        windowInsetsController.show(WindowInsetsCompat.Type.systemBars())
                        onCustomViewToggle(false)
                    }
                }

                CookieManager.getInstance().setAcceptCookie(true)
                CookieManager.getInstance().setAcceptThirdPartyCookies(this, true)

                setDownloadListener { url, userAgent, contentDisposition, mimetype, _ ->
                    when {
                        url.startsWith("blob") -> {
                            Toast.makeText(context, "Download not supported for blob URLs", Toast.LENGTH_LONG).show()
                        }
                        url.startsWith("http://") || url.startsWith("https://") -> {
                            try {
                                val fileName = URLUtil.guessFileName(url, contentDisposition, mimetype)
                                val request = DownloadManager.Request(url.toUri()).apply {
                                    setMimeType(mimetype)
                                    addRequestHeader("User-Agent", userAgent)
                                    CookieManager.getInstance().getCookie(url)?.let {
                                        addRequestHeader("cookie", it)
                                    }
                                    setTitle(fileName)
                                    setDescription("Downloading file...")
                                    setNotificationVisibility(
                                        DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED
                                    )
                                    setDestinationInExternalPublicDir(
                                        Environment.DIRECTORY_DOWNLOADS,
                                        fileName
                                    )
                                }
                                val dm = context.getSystemService(DOWNLOAD_SERVICE) as DownloadManager
                                dm.enqueue(request)
                                Toast.makeText(context, "Downloading file...", Toast.LENGTH_LONG).show()
                            } catch (e: Exception) {
                                Toast.makeText(
                                    context,
                                    "Error downloading file: ${e.message}",
                                    Toast.LENGTH_LONG
                                ).show()
                            }
                        }
                        else -> {
                            Toast.makeText(context, "Unsupported download scheme", Toast.LENGTH_LONG).show()
                        }
                    }
                }

                onWebViewCreated(this)
                loadUrl(initialUrl)
            }
        },
        modifier = Modifier.fillMaxSize()
    )
}