package com.example.sms

import android.Manifest
import android.app.role.RoleManager
import android.content.Intent
import android.database.ContentObserver
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.Telephony
import android.view.View
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import kotlinx.coroutines.delay

/** Increments whenever the SMS database or the blacklist changes; screens reload on it. */
val LocalVersion = compositionLocalOf { 0 }

sealed interface Screen {
    data object List : Screen
    data class Chat(val address: String, val draft: String? = null) : Screen
    data object New : Screen
    data object Blacklist : Screen
    data object Settings : Screen
}

class MainActivity : ComponentActivity() {
    companion object {
        /** Address of the chat on screen right now — no notification for it. */
        @Volatile var openAddress: String? = null
    }

    private lateinit var prefs: Prefs
    private val stack = mutableStateListOf<Screen>(Screen.List)
    private var version by mutableIntStateOf(0)
    private var isDefault by mutableStateOf(false)
    /** The system role dialog didn't help (some firmwares, e.g. MIUI/HyperOS, block it) — offer Settings. */
    private var roleFailed by mutableStateOf(false)
    private var now by mutableLongStateOf(System.currentTimeMillis())
    private val bump: () -> Unit = { runOnUiThread { version++ } }
    private val observer = object : ContentObserver(Handler(Looper.getMainLooper())) {
        override fun onChange(selfChange: Boolean) { version++ }
    }

    private val roleLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        isDefault = Sms.isDefault(this)
        if (!isDefault) roleFailed = true
        version++
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        prefs = Prefs(this)
        Notify.ensureChannel(this)
        isDefault = Sms.isDefault(this)
        handle(intent)
        SmsEvents.listen(bump)
        runCatching { contentResolver.registerContentObserver(Telephony.Sms.CONTENT_URI, true, observer) }

        setContent { App() }
        Updater.autoCheck(this)
        requestMaxRefreshRate()
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handle(intent)
    }

    override fun onResume() {
        super.onResume()
        requestMaxRefreshRate()
        isDefault = Sms.isDefault(this)
        now = System.currentTimeMillis()
        Sms.clearNames()
        version++
    }

    override fun onDestroy() {
        SmsEvents.unlisten(bump)
        runCatching { contentResolver.unregisterContentObserver(observer) }
        super.onDestroy()
    }

    /** Opens a chat from a notification or from an "sms:" link of another app. */
    private fun handle(intent: Intent?) {
        intent ?: return
        val fromNotification = intent.getStringExtra(Notify.EXTRA_ADDRESS)
        val fromLink = when (intent.action) {
            Intent.ACTION_SENDTO, Intent.ACTION_VIEW -> intent.data?.let { recipientFrom(it) }
            else -> null
        }
        val address = fromNotification ?: fromLink ?: return
        val draft = intent.getStringExtra("sms_body") ?: intent.getStringExtra(Intent.EXTRA_TEXT)
        stack.clear()
        stack.add(Screen.List)
        stack.add(Screen.Chat(address, draft))
    }

    /**
     * Asks the display for its highest refresh rate (90/120/144 Hz) at the current resolution.
     * Without this many firmwares (Honor, Xiaomi) keep third-party apps at 60 Hz.
     */
    private fun requestMaxRefreshRate() {
        val d = display ?: return
        val cur = d.mode
        val best = d.supportedModes
            .filter { it.physicalWidth == cur.physicalWidth && it.physicalHeight == cur.physicalHeight }
            .maxByOrNull { it.refreshRate } ?: return
        window.attributes = window.attributes.also {
            it.preferredDisplayModeId = best.modeId
            it.preferredRefreshRate = best.refreshRate
        }
        if (Build.VERSION.SDK_INT >= 35) {
            // Don't let the system drop to 60 Hz to save power while our animations run
            window.setFrameRatePowerSavingsBalanced(false)
            window.decorView.requestedFrameRate = View.REQUESTED_FRAME_RATE_CATEGORY_HIGH
        }
    }

    private fun requestRole() {
        if (Sms.isDefault(this)) { isDefault = true; return }
        val rm = getSystemService(RoleManager::class.java)
        if (!roleFailed && rm != null && rm.isRoleAvailable(RoleManager.ROLE_SMS)) {
            runCatching { roleLauncher.launch(rm.createRequestRoleIntent(RoleManager.ROLE_SMS)) }
                .onSuccess { return }
        }
        roleFailed = true
        openDefaultAppsSettings()
    }

    /** Settings → Default apps, where the user can pick the SMS app by hand. */
    private fun openDefaultAppsSettings() {
        val intents = listOf(
            Intent(android.provider.Settings.ACTION_MANAGE_DEFAULT_APPS_SETTINGS),
            Intent(android.provider.Settings.ACTION_SETTINGS)
        )
        for (i in intents) {
            if (runCatching { startActivity(i) }.isSuccess) return
        }
    }

    @Composable
    private fun App() {
        // ---- theme state ----
        var mode by remember { mutableStateOf(prefs.mode) }
        var bubbleDark by remember { mutableIntStateOf(prefs.bubble(true)) }
        var bubbleLight by remember { mutableIntStateOf(prefs.bubble(false)) }
        var geo by remember { mutableStateOf(prefs.lat to prefs.lon) }
        var hasGeo by remember { mutableStateOf(Geo.granted(this)) }

        // Re-check sunrise/sunset every minute
        LaunchedEffect(Unit) {
            while (true) {
                delay(60_000)
                now = System.currentTimeMillis()
            }
        }

        val refreshGeo = { Geo.refresh(this, prefs) { geo = prefs.lat to prefs.lon } }

        val perms = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
            hasGeo = Geo.granted(this)
            if (hasGeo) refreshGeo()
            Sms.clearNames()
            version++
        }
        val geoPerm = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
            hasGeo = it
            if (it) refreshGeo()
        }

        // After becoming default: ask for contacts, notifications and location once
        LaunchedEffect(isDefault) {
            if (isDefault) {
                perms.launch(
                    arrayOf(
                        Manifest.permission.READ_CONTACTS,
                        Manifest.permission.POST_NOTIFICATIONS,
                        Manifest.permission.ACCESS_COARSE_LOCATION
                    )
                )
            }
        }
        LaunchedEffect(hasGeo) { if (hasGeo) refreshGeo() }

        val night = Sun.isNight(geo.first, geo.second, now)
        val dark = when (mode) {
            ThemeMode.DARK -> true
            ThemeMode.LIGHT -> false
            ThemeMode.AUTO -> night
        }
        val target = if (dark) darkColors(Color(bubbleDark)) else lightColors(Color(bubbleLight))
        val colors = animated(target)

        // Status / navigation bar icons follow the theme
        LaunchedEffect(dark) {
            val style = if (dark) SystemBarStyle.dark(android.graphics.Color.TRANSPARENT)
            else SystemBarStyle.light(android.graphics.Color.TRANSPARENT, android.graphics.Color.TRANSPARENT)
            enableEdgeToEdge(statusBarStyle = style, navigationBarStyle = style)
        }

        val current = stack.last()
        DisposableEffect(current) {
            openAddress = (current as? Screen.Chat)?.address
            onDispose { openAddress = null }
        }

        CompositionLocalProvider(LocalColors provides colors, LocalVersion provides version) {
            Box(Modifier.fillMaxSize().background(colors.bg)) {
                if (!isDefault) {
                    WelcomeScreen(roleFailed, onMakeDefault = ::requestRole)
                    return@Box
                }
                BackHandler(enabled = stack.size > 1) { stack.removeAt(stack.lastIndex) }
                val depth = stack.size
                AnimatedContent(
                    targetState = current to depth,
                    transitionSpec = {
                        val forward = targetState.second >= initialState.second
                        val dur = 420
                        if (forward) {
                            (slideInHorizontally(tween(dur)) { it / 3 } + fadeIn(tween(dur)) + scaleIn(tween(dur), 0.96f)) togetherWith
                                (slideOutHorizontally(tween(dur)) { -it / 6 } + fadeOut(tween(dur / 2)) + scaleOut(tween(dur), 0.98f))
                        } else {
                            (slideInHorizontally(tween(dur)) { -it / 6 } + fadeIn(tween(dur)) + scaleIn(tween(dur), 0.98f)) togetherWith
                                (slideOutHorizontally(tween(dur)) { it / 3 } + fadeOut(tween(dur / 2)) + scaleOut(tween(dur), 0.96f))
                        }
                    },
                    label = "nav"
                ) { (screen, _) ->
                    when (screen) {
                        Screen.List -> ConversationsScreen(
                            onOpen = { stack.add(Screen.Chat(it)) },
                            onNew = { stack.add(Screen.New) },
                            onBlacklist = { stack.add(Screen.Blacklist) },
                            onSettings = { stack.add(Screen.Settings) }
                        )
                        is Screen.Chat -> ChatScreen(screen.address, screen.draft, onBack = { pop() })
                        Screen.New -> NewMessageScreen(
                            onBack = { pop() },
                            onPick = { stack[stack.lastIndex] = Screen.Chat(it) }
                        )
                        Screen.Blacklist -> BlacklistScreen(onBack = { pop() })
                        Screen.Settings -> SettingsScreen(
                            mode = mode,
                            onMode = { mode = it; prefs.mode = it },
                            bubbleDark = Color(bubbleDark),
                            bubbleLight = Color(bubbleLight),
                            onBubble = { d, col ->
                                if (d) bubbleDark = col.toArgb() else bubbleLight = col.toArgb()
                                prefs.setBubble(d, col.toArgb())
                            },
                            lat = geo.first,
                            lon = geo.second,
                            hasGeo = hasGeo,
                            onRequestGeo = {
                                if (hasGeo) refreshGeo() else geoPerm.launch(Manifest.permission.ACCESS_COARSE_LOCATION)
                            },
                            onBack = { pop() }
                        )
                    }
                }
            }
        }
    }

    private fun pop() {
        if (stack.size > 1) stack.removeAt(stack.lastIndex)
    }
}
