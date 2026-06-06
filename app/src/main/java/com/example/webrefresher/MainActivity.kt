package com.example.webrefresher

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.provider.Settings as AndroidSettings
import android.view.ViewGroup
import android.webkit.WebView
import android.webkit.WebViewClient
import android.content.BroadcastReceiver
import android.os.Bundle
import android.content.IntentFilter
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Info
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import kotlinx.coroutines.delay
import kotlin.math.roundToInt
import kotlin.random.Random

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            val isDark = isSystemInDarkTheme()
            MaterialTheme(
                colorScheme = if (isDark) darkColorScheme(
                    background = Color.Black,
                    surface = Color.Black
                ) else lightColorScheme(
                    background = Color.White,
                    surface = Color.White
                )
            ) {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    GlassmorphicRefresherScreen()
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RefreshTooltip(text: String, content: @Composable () -> Unit) {
    TooltipBox(
        positionProvider = TooltipDefaults.rememberPlainTooltipPositionProvider(),
        tooltip = {
            PlainTooltip {
                Text(text)
            }
        },
        state = rememberTooltipState()
    ) {
        content()
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GlassmorphicRefresherScreen() {
    val context = LocalContext.current
    val prefs = remember { context.getSharedPreferences("refresher_prefs", Context.MODE_PRIVATE) }
    
    var urlInput by remember { mutableStateOf("") }
    var isUrlLoaded by remember { mutableStateOf(false) }
    var isRunning by remember { mutableStateOf(false) }
    var webViewInstance by remember { mutableStateOf<WebView?>(null) }

    val intervals = listOf(60, 180, 300)
    var selectedInterval by remember { mutableStateOf(intervals[0]) }
    var customRefreshInput by remember { mutableStateOf("") }
    var autoStopInput by remember { mutableStateOf("60") }
    var totalDurationLeft by remember { mutableStateOf(3600L) }
    var currentIntervalTimeLeft by remember { mutableStateOf(selectedInterval) }
    var isSafetyEnabled by remember { mutableStateOf(false) }

    // Unified Timer and Broadcast Listener
    DisposableEffect(context) {
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent?) {
                Log.d("MainActivity", "Received broadcast: ${intent?.action}")
                when (intent?.action) {
                    "com.example.webrefresher.STOP_AUTOMATION" -> {
                        isRunning = false
                    }
                    "com.example.webrefresher.REFRESH_WEBVIEW" -> {
                        webViewInstance?.let { view ->
                            Log.d("MainActivity", "Reloading WebView")
                            // Force resume and reload
                            view.onResume()
                            view.reload()
                            
                            // Force wake the screen for a moment
                            context?.let { ctx ->
                                val pm = ctx.getSystemService(Context.POWER_SERVICE) as PowerManager
                                val wl = pm.newWakeLock(PowerManager.SCREEN_BRIGHT_WAKE_LOCK or PowerManager.ACQUIRE_CAUSES_WAKEUP, "WebRefresher::TemporaryWake")
                                wl.acquire(3000L)
                            }
                        } ?: Log.e("MainActivity", "WebView instance is null, cannot reload")
                    }
                }
            }
        }
        val filter = IntentFilter().apply {
            addAction("com.example.webrefresher.STOP_AUTOMATION")
            addAction("com.example.webrefresher.REFRESH_WEBVIEW")
        }
        
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            context.registerReceiver(receiver, filter, Context.RECEIVER_EXPORTED)
        } else {
            context.registerReceiver(receiver, filter)
        }
        onDispose {
            context.unregisterReceiver(receiver)
        }
    }

    // Local Timer Loop for UI (only active when app is open)
    LaunchedEffect(isRunning) {
        if (!isRunning) return@LaunchedEffect
        while (isRunning) {
            val now = System.currentTimeMillis()
            val endTime = prefs.getLong("end_time", 0L)
            val remaining = (endTime - now) / 1000L
            
            if (remaining <= 0) {
                isRunning = false
                break
            }
            
            totalDurationLeft = remaining
            
            val startTime = prefs.getLong("start_time", now)
            val elapsedSinceStart = (now - startTime) / 1000L
            currentIntervalTimeLeft = (selectedInterval - (elapsedSinceStart % selectedInterval)).toInt()
            
            delay(1000L)
        }
    }

    // Permission Requests
    val powerManager = context.getSystemService(Context.POWER_SERVICE) as PowerManager
    val notificationPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { _ -> }

    LaunchedEffect(Unit) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            notificationPermissionLauncher.launch(android.Manifest.permission.POST_NOTIFICATIONS)
        }
        if (!powerManager.isIgnoringBatteryOptimizations(context.packageName)) {
            val intent = Intent(AndroidSettings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
                data = Uri.parse("package:${context.packageName}")
            }
            context.startActivity(intent)
        }
    }
    
    val isDark = isSystemInDarkTheme()
    val solidPanelColor = if (isDark) Color.Black else Color.White
    val borderColor = if (isDark) Color.White.copy(alpha = 0.1f) else Color.Black.copy(alpha = 0.1f)
    val textColor = if (isDark) Color.White else Color.Black
    val accentColor = Color(0xFF007AFF)

    var urlHistory by rememberSaveable { mutableStateOf(emptySet<String>()) }
    
    // Restore and Persist History/Session
    LaunchedEffect(Unit) {
        val savedHistory = prefs.getStringSet("url_history", emptySet()) ?: emptySet()
        urlHistory = savedHistory.toSet()

        val wasRunning = prefs.getBoolean("is_running", false)
        if (wasRunning) {
            val savedUrl = prefs.getString("last_url", "") ?: ""
            val savedInterval = prefs.getInt("interval", 60)
            val savedAutoStop = prefs.getString("auto_stop", "60") ?: "60"
            val endTime = prefs.getLong("end_time", 0L)
            val remaining = (endTime - System.currentTimeMillis()) / 1000L
            
            if (remaining > 0 && savedUrl.isNotEmpty()) {
                urlInput = savedUrl
                selectedInterval = savedInterval
                autoStopInput = savedAutoStop
                isSafetyEnabled = prefs.getBoolean("safety_enabled", false)
                totalDurationLeft = remaining
                isUrlLoaded = true
                isRunning = true
                currentIntervalTimeLeft = selectedInterval
            } else {
                prefs.edit().putBoolean("is_running", false).apply()
            }
        }
    }

    // Persist session state when it changes
    LaunchedEffect(isRunning, urlInput, selectedInterval, isSafetyEnabled) {
        if (isRunning) {
            prefs.edit().apply {
                putBoolean("is_running", true)
                putString("last_url", urlInput)
                putInt("interval", selectedInterval)
                putString("auto_stop", autoStopInput)
                putBoolean("safety_enabled", isSafetyEnabled)
                apply()
            }
        } else {
            prefs.edit().putBoolean("is_running", false).apply()
        }
    }

    // Start/Stop Service based on running state
    LaunchedEffect(isRunning) {
        if (isRunning) {
            val serviceIntent = Intent(context, RefreshService::class.java).apply {
                putExtra("interval", selectedInterval)
                putExtra("total_seconds", totalDurationLeft)
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(serviceIntent)
            } else {
                context.startService(serviceIntent)
            }
        } else {
            context.stopService(Intent(context, RefreshService::class.java))
        }
    }

    var isDesktopMode by remember { mutableStateOf(true) }
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var showBottomSheet by remember { mutableStateOf(false) }

    var offsetX by remember { mutableStateOf(-1f) }
    var offsetY by remember { mutableStateOf(-1f) }
    var parentSize by remember { mutableStateOf(IntSize.Zero) }

    LaunchedEffect(parentSize) {
        if (parentSize != IntSize.Zero && offsetX == -1f) {
            offsetX = parentSize.width.toFloat() - 250f
            offsetY = parentSize.height.toFloat() - 400f
        }
    }

    LaunchedEffect(isDesktopMode) {
        webViewInstance?.settings?.userAgentString = if (isDesktopMode) {
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/122.0.0.0 Safari/537.36"
        } else {
            null
        }
        if (isUrlLoaded) {
            webViewInstance?.reload()
        }
    }

    Box(modifier = Modifier
        .fillMaxSize()
        .onGloballyPositioned { parentSize = it.size }
    ) {
        if (!isUrlLoaded) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(if (isDark) Color.Black else Color(0xFFF2F2F7))
                    .windowInsetsPadding(WindowInsets.safeDrawing),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    modifier = Modifier.fillMaxWidth(0.9f).clip(RoundedCornerShape(32.dp)).background(solidPanelColor).border(1.dp, borderColor, RoundedCornerShape(32.dp)).padding(32.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text("Web Refresher Pro", style = MaterialTheme.typography.headlineMedium.copy(color = textColor, fontWeight = FontWeight.Bold, fontSize = 26.sp))
                    Spacer(modifier = Modifier.height(32.dp))
                    
                    var expanded by remember { mutableStateOf(false) }
                    ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { expanded = !expanded }, modifier = Modifier.fillMaxWidth()) {
                        OutlinedTextField(
                            value = urlInput,
                            onValueChange = { urlInput = it },
                            placeholder = { Text("Enter URL", color = textColor.copy(alpha = 0.4f)) },
                            singleLine = true,
                            shape = RoundedCornerShape(16.dp),
                            modifier = Modifier.menuAnchor().fillMaxWidth(),
                            trailingIcon = {
                                IconButton(
                                    onClick = { 
                                        if (urlInput.isNotBlank()) {
                                            isUrlLoaded = true 
                                            val newList = (urlHistory.toList() + urlInput).distinct().takeLast(10)
                                            urlHistory = newList.toSet()
                                            prefs.edit().putStringSet("url_history", urlHistory).apply()
                                        }
                                    },
                                    modifier = Modifier.size(40.dp).clip(RoundedCornerShape(12.dp)).background(accentColor)
                                ) {
                                    Icon(Icons.Default.PlayArrow, contentDescription = "Go", tint = Color.White)
                                }
                            },
                            colors = OutlinedTextFieldDefaults.colors(focusedTextColor = textColor, unfocusedTextColor = textColor, focusedBorderColor = accentColor, unfocusedBorderColor = borderColor, focusedContainerColor = Color.Transparent, unfocusedContainerColor = Color.Transparent)
                        )
                        if (urlHistory.isNotEmpty()) {
                            ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }, modifier = Modifier.background(solidPanelColor)) {
                                urlHistory.toList().reversed().forEach { historyUrl ->
                                    DropdownMenuItem(text = { Text(historyUrl, color = textColor) }, onClick = { urlInput = historyUrl; expanded = false }, contentPadding = ExposedDropdownMenuDefaults.ItemContentPadding)
                                }
                            }
                        }
                    }
                    
                    if (urlHistory.isNotEmpty()) {
                        Spacer(modifier = Modifier.height(24.dp))
                        Text("Recents", fontSize = 13.sp, color = textColor.copy(alpha = 0.5f), fontWeight = FontWeight.SemiBold, modifier = Modifier.align(Alignment.Start))
                        Spacer(modifier = Modifier.height(12.dp))
                        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                            items(urlHistory.toList().reversed()) { historyUrl ->
                                SuggestionChip(onClick = { urlInput = historyUrl }, label = { Text(historyUrl, maxLines = 1, fontSize = 11.sp, color = textColor) }, shape = RoundedCornerShape(12.dp), colors = SuggestionChipDefaults.suggestionChipColors(containerColor = textColor.copy(alpha = 0.05f)), border = null)
                            }
                        }
                    }
                    
                    Spacer(modifier = Modifier.height(32.dp))
                    Text(
                        "Designed by Bunny", 
                        fontSize = 12.sp, 
                        color = textColor.copy(alpha = 0.3f),
                        fontWeight = FontWeight.Medium
                    )
                }
            }
        } else {
            Box(modifier = Modifier.fillMaxSize().background(solidPanelColor).windowInsetsPadding(WindowInsets.safeDrawing)) {
                AndroidView(
                    factory = { context ->
                        WebView(context).apply {
                            layoutParams = ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
                            webViewClient = WebViewClient()
                            settings.apply {
                                javaScriptEnabled = true
                                domStorageEnabled = true
                                loadWithOverviewMode = true
                                useWideViewPort = true
                                builtInZoomControls = true
                                displayZoomControls = false
                                if (isDesktopMode) {
                                    userAgentString = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/122.0.0.0 Safari/537.36"
                                }
                            }
                            loadUrl(urlInput)
                            webViewInstance = this
                        }
                    },
                    update = { view ->
                        // Prevent the OS from pausing the webview when the app is minimized
                        // as long as automation is running.
                        if (isRunning) {
                            view.onResume()
                        }
                    },
                    modifier = Modifier.fillMaxSize()
                )
            }

            var elementSize by remember { mutableStateOf(IntSize.Zero) }
            val density = LocalDensity.current
            val safeInsets = WindowInsets.safeDrawing
            val topInset = with(density) { safeInsets.getTop(this).toFloat() }
            val bottomInset = with(density) { safeInsets.getBottom(this).toFloat() }
            val leftInset = with(density) { safeInsets.getLeft(this, LayoutDirection.Ltr).toFloat() }
            val rightInset = with(density) { safeInsets.getRight(this, LayoutDirection.Ltr).toFloat() }

            LaunchedEffect(elementSize, parentSize) {
                if (elementSize != IntSize.Zero && parentSize != IntSize.Zero) {
                    val minX = leftInset
                    val minY = topInset
                    val maxX = (parentSize.width - elementSize.width).toFloat() - rightInset
                    val maxY = (parentSize.height - elementSize.height).toFloat() - bottomInset
                    
                    offsetX = offsetX.coerceIn(minX, maxOf(minX, maxX))
                    offsetY = offsetY.coerceIn(minY, maxOf(minY, maxY))
                }
            }
            
            Box(
                modifier = Modifier.onGloballyPositioned { elementSize = it.size }.offset { IntOffset(offsetX.roundToInt(), offsetY.roundToInt()) }.pointerInput(parentSize) {
                    detectDragGestures { change, dragAmount ->
                        change.consume()
                        val minX = leftInset
                        val minY = topInset
                        val maxX = (parentSize.width - elementSize.width).toFloat() - rightInset
                        val maxY = (parentSize.height - elementSize.height).toFloat() - bottomInset
                        
                        offsetX = (offsetX + dragAmount.x).coerceIn(minX, maxOf(minX, maxX))
                        offsetY = (offsetY + dragAmount.y).coerceIn(minY, maxOf(minY, maxY))
                    }
                }.padding(16.dp)
            ) {
                if (isRunning && !showBottomSheet) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.height(52.dp).clip(CircleShape).background(solidPanelColor).border(1.dp, borderColor, CircleShape).clickable { showBottomSheet = true }.padding(horizontal = 16.dp)
                    ) {
                        val minutes = (totalDurationLeft / 60).toString().padStart(2, '0')
                        val seconds = (totalDurationLeft % 60).toString().padStart(2, '0')
                        Text(text = "Remaining: $minutes:$seconds", color = textColor, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
                        Spacer(modifier = Modifier.width(12.dp))
                        Icon(Icons.Default.Settings, null, tint = textColor.copy(alpha = 0.6f), modifier = Modifier.size(18.dp))
                    }
                } else {
                    FloatingActionButton(onClick = { showBottomSheet = true }, containerColor = solidPanelColor, contentColor = textColor, shape = CircleShape, modifier = Modifier.size(56.dp).border(1.dp, borderColor, CircleShape), elevation = FloatingActionButtonDefaults.elevation(0.dp, 0.dp, 0.dp, 0.dp)) {
                        Icon(Icons.Default.Settings, contentDescription = "Settings")
                    }
                }
            }
        }

        if (showBottomSheet) {
            ModalBottomSheet(onDismissRequest = { showBottomSheet = false }, sheetState = sheetState, containerColor = solidPanelColor, scrimColor = Color.Black.copy(alpha = 0.32f), shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp) ) {
                Column(modifier = Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = 24.dp).padding(bottom = 64.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(24.dp)) {
                    Text("Automation Settings", style = MaterialTheme.typography.titleLarge.copy(color = textColor, fontWeight = FontWeight.Bold))
                    Column(modifier = Modifier.fillMaxWidth()) {
                        Text("Target URL", fontSize = 12.sp, color = textColor.copy(alpha = 0.5f), fontWeight = FontWeight.Bold)
                        Spacer(modifier = Modifier.height(8.dp))
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            OutlinedTextField(value = urlInput, onValueChange = { urlInput = it }, singleLine = true, modifier = Modifier.weight(1f), shape = RoundedCornerShape(12.dp), colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = accentColor, unfocusedBorderColor = borderColor, focusedContainerColor = textColor.copy(alpha = 0.05f), unfocusedContainerColor = textColor.copy(alpha = 0.05f)))
                            IconButton(onClick = { if (urlInput.isNotBlank()) { webViewInstance?.loadUrl(urlInput); val newList = (urlHistory.toList() + urlInput).distinct().takeLast(10); urlHistory = newList.toSet(); prefs.edit().putStringSet("url_history", urlHistory).apply() } }, modifier = Modifier.size(52.dp).clip(RoundedCornerShape(12.dp)).background(accentColor)) {
                                Icon(Icons.Default.Refresh, null, tint = Color.White)
                            }
                        }
                    }
                    Column(modifier = Modifier.fillMaxWidth()) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text("Interval", fontSize = 12.sp, color = textColor.copy(alpha = 0.5f), fontWeight = FontWeight.Bold)
                            RefreshTooltip("Select how often the website should reload automatically.") {
                                Icon(Icons.Default.Info, null, tint = textColor.copy(alpha = 0.3f), modifier = Modifier.size(14.dp))
                            }
                        }
                        Spacer(modifier = Modifier.height(12.dp))
                        Row(modifier = Modifier.fillMaxWidth().background(textColor.copy(alpha = 0.05f), CircleShape).padding(4.dp)) {
                            intervals.forEach { sec ->
                                val isSelected = selectedInterval == sec
                                Box(modifier = Modifier.weight(1f).clip(CircleShape).background(if (isSelected) (if(isDark) Color.White.copy(alpha = 0.2f) else Color.White) else Color.Transparent).clickable { selectedInterval = sec; currentIntervalTimeLeft = sec; customRefreshInput = "" }.padding(vertical = 8.dp), contentAlignment = Alignment.Center) {
                                    Text("${sec / 60}m", fontSize = 14.sp, color = if (isSelected) textColor else textColor.copy(alpha = 0.5f), fontWeight = if(isSelected) FontWeight.Bold else FontWeight.Normal)
                                }
                            }
                            // Custom Option Button
                            val isCustomSelected = selectedInterval !in intervals
                            Box(modifier = Modifier.weight(1f).clip(CircleShape).background(if (isCustomSelected) (if(isDark) Color.White.copy(alpha = 0.2f) else Color.White) else Color.Transparent).clickable { if (customRefreshInput.isEmpty()) customRefreshInput = (selectedInterval / 60).toString() }.padding(vertical = 8.dp), contentAlignment = Alignment.Center) {
                                Text("Custom", fontSize = 14.sp, color = if (isCustomSelected) textColor else textColor.copy(alpha = 0.5f), fontWeight = if(isCustomSelected) FontWeight.Bold else FontWeight.Normal)
                            }
                        }
                        
                        AnimatedVisibility(visible = selectedInterval !in intervals || customRefreshInput.isNotEmpty()) {
                            Column {
                                Spacer(modifier = Modifier.height(12.dp))
                                OutlinedTextField(
                                    value = customRefreshInput,
                                    onValueChange = { 
                                        customRefreshInput = it
                                        val mins = it.toIntOrNull() ?: 0
                                        if (mins > 0) {
                                            selectedInterval = mins * 60
                                            currentIntervalTimeLeft = selectedInterval
                                        }
                                    },
                                    label = { Text("Refresh every (mins)") },
                                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                    modifier = Modifier.fillMaxWidth(),
                                    shape = RoundedCornerShape(12.dp),
                                    colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = accentColor, unfocusedBorderColor = borderColor)
                                )
                            }
                        }
                    }
                    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text("Stop after (mins)", fontSize = 12.sp, color = textColor.copy(alpha = 0.5f), fontWeight = FontWeight.Bold)
                        RefreshTooltip("Total duration for the automation session. App stops after this time.") {
                            Icon(Icons.Default.Info, null, tint = textColor.copy(alpha = 0.3f), modifier = Modifier.size(14.dp))
                        }
                    }
                    OutlinedTextField(value = autoStopInput, onValueChange = { autoStopInput = it; totalDurationLeft = (it.toLongOrNull() ?: 0L) * 60L }, singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp), colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = accentColor, unfocusedBorderColor = borderColor))
                    
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                        Column(modifier = Modifier.weight(1f)) {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                Text("Safety Mode", color = textColor, fontWeight = FontWeight.Medium)
                                RefreshTooltip("Anti-detection: Adds a random delay (1-30s) to each refresh so it's not exactly periodic.") {
                                    Icon(Icons.Default.Info, null, tint = textColor.copy(alpha = 0.3f), modifier = Modifier.size(14.dp))
                                }
                            }
                            Text("Adds random 1-30s delay to each refresh", fontSize = 11.sp, color = textColor.copy(alpha = 0.5f))
                        }
                        Switch(checked = isSafetyEnabled, onCheckedChange = { isSafetyEnabled = it }, colors = SwitchDefaults.colors(checkedTrackColor = accentColor))
                    }

                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text("Desktop Mode", color = textColor, fontWeight = FontWeight.Medium)
                            RefreshTooltip("Forces the website to load the Desktop version instead of Mobile.") {
                                Icon(Icons.Default.Info, null, tint = textColor.copy(alpha = 0.3f), modifier = Modifier.size(14.dp))
                            }
                        }
                        Switch(checked = isDesktopMode, onCheckedChange = { isDesktopMode = it }, colors = SwitchDefaults.colors(checkedTrackColor = accentColor))
                    }
                    Button(
                        onClick = { 
                            if (!isRunning && urlInput.isNotBlank()) { 
                                val newList = (urlHistory.toList() + urlInput).distinct().takeLast(10)
                                urlHistory = newList.toSet()
                                prefs.edit().putStringSet("url_history", urlHistory).apply()
                                
                                // Set official session end time before starting
                                val now = System.currentTimeMillis()
                                val durationMins = autoStopInput.toLongOrNull() ?: 60L
                                val endTime = now + (durationMins * 60L * 1000L)
                                
                                prefs.edit().apply {
                                    putLong("start_time", now)
                                    putLong("end_time", endTime)
                                    putInt("interval", selectedInterval)
                                    putString("auto_stop", autoStopInput)
                                    putString("last_url", urlInput)
                                    putBoolean("safety_enabled", isSafetyEnabled)
                                    apply()
                                }
                                totalDurationLeft = durationMins * 60L
                            }
                            isRunning = !isRunning
                            showBottomSheet = false 
                        }, 
                        modifier = Modifier.fillMaxWidth(0.95f).height(56.dp), 
                        shape = RoundedCornerShape(16.dp), 
                        colors = ButtonDefaults.buttonColors(containerColor = if (isRunning) Color(0xFFFF3B30) else accentColor)
                    ) {
                        Text(if (isRunning) "Stop Automation" else "Start Automation", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                    }
                }
            }
        }
    }
}
