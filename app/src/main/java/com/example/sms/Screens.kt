package com.example.sms

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Intent
import android.net.Uri
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Create
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kyant.backdrop.Backdrop
import com.kyant.backdrop.backdrops.layerBackdrop
import com.kyant.backdrop.backdrops.rememberLayerBackdrop
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

// =====================================================================
// Welcome — become the default SMS app
// =====================================================================

@Composable
fun WelcomeScreen(roleFailed: Boolean, onMakeDefault: () -> Unit) {
    val c = LocalColors.current
    val backdrop = rememberLayerBackdrop()
    Box(Modifier.fillMaxSize()) {
        LivingBackground(Modifier.layerBackdrop(backdrop))
        Column(
            Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding().padding(24.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Column(
                Modifier.fillMaxWidth().themedGlass(backdrop, Card).padding(26.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text("Polaris SMS", color = c.text, fontSize = 30.sp, fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.height(10.dp))
                Text(
                    "Чтобы читать, отправлять SMS и вести чёрный список, сделайте приложение основным для сообщений.",
                    color = c.dim, fontSize = 15.sp, textAlign = TextAlign.Center
                )
                AnimatedVisibility(roleFailed) {
                    Text(
                        "Система не показала выбор. Откройте «Приложения по умолчанию» → «SMS» и выберите Polaris SMS, затем вернитесь сюда.",
                        color = c.text, fontSize = 14.sp, textAlign = TextAlign.Center,
                        modifier = Modifier.padding(top = 14.dp)
                    )
                }
                Spacer(Modifier.height(22.dp))
                GlassButton(
                    backdrop, if (roleFailed) "Открыть настройки" else "Сделать основным",
                    Modifier.fillMaxWidth(), accent = true, onClick = onMakeDefault
                )
            }
        }
    }
}

// =====================================================================
// Conversations
// =====================================================================

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun ConversationsScreen(
    onOpen: (String) -> Unit,
    onNew: () -> Unit,
    onBlacklist: () -> Unit,
    onSettings: () -> Unit
) {
    val c = LocalColors.current
    val haptics = rememberHaptics()
    val ctx = LocalContext.current
    val version = LocalVersion.current
    val scope = rememberCoroutineScope()
    val backdrop = rememberLayerBackdrop()
    val data by produceState<Pair<List<Conversation>, Set<String>>?>(null, version) {
        value = withContext(Dispatchers.IO) { Sms.conversations(ctx) to Sms.blocked(ctx).toSet() }
    }
    var menuFor by remember { mutableStateOf<Conversation?>(null) }
    val top = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()

    Box(Modifier.fillMaxSize()) {
        Box(Modifier.fillMaxSize().layerBackdrop(backdrop)) {
            LivingBackground()
            LazyColumn(
                Modifier.fillMaxSize(),
                contentPadding = PaddingValues(top = top + 72.dp, bottom = 120.dp)
            ) {
                item(key = "title") {
                    Text(
                        "Сообщения", color = c.text, fontSize = 34.sp, fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(start = 22.dp, bottom = 10.dp)
                    )
                }
                val list = data?.first
                if (list != null && list.isEmpty()) {
                    item(key = "empty") {
                        Text(
                            "Пока нет переписок", color = c.dim, fontSize = 15.sp,
                            modifier = Modifier.fillMaxWidth().padding(top = 80.dp).animateItem(),
                            textAlign = TextAlign.Center
                        )
                    }
                }
                items(list ?: emptyList(), key = { it.threadId }) { conv ->
                    val src = remember { MutableInteractionSource() }
                    val blocked = data?.second?.any { numbersMatch(it, conv.address) } == true
                    Row(
                        Modifier.fillMaxWidth()
                            .animateItem(
                                fadeInSpec = tween(350), fadeOutSpec = tween(250),
                                placementSpec = spring(Spring.DampingRatioLowBouncy, Spring.StiffnessMediumLow)
                            )
                            .pressScale(src, 0.03f)
                            .combinedClickable(
                                interactionSource = src,
                                indication = null,
                                onLongClick = { haptics.long(); menuFor = conv },
                                onClick = { haptics.press(); onOpen(conv.address) }
                            )
                            .padding(horizontal = 18.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Avatar(conv.title, conv.address, lookup = true)
                        Spacer(Modifier.size(14.dp))
                        Column(Modifier.weight(1f)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    conv.title, color = c.text, fontSize = 16.sp,
                                    fontWeight = if (conv.unread > 0) FontWeight.Bold else FontWeight.Medium,
                                    maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, false)
                                )
                                if (blocked) {
                                    Spacer(Modifier.size(6.dp))
                                    Icon(Icons.Filled.Lock, "В чёрном списке", tint = c.danger, modifier = Modifier.size(14.dp))
                                }
                                Spacer(Modifier.weight(1f))
                                Text(fmtListTime(conv.date), color = c.dim, fontSize = 12.sp)
                            }
                            Spacer(Modifier.height(3.dp))
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    (if (conv.outgoing) "Вы: " else "") + conv.snippet,
                                    color = if (conv.unread > 0) c.text else c.dim, fontSize = 14.sp,
                                    maxLines = 2, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f)
                                )
                                AnimatedVisibility(conv.unread > 0, enter = scaleIn() + fadeIn(), exit = scaleOut() + fadeOut()) {
                                    Box(
                                        Modifier.padding(start = 8.dp).heightIn(min = 20.dp).widthIn(min = 20.dp)
                                            .background(c.bubble, CircleShape).padding(horizontal = 6.dp),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text("${conv.unread}", color = c.bubbleText, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        // Floating glass controls (outside the captured layer, so they refract the list)
        Row(
            Modifier.fillMaxWidth().statusBarsPadding().padding(horizontal = 14.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.End,
            verticalAlignment = Alignment.CenterVertically
        ) {
            GlassIconButton(backdrop, onClick = onBlacklist) { Icon(Icons.Filled.Lock, "Чёрный список", tint = c.text) }
            Spacer(Modifier.size(10.dp))
            GlassIconButton(backdrop, onClick = onSettings) { Icon(Icons.Filled.Settings, "Настройки", tint = c.text) }
        }
        Box(Modifier.fillMaxSize().navigationBarsPadding().padding(22.dp), contentAlignment = Alignment.BottomEnd) {
            val press = rememberPress()
            Box(
                Modifier.size(62.dp)
                    .liquidGlass(backdrop, Pill, c.bubble.copy(alpha = 0.8f), 14.dp, 28.dp, press = press)
                    .pressable(press, onClick = onNew),
                contentAlignment = Alignment.Center
            ) { Icon(Icons.Filled.Create, "Новое сообщение", tint = c.bubbleText) }
        }

        UpdateSheet(backdrop)

        val conv = menuFor
        val blocked = conv != null && data?.second?.any { numbersMatch(it, conv.address) } == true
        GlassSheet(conv != null, backdrop, onDismiss = { menuFor = null }) {
            if (conv != null) {
                Text(conv.title, color = c.text, fontSize = 18.sp, fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.height(14.dp))
                SheetAction(if (blocked) "Убрать из чёрного списка" else "Добавить в чёрный список", danger = !blocked) {
                    scope.launch(Dispatchers.IO) {
                        if (blocked) Sms.unblock(ctx, conv.address) else Sms.block(ctx, conv.address)
                        SmsEvents.changed()
                    }
                    menuFor = null
                }
                SheetAction("Удалить переписку", danger = true) {
                    scope.launch(Dispatchers.IO) { Sms.deleteThread(ctx, conv.address) }
                    menuFor = null
                }
            }
        }
    }
}

/** Compares numbers by their last 10 digits so +7 999… and 8 999… match. */
fun numbersMatch(a: String, b: String): Boolean {
    val x = a.filter { it.isDigit() }.takeLast(10)
    val y = b.filter { it.isDigit() }.takeLast(10)
    return if (x.isEmpty() || y.isEmpty()) a.equals(b, ignoreCase = true) else x == y
}

// =====================================================================
// Chat
// =====================================================================

private sealed interface Row2 {
    data class Bubble(val m: Msg) : Row2
    data class Day(val t: Long) : Row2
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun ChatScreen(address: String, draft: String?, onBack: () -> Unit) {
    val c = LocalColors.current
    val ctx = LocalContext.current
    val version = LocalVersion.current
    val scope = rememberCoroutineScope()
    val backdrop = rememberLayerBackdrop()
    val listState = rememberLazyListState()
    var text by rememberSaveable(address) { mutableStateOf(draft ?: "") }
    var menu by remember { mutableStateOf(false) }
    var msgMenu by remember { mutableStateOf<Msg?>(null) }

    val messages by produceState<List<Msg>>(emptyList(), address, version) {
        value = withContext(Dispatchers.IO) { Sms.messages(ctx, address) }
    }
    val blocked by produceState(false, address, version) {
        value = withContext(Dispatchers.IO) { Sms.isBlocked(ctx, address) }
    }
    val name = remember(address) { Sms.name(ctx, address) }

    LaunchedEffect(address, version) { withContext(Dispatchers.IO) { Sms.markRead(ctx, address) } }
    val haptics = rememberHaptics()
    var seen by remember(address) { mutableStateOf(-1) }
    LaunchedEffect(messages.size) {
        if (messages.isNotEmpty()) {
            // A soft tick when a new message arrives in the open chat
            if (seen in 0 until messages.size && messages.last().outgoing.not()) haptics.sheet()
            seen = messages.size
            listState.animateScrollToItem(0)
        }
    }

    val rows = remember(messages) {
        buildList<Row2> {
            for (i in messages.indices.reversed()) {
                val m = messages[i]
                add(Row2.Bubble(m))
                if (i == 0 || !sameDay(messages[i - 1].date, m.date)) add(Row2.Day(m.date))
            }
        }
    }
    val top = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()

    Box(Modifier.fillMaxSize().imePadding()) {
        Box(Modifier.fillMaxSize().layerBackdrop(backdrop)) {
            LivingBackground()
            LazyColumn(
                Modifier.fillMaxSize(),
                state = listState,
                reverseLayout = true,
                contentPadding = PaddingValues(start = 12.dp, end = 12.dp, top = top + 78.dp, bottom = 96.dp)
            ) {
                items(rows, key = { r ->
                    when (r) { is Row2.Bubble -> "m${r.m.id}"; is Row2.Day -> "d${dayKey(r.t)}" }
                }) { r ->
                    when (r) {
                        is Row2.Day -> Text(
                            fmtDay(r.t), color = c.dim, fontSize = 12.sp, textAlign = TextAlign.Center,
                            modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp).animateItem()
                        )
                        is Row2.Bubble -> MessageBubble(
                            r.m,
                            Modifier.animateItem(
                                fadeInSpec = tween(300),
                                placementSpec = spring(Spring.DampingRatioLowBouncy, Spring.StiffnessMediumLow)
                            ),
                            onLong = { msgMenu = r.m }
                        )
                    }
                }
            }
        }

        GlassHeader(
            backdrop, name ?: address, if (name != null) address else if (blocked) "В чёрном списке" else null,
            onBack = onBack
        ) {
            GlassIconButton(backdrop, onClick = { menu = true }) { Icon(Icons.Filled.MoreVert, "Ещё", tint = c.text) }
        }

        // Input bar
        Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.Bottom) {
            AnimatedVisibility(blocked, enter = fadeIn() + scaleIn(initialScale = 0.9f), exit = fadeOut() + scaleOut(targetScale = 0.9f)) {
                Text(
                    "Номер в чёрном списке — входящие от него не приходят",
                    color = c.danger, fontSize = 13.sp, textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth().padding(bottom = 6.dp)
                )
            }
            Row(
                Modifier.fillMaxWidth().navigationBarsPadding().padding(horizontal = 12.dp, vertical = 10.dp),
                verticalAlignment = Alignment.Bottom
            ) {
                Box(
                    Modifier.weight(1f).heightIn(min = 50.dp).animateContentSize(spring(stiffness = Spring.StiffnessMediumLow))
                        .themedGlass(backdrop, RoundedCornerShape(25.dp), lensHeight = 12.dp, lensAmount = 24.dp)
                        .padding(horizontal = 18.dp, vertical = 14.dp),
                    contentAlignment = Alignment.CenterStart
                ) {
                    if (text.isEmpty()) Text("Сообщение", color = c.dim, fontSize = 16.sp)
                    BasicTextField(
                        text, { text = it },
                        textStyle = TextStyle(color = c.text, fontSize = 16.sp),
                        cursorBrush = SolidColor(c.bubble),
                        maxLines = 6,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
                AnimatedVisibility(
                    text.isNotBlank(),
                    enter = scaleIn(spring(0.5f, 500f)) + fadeIn(),
                    exit = scaleOut(spring(stiffness = 600f)) + fadeOut()
                ) {
                    val press = rememberPress()
                    Box(
                        Modifier.padding(start = 8.dp).size(50.dp)
                            .liquidGlass(backdrop, Pill, c.bubble.copy(alpha = 0.9f), 10.dp, 20.dp, press = press)
                            .pressable(press) {
                                val t = text.trim()
                                text = ""
                                scope.launch(Dispatchers.IO) { Sms.send(ctx, address, t) }
                            },
                        contentAlignment = Alignment.Center
                    ) { Icon(Icons.AutoMirrored.Filled.Send, "Отправить", tint = c.bubbleText) }
                }
            }
        }

        GlassSheet(menu, backdrop, onDismiss = { menu = false }) {
            Text(name ?: address, color = c.text, fontSize = 18.sp, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(14.dp))
            SheetAction("Позвонить") {
                menu = false
                runCatching { ctx.startActivity(Intent(Intent.ACTION_DIAL, Uri.parse("tel:$address"))) }
            }
            SheetAction(if (blocked) "Убрать из чёрного списка" else "Добавить в чёрный список", danger = !blocked) {
                menu = false
                scope.launch(Dispatchers.IO) {
                    if (blocked) Sms.unblock(ctx, address) else Sms.block(ctx, address)
                    SmsEvents.changed()
                }
            }
            SheetAction("Удалить переписку", danger = true) {
                menu = false
                scope.launch(Dispatchers.IO) { Sms.deleteThread(ctx, address) }
                onBack()
            }
        }

        val m = msgMenu
        GlassSheet(m != null, backdrop, onDismiss = { msgMenu = null }) {
            if (m != null) {
                SheetAction("Копировать") {
                    ctx.getSystemService(ClipboardManager::class.java)
                        ?.setPrimaryClip(ClipData.newPlainText("sms", m.body))
                    msgMenu = null
                }
                if (m.failed) SheetAction("Отправить ещё раз") {
                    scope.launch(Dispatchers.IO) { Sms.resend(ctx, m) }
                    msgMenu = null
                }
                SheetAction("Удалить", danger = true) {
                    scope.launch(Dispatchers.IO) { Sms.delete(ctx, m) }
                    msgMenu = null
                }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun MessageBubble(m: Msg, modifier: Modifier, onLong: () -> Unit) {
    val c = LocalColors.current
    val haptics = rememberHaptics()
    val out = m.outgoing
    val src = remember { MutableInteractionSource() }
    Column(
        modifier.fillMaxWidth().padding(vertical = 3.dp),
        horizontalAlignment = if (out) Alignment.End else Alignment.Start
    ) {
        val bg = if (out) Brush.verticalGradient(listOf(c.bubble, c.bubble.copy(alpha = 0.85f)))
        else Brush.verticalGradient(listOf(c.incoming, c.incoming))
        Box(
            Modifier.widthIn(max = 300.dp).pressScale(src, 0.05f)
                .background(bg, RoundedBubble(out))
                .combinedClickable(
                    interactionSource = src,
                    indication = null,
                    onLongClick = { haptics.long(); onLong() },
                    onClick = {}
                )
                .padding(horizontal = 15.dp, vertical = 10.dp)
        ) {
            Text(m.body, color = if (out) c.bubbleText else c.incomingText, fontSize = 16.sp, lineHeight = 21.sp)
        }
        val status = when {
            m.failed -> "Не отправлено · удерживайте, чтобы повторить"
            m.pending -> "Отправка…"
            else -> fmtClock(m.date)
        }
        Text(
            status, color = if (m.failed) c.danger else c.dim, fontSize = 11.sp,
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
        )
    }
}

// =====================================================================
// New message
// =====================================================================

@Composable
fun NewMessageScreen(onBack: () -> Unit, onPick: (String) -> Unit) {
    val c = LocalColors.current
    val ctx = LocalContext.current
    val backdrop = rememberLayerBackdrop()
    var q by rememberSaveable { mutableStateOf("") }
    val hits by produceState(emptyList<ContactHit>(), q) {
        value = withContext(Dispatchers.IO) { Sms.searchContacts(ctx, q.trim()) }
    }
    val looksLikeNumber = q.count { it.isDigit() } >= 3 && q.all { it.isDigit() || it in "+-() " }
    val top = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()

    Box(Modifier.fillMaxSize().imePadding()) {
        Box(Modifier.fillMaxSize().layerBackdrop(backdrop)) {
            LivingBackground()
            LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(top = top + 140.dp, bottom = 40.dp)) {
                if (looksLikeNumber) item(key = "num") {
                    ContactRow("Написать на ${q.trim()}", null, q.trim(), Modifier.animateItem()) { onPick(q.trim()) }
                }
                items(hits, key = { it.number }) { h ->
                    ContactRow(h.name, h.number, h.number, Modifier.animateItem()) { onPick(h.number) }
                }
            }
        }
        Column {
            GlassHeader(backdrop, "Новое сообщение", onBack = onBack)
            Box(
                Modifier.padding(horizontal = 14.dp).fillMaxWidth()
                    .themedGlass(backdrop, Pill, lensHeight = 10.dp, lensAmount = 20.dp)
                    .padding(horizontal = 20.dp, vertical = 15.dp)
            ) {
                if (q.isEmpty()) Text("Имя или номер", color = c.dim, fontSize = 16.sp)
                BasicTextField(
                    q, { q = it }, singleLine = true,
                    textStyle = TextStyle(color = c.text, fontSize = 16.sp),
                    cursorBrush = SolidColor(c.bubble),
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }
    }
}

@Composable
private fun ContactRow(title: String, sub: String?, address: String, modifier: Modifier, onClick: () -> Unit) {
    val c = LocalColors.current
    val press = rememberPress()
    Row(
        modifier.fillMaxWidth().pressScale(press, 0.03f).pressable(press, onClick = onClick)
            .padding(horizontal = 18.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Avatar(title, address, 44.dp, lookup = true)
        Spacer(Modifier.size(14.dp))
        Column {
            Text(title, color = c.text, fontSize = 16.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (sub != null) Text(sub, color = c.dim, fontSize = 13.sp)
        }
    }
}

// =====================================================================
// Blacklist
// =====================================================================

@Composable
fun BlacklistScreen(onBack: () -> Unit) {
    val c = LocalColors.current
    val ctx = LocalContext.current
    val version = LocalVersion.current
    val scope = rememberCoroutineScope()
    val backdrop = rememberLayerBackdrop()
    var number by rememberSaveable { mutableStateOf("") }
    val list by produceState<List<String>?>(null, version) {
        value = withContext(Dispatchers.IO) { Sms.blocked(ctx) }
    }
    val canBlock = remember(version) { Sms.canBlock(ctx) }
    val top = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()

    Box(Modifier.fillMaxSize().imePadding()) {
        LivingBackground(Modifier.layerBackdrop(backdrop))
        LazyColumn(
            Modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = 14.dp, end = 14.dp, top = top + 76.dp, bottom = 110.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            if (!canBlock) item(key = "warn") {
                Text(
                    "Чёрный список доступен, только когда приложение выбрано основным для SMS.",
                    color = c.danger, fontSize = 14.sp, modifier = Modifier.padding(8.dp)
                )
            }
            if (list?.isEmpty() == true) item(key = "empty") {
                Text(
                    "Список пуст.\nДобавьте номер внизу или удерживайте переписку в списке.",
                    color = c.dim, fontSize = 15.sp, textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth().padding(top = 60.dp).animateItem()
                )
            }
            items(list?.distinct() ?: emptyList(), key = { it }) { n ->
                val name = remember(n) { Sms.name(ctx, n) }
                Row(
                    Modifier.fillMaxWidth()
                        .animateItem(
                            fadeInSpec = tween(300), fadeOutSpec = tween(250),
                            placementSpec = spring(Spring.DampingRatioLowBouncy, Spring.StiffnessMediumLow)
                        )
                        .themedGlass(backdrop, RoundedCornerShape(24.dp), lensHeight = 12.dp, lensAmount = 24.dp)
                        .padding(14.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Avatar(name ?: n, n, 42.dp, lookup = true)
                    Spacer(Modifier.size(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text(name ?: n, color = c.text, fontSize = 16.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        if (name != null) Text(n, color = c.dim, fontSize = 13.sp)
                    }
                    GlassButton(backdrop, "Убрать") {
                        scope.launch(Dispatchers.IO) { Sms.unblock(ctx, n); SmsEvents.changed() }
                    }
                }
            }
        }

        GlassHeader(backdrop, "Чёрный список", subtitle = list?.let { "Номеров: ${it.size}" }, onBack = onBack)

        Row(
            Modifier.align(Alignment.BottomCenter).fillMaxWidth().navigationBarsPadding()
                .padding(horizontal = 12.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                Modifier.weight(1f).themedGlass(backdrop, Pill, lensHeight = 10.dp, lensAmount = 20.dp)
                    .padding(horizontal = 20.dp, vertical = 15.dp)
            ) {
                if (number.isEmpty()) Text("Номер телефона", color = c.dim, fontSize = 16.sp)
                BasicTextField(
                    number, { number = it }, singleLine = true,
                    textStyle = TextStyle(color = c.text, fontSize = 16.sp),
                    cursorBrush = SolidColor(c.bubble),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                    modifier = Modifier.fillMaxWidth()
                )
            }
            AnimatedVisibility(number.count { it.isDigit() } >= 3, enter = scaleIn() + fadeIn(), exit = scaleOut() + fadeOut()) {
                val press = rememberPress()
                Box(
                    Modifier.padding(start = 8.dp).size(50.dp)
                        .liquidGlass(backdrop, Pill, c.danger.copy(alpha = 0.85f), 10.dp, 20.dp, press = press)
                        .pressable(press) {
                            val n = number.trim()
                            number = ""
                            scope.launch(Dispatchers.IO) { Sms.block(ctx, n); SmsEvents.changed() }
                        },
                    contentAlignment = Alignment.Center
                ) { Icon(Icons.Filled.Add, "Заблокировать", tint = Color.White) }
            }
        }
    }
}

// =====================================================================
// Settings — theme & bubble colours
// =====================================================================

@Composable
fun SettingsScreen(
    mode: ThemeMode,
    onMode: (ThemeMode) -> Unit,
    bubbleDark: Color,
    bubbleLight: Color,
    onBubble: (Boolean, Color) -> Unit,
    lat: Double?,
    lon: Double?,
    hasGeo: Boolean,
    onRequestGeo: () -> Unit,
    onBack: () -> Unit
) {
    val c = LocalColors.current
    val backdrop = rememberLayerBackdrop()
    var editing by remember { mutableStateOf<Boolean?>(null) } // true = dark colour, false = light colour
    val top = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
    val clock = remember { SimpleDateFormat("HH:mm", Locale.forLanguageTag("ru")) }

    Box(Modifier.fillMaxSize()) {
        LivingBackground(Modifier.layerBackdrop(backdrop))
        Column(
            Modifier.fillMaxSize().verticalScroll(rememberScrollState())
                .padding(start = 14.dp, end = 14.dp, top = top + 76.dp, bottom = 40.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            // Theme
            Column(
                Modifier.fillMaxWidth().themedGlass(backdrop, Card).padding(18.dp).animateContentSize(spring(stiffness = Spring.StiffnessMediumLow))
            ) {
                Text("Тема", color = c.text, fontSize = 18.sp, fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.height(12.dp))
                Segmented(backdrop, ThemeMode.entries.map { it.label }, mode.ordinal) { onMode(ThemeMode.entries[it]) }
                AnimatedVisibility(mode == ThemeMode.AUTO) {
                    Column(Modifier.padding(top = 14.dp)) {
                        if (lat != null && lon != null) {
                            val t = Sun.times(lat, lon)
                            val line = when {
                                t.polarDay -> "Сейчас полярный день — светлая тема весь день"
                                t.polarNight -> "Сейчас полярная ночь — тёмная тема весь день"
                                else -> "Восход ${clock.format(Date(t.sunrise!!))} · закат ${clock.format(Date(t.sunset!!))}"
                            }
                            Text(line, color = c.text, fontSize = 15.sp)
                            Text("Светлая днём, чёрная после заката", color = c.dim, fontSize = 13.sp)
                        } else {
                            Text(
                                if (hasGeo) "Определяю местоположение…" else "Нет доступа к геопозиции — пока переключаю в 7:00 и 21:00",
                                color = c.dim, fontSize = 14.sp
                            )
                        }
                        Spacer(Modifier.height(12.dp))
                        GlassButton(backdrop, if (hasGeo) "Обновить геопозицию" else "Разрешить геопозицию", onClick = onRequestGeo)
                    }
                }
            }

            // Bubble colours
            Column(Modifier.fillMaxWidth().themedGlass(backdrop, Card).padding(18.dp)) {
                Text("Цвет сообщений", color = c.text, fontSize = 18.sp, fontWeight = FontWeight.SemiBold)
                Text("Для каждой темы свой", color = c.dim, fontSize = 13.sp)
                Spacer(Modifier.height(12.dp))
                ColorRow("В чёрной теме", bubbleDark) { editing = true }
                Spacer(Modifier.height(8.dp))
                ColorRow("В светлой теме", bubbleLight) { editing = false }
            }

            AboutCard(backdrop, "Polaris SMS")
        }

        GlassHeader(backdrop, "Настройки", onBack = onBack)
        UpdateSheet(backdrop)

        val e = editing
        GlassSheet(e != null, backdrop, onDismiss = { editing = null }) {
            if (e != null) {
                Text(
                    if (e) "Цвет в чёрной теме" else "Цвет в светлой теме",
                    color = c.text, fontSize = 18.sp, fontWeight = FontWeight.SemiBold
                )
                Spacer(Modifier.height(16.dp))
                androidx.compose.runtime.key(e) {
                    ColorWheel(if (e) bubbleDark else bubbleLight) { onBubble(e, it) }
                }
                Spacer(Modifier.height(16.dp))
                GlassButton(backdrop, "Готово", Modifier.fillMaxWidth(), accent = true) { editing = null }
            }
        }
    }
}

@Composable
private fun ColorRow(title: String, color: Color, onClick: () -> Unit) {
    val c = LocalColors.current
    val press = rememberPress()
    Row(
        Modifier.fillMaxWidth().pressScale(press, 0.03f)
            .background(c.surface, RoundedCornerShape(18.dp))
            .pressable(press, onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(title, color = c.text, fontSize = 15.sp, modifier = Modifier.weight(1f))
        Box(Modifier.size(28.dp).background(color, CircleShape))
    }
}
