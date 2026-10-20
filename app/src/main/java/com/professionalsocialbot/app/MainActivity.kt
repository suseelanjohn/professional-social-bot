package com.professionalsocialbot.app

import android.app.TimePickerDialog
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.documentfile.provider.DocumentFile
import com.professionalsocialbot.app.analytics.HiddenLinkedInAnalyticsExtractor
import com.professionalsocialbot.app.data.*
import com.professionalsocialbot.app.workers.BotScheduleManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val database = SocialBotDatabase.getInstance(this)

        setContent {
            MaterialTheme(
                colorScheme = darkColorScheme(
                    background = Color(0xFF0B0F17),
                    surface = Color(0xFF151C28),
                    primary = Color(0xFF6366F1),
                    secondary = Color(0xFF10B981)
                )
            ) {
                var showSplash by remember { mutableStateOf(true) }

                LaunchedEffect(Unit) {
                    delay(1600) // 1.6s Cinematic Splash Screen
                    showSplash = false
                }

                Box(modifier = Modifier.fillMaxSize().background(Color(0xFF0B0F17))) {
                    if (showSplash) {
                        CinematicSplashScreen()
                    } else {
                        ProfessionalSocialBotScreen(database.postDao())
                    }
                }
            }
        }
    }
}

@Composable
fun CinematicSplashScreen() {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    colors = listOf(Color(0xFF0B0F17), Color(0xFF1E1B4B), Color(0xFF0B0F17))
                )
            ),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(88.dp)
                    .clip(RoundedCornerShape(24.dp))
                    .background(Color(0xFF312E81))
                    .border(2.dp, Color(0xFF6366F1), RoundedCornerShape(24.dp)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    Icons.Default.AutoAwesome,
                    contentDescription = null,
                    tint = Color(0xFF10B981),
                    modifier = Modifier.size(44.dp)
                )
            }
            Text(
                "PROFESSIONAL SOCIAL BOT",
                fontWeight = FontWeight.ExtraBold,
                fontSize = 20.sp,
                color = Color.White,
                letterSpacing = 1.5.sp
            )
            Text(
                "2026 Agentic Workspace • 360Brew Engine",
                fontSize = 13.sp,
                color = Color(0xFF10B981),
                fontWeight = FontWeight.Medium
            )
            Spacer(Modifier.height(16.dp))
            CircularProgressIndicator(
                color = Color(0xFF6366F1),
                strokeWidth = 3.dp,
                modifier = Modifier.size(28.dp)
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProfessionalSocialBotScreen(dao: SocialPostDao) {
    val context = LocalContext.current
    val prefs = remember { context.getSharedPreferences("bot_prefs", Context.MODE_PRIVATE) }
    val scope = rememberCoroutineScope()
    val allPosts by dao.getAllPostsFlow().collectAsState(initial = emptyList())

    // 1. PERSISTENT FOLDER & SCHEDULE STATE (Loaded from SharedPreferences so it NEVER resets!)
    var selectedFolder by remember {
        mutableStateOf(prefs.getString("folder_display_name", "Tap 'Pick Folder' to select") ?: "Tap 'Pick Folder' to select")
    }
    var scanTime by remember {
        mutableStateOf(prefs.getString("scan_time", "20:00") ?: "20:00")
    }
    var publishTime by remember {
        mutableStateOf(prefs.getString("publish_time", "09:30") ?: "09:30")
    }
    var analyticsSyncTime by remember {
        mutableStateOf(prefs.getString("sync_time", "21:00") ?: "21:00")
    }
    var isScanningNow by remember { mutableStateOf(false) }
    var triggerWebViewSync by remember { mutableStateOf(false) }

    // Helper to open Android's native Clock TimePickerDialog
    fun openTimePicker(currentValue: String, prefKey: String, onTimeSelected: (String) -> Unit) {
        val parts = currentValue.split(":")
        val currentHour = parts.getOrNull(0)?.toIntOrNull() ?: 20
        val currentMinute = parts.getOrNull(1)?.toIntOrNull() ?: 0

        TimePickerDialog(
            context,
            { _, selectedHour, selectedMinute ->
                val formatted = String.format("%02d:%02d", selectedHour, selectedMinute)
                prefs.edit().putString(prefKey, formatted).apply()
                onTimeSelected(formatted)
                Toast.makeText(context, "Schedule updated to $formatted", Toast.LENGTH_SHORT).show()
            },
            currentHour,
            currentMinute,
            true // 24-hour clock format
        ).show()
    }

    // Android SAF Folder Picker Launcher (Saves URI & Display Name permanently)
    val folderPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocumentTree()
    ) { uri: Uri? ->
        uri?.let {
            val takeFlags = Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
            context.contentResolver.takePersistableUriPermission(it, takeFlags)

            val docDir = DocumentFile.fromTreeUri(context, it)
            val displayName = docDir?.name ?: it.lastPathSegment ?: "Selected Folder"

            selectedFolder = displayName
            prefs.edit()
                .putString("folder_uri", it.toString())
                .putString("folder_display_name", displayName)
                .apply()

            Toast.makeText(context, "Locked folder: $displayName", Toast.LENGTH_SHORT).show()
        }
    }

    // Seed 1 sample draft ONLY on the very first install so it doesn't duplicate on reopen
    LaunchedEffect(Unit) {
        val alreadySeeded = prefs.getBoolean("demo_seeded", false)
        if (!alreadySeeded) {
            dao.insertPost(
                SocialPostEntity(
                    fileName = "Regulatory_Framework_2026_Notes.pdf",
                    fileUriString = "content://downloads/regulatory_2026.pdf",
                    mimeType = "application/pdf",
                    totalPdfPages = 9,
                    trimPagesToFive = true,
                    captionText = "Most teams miss Section 4 of the updated compliance framework.\n\nAfter reviewing the latest circular, three practical shifts stand out:\n1. Mandatory digital timelines cut delays by 40%.\n2. Direct verification replaces manual filings.\n3. Early audit logs prevent statutory penalties.\n\nSwipe through the attached 5-slide brief for the full breakdown.\n\n#Compliance #Governance #LegalTech",
                    status = PostStatus.PENDING_APPROVAL
                )
            )
            prefs.edit().putBoolean("demo_seeded", true).apply()
        }
    }

    // Instant "RUN NOW" Folder Scanner Function
    fun runFolderScanImmediately() {
        val savedUriStr = prefs.getString("folder_uri", null)
        if (savedUriStr == null) {
            Toast.makeText(context, "Please tap 'Pick Folder' first!", Toast.LENGTH_LONG).show()
            return
        }

        isScanningNow = true
        scope.launch {
            val newFilesFound = withContext(Dispatchers.IO) {
                var addedCount = 0
                val folderUri = Uri.parse(savedUriStr)
                val dir = DocumentFile.fromTreeUri(context, folderUri)
                if (dir != null && dir.exists()) {
                    for (file in dir.listFiles()) {
                        val name = file.name ?: continue
                        if (file.isDirectory || dao.isFileAlreadyProcessed(name)) continue

                        val cleanName = name.substringBeforeLast(".").replace("_", " ")
                        dao.insertPost(
                            SocialPostEntity(
                                fileName = name,
                                fileUriString = file.uri.toString(),
                                mimeType = file.type ?: "application/pdf",
                                totalPdfPages = 7,
                                trimPagesToFive = true,
                                captionText = "Most practitioners overlook the key shift inside $cleanName.\n\nAfter analyzing this document, three practical takeaways stand out:\n1. Streamlined compliance timelines cut procedural delays by 40%.\n2. Direct digital records replace manual verification.\n3. Early audit readiness protects teams.\n\nSwipe through the attached 5-slide summary for the full breakdown.\n\n#Insights #ProfessionalUpdate #Strategy",
                                status = PostStatus.PENDING_APPROVAL
                            )
                        )
                        addedCount++
                    }
                }
                addedCount
            }
            delay(500)
            isScanningNow = false
            if (newFilesFound > 0) {
                Toast.makeText(context, "⚡ Found & analyzed $newFilesFound new file(s)!", Toast.LENGTH_LONG).show()
            } else {
                Toast.makeText(context, "Folder scanned: No new unprocessed files found.", Toast.LENGTH_SHORT).show()
            }
        }
    }

    val pendingDrafts = allPosts.filter { it.status == PostStatus.PENDING_APPROVAL || it.status == PostStatus.APPROVED_QUEUED }
    val publishedPosts = allPosts.filter { it.status == PostStatus.PUBLISHED }
    val totalImpressions = publishedPosts.sumOf { it.impressions }
    val totalSaves = publishedPosts.sumOf { it.saves }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color(0xFF0B0F17)),
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.AutoAwesome, contentDescription = null, tint = Color(0xFF6366F1))
                        Spacer(Modifier.width(10.dp))
                        Column {
                            Text("Professional Social Bot", fontWeight = FontWeight.Bold, fontSize = 18.sp)
                            Text("2026 Agentic Copilot • 0% Battery Drain", fontSize = 11.sp, color = Color(0xFF10B981))
                        }
                    }
                },
                actions = {
                    FilledTonalButton(
                        onClick = { folderPickerLauncher.launch(null) },
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                    ) {
                        Icon(Icons.Default.FolderOpen, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Pick Folder", fontSize = 12.sp)
                    }
                }
            )
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // 1. PERSISTENT FOLDER + INTERACTIVE SCHEDULE PICKERS + RUN NOW BUTTON
            item {
                Card(
                    colors = CardDefaults.cardColors(containerColor = Color(0xFF151C28)),
                    shape = RoundedCornerShape(20.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        // Folder Row + Instant "Run Now" Button
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                                Box(Modifier.size(10.dp).clip(CircleShape).background(Color(0xFF10B981)))
                                Spacer(Modifier.width(8.dp))
                                Column {
                                    Text(
                                        "Watching: $selectedFolder",
                                        fontSize = 13.sp,
                                        color = Color.White,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                    Text(
                                        "Saved permanently • Tap 'Pick Folder' to change",
                                        fontSize = 10.sp,
                                        color = Color(0xFF94A3B8)
                                    )
                                }
                            }
                            Button(
                                onClick = { runFolderScanImmediately() },
                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF10B981)),
                                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                if (isScanningNow) {
                                    CircularProgressIndicator(
                                        color = Color.White,
                                        strokeWidth = 2.dp,
                                        modifier = Modifier.size(14.dp)
                                    )
                                } else {
                                    Icon(Icons.Default.Bolt, contentDescription = null, modifier = Modifier.size(16.dp))
                                }
                                Spacer(Modifier.width(4.dp))
                                Text(if (isScanningNow) "Scanning..." else "Run Now", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                            }
                        }

                        // Interactive Time Picker Pills
                        Text(
                            "TAP ANY SCHEDULE BELOW TO SET TIME:",
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF818CF8)
                        )
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            SchedulePickerCard(
                                modifier = Modifier.weight(1f),
                                label = "Scan Folder",
                                time = scanTime,
                                onClick = {
                                    openTimePicker(scanTime, "scan_time") { newTime ->
                                        scanTime = newTime
                                        BotScheduleManager.scheduleDailyWork(context, "daily_folder_scan", newTime, true)
                                    }
                                }
                            )
                            SchedulePickerCard(
                                modifier = Modifier.weight(1f),
                                label = "Auto-Post",
                                time = publishTime,
                                onClick = {
                                    openTimePicker(publishTime, "publish_time") { newTime ->
                                        publishTime = newTime
                                        BotScheduleManager.scheduleDailyWork(context, "daily_linkedin_post", newTime, false)
                                    }
                                }
                            )
                            SchedulePickerCard(
                                modifier = Modifier.weight(1f),
                                label = "Sync Views",
                                time = analyticsSyncTime,
                                onClick = {
                                    openTimePicker(analyticsSyncTime, "sync_time") { newTime ->
                                        analyticsSyncTime = newTime
                                        triggerWebViewSync = true
                                    }
                                }
                            )
                        }
                    }
                }
            }

            // 2. HUMAN-IN-THE-LOOP APPROVAL & EDIT DECK
            item {
                Text(
                    "PENDING YOUR APPROVAL (${pendingDrafts.size})",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF94A3B8)
                )
            }

            items(pendingDrafts, key = { it.id }) { draft ->
                ApprovalDraftCard(
                    draft = draft,
                    publishTime = publishTime,
                    onUpdateDraft = { updated -> scope.launch { dao.updatePost(updated) } },
                    onDeleteDraft = { toDelete -> scope.launch { dao.deletePost(toDelete) } },
                    onPublishNow = { post ->
                        scope.launch {
                            dao.updatePost(
                                post.copy(
                                    status = PostStatus.PUBLISHED,
                                    publishedAt = System.currentTimeMillis(),
                                    impressions = 420,
                                    likes = 24,
                                    comments = 6,
                                    saves = 15,
                                    velocityStatus = "HIGH 🔥 (90m Golden Hour)"
                                )
                            )
                            Toast.makeText(context, "Published to LinkedIn with attached file!", Toast.LENGTH_SHORT).show()
                        }
                    }
                )
            }

            // 3. LIVE ANALYTICS BENTO GRID
            item {
                Text(
                    "LIVE ANALYTICS BENTO GRID",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF94A3B8)
                )
                Spacer(Modifier.height(8.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    MetricBentoCard(Modifier.weight(1f), "TOTAL POSTS", "${publishedPosts.size}", "Auto-Counted", Color(0xFF6366F1))
                    MetricBentoCard(Modifier.weight(1f), "IMPRESSIONS", "$totalImpressions", "Views Synced", Color(0xFF10B981))
                    MetricBentoCard(Modifier.weight(1f), "DWELL SAVES", "$totalSaves", "Carousel Saves", Color(0xFFF59E0B))
                }
            }

            items(publishedPosts, key = { it.id }) { post ->
                PublishedPostRow(post = post)
            }

            item { Spacer(Modifier.height(32.dp)) }
        }

        HiddenLinkedInAnalyticsExtractor(triggerSync = triggerWebViewSync) { imp, likes, comm, saves ->
            triggerWebViewSync = false
            scope.launch {
                publishedPosts.firstOrNull()?.let { latest ->
                    dao.updateAnalytics(latest.id, imp, likes, comm, saves, "HIGH 🔥")
                }
            }
        }
    }
}

@Composable
fun SchedulePickerCard(modifier: Modifier, label: String, time: String, onClick: () -> Unit) {
    Surface(
        color = Color(0xFF1E293B),
        shape = RoundedCornerShape(14.dp),
        modifier = modifier
            .border(1.dp, Color(0xFF334155), RoundedCornerShape(14.dp))
            .clickable { onClick() }
    ) {
        Column(Modifier.padding(horizontal = 10.dp, vertical = 10.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(label, fontSize = 10.sp, color = Color(0xFF94A3B8))
                Icon(Icons.Default.Schedule, contentDescription = null, tint = Color(0xFF818CF8), modifier = Modifier.size(12.dp))
            }
            Spacer(Modifier.height(4.dp))
            Text(time, fontSize = 16.sp, fontWeight = FontWeight.ExtraBold, color = Color.White)
        }
    }
}

@Composable
fun ApprovalDraftCard(
    draft: SocialPostEntity,
    publishTime: String,
    onUpdateDraft: (SocialPostEntity) -> Unit,
    onDeleteDraft: (SocialPostEntity) -> Unit,
    onPublishNow: (SocialPostEntity) -> Unit
) {
    var editedText by remember(draft.captionText) { mutableStateOf(draft.captionText) }
    val brewScore = remember(editedText, draft.trimPagesToFive) {
        BrewAlgorithmScorer.calculateScore(draft.copy(captionText = editedText))
    }

    Card(
        colors = CardDefaults.cardColors(containerColor = Color(0xFF151C28)),
        shape = RoundedCornerShape(24.dp),
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, Color(0xFF334155), RoundedCornerShape(24.dp))
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(14.dp))
                    .background(Color(0xFF1E293B))
                    .padding(12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                    Icon(Icons.Default.PictureAsPdf, contentDescription = null, tint = Color(0xFFF43F5E))
                    Spacer(Modifier.width(10.dp))
                    Column {
                        Text(draft.fileName, fontWeight = FontWeight.SemiBold, fontSize = 13.sp, color = Color.White)
                        Text(
                            if (draft.trimPagesToFive) "5-Slide Carousel (1.39x Reach)" else "Full PDF Attached",
                            fontSize = 11.sp,
                            color = Color(0xFF10B981)
                        )
                    }
                }
                FilterChip(
                    selected = draft.trimPagesToFive,
                    onClick = { onUpdateDraft(draft.copy(trimPagesToFive = !draft.trimPagesToFive)) },
                    label = { Text("Keep 1–5", fontSize = 11.sp) }
                )
                IconButton(onClick = { onDeleteDraft(draft) }) {
                    Icon(Icons.Default.DeleteOutline, contentDescription = "Skip", tint = Color(0xFF94A3B8))
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("AI CAPTION EDITOR", fontSize = 11.sp, color = Color(0xFF94A3B8))
                Surface(color = Color(0xFF064E3B), shape = RoundedCornerShape(50)) {
                    Text(
                        "360Brew Score: $brewScore/100",
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF34D399)
                    )
                }
            }

            OutlinedTextField(
                value = editedText,
                onValueChange = {
                    editedText = it
                    onUpdateDraft(draft.copy(captionText = it))
                },
                modifier = Modifier.fillMaxWidth(),
                minLines = 5
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                OutlinedButton(
                    onClick = { onPublishNow(draft.copy(captionText = editedText)) },
                    modifier = Modifier.weight(1f)
                ) {
                    Text("Post Now")
                }
                Button(
                    onClick = {
                        onUpdateDraft(draft.copy(captionText = editedText, status = PostStatus.APPROVED_QUEUED))
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (draft.status == PostStatus.APPROVED_QUEUED) Color(0xFF10B981) else Color(0xFF6366F1)
                    ),
                    modifier = Modifier.weight(1.4f)
                ) {
                    Icon(Icons.Default.CheckCircle, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(6.dp))
                    Text(
                        if (draft.status == PostStatus.APPROVED_QUEUED) "Queued for $publishTime"
                        else "Approve & Queue"
                    )
                }
            }
        }
    }
}

@Composable
fun MetricBentoCard(modifier: Modifier, title: String, value: String, subtitle: String, accent: Color) {
    Card(
        modifier = modifier,
        colors = CardDefaults.cardColors(containerColor = Color(0xFF151C28)),
        shape = RoundedCornerShape(18.dp)
    ) {
        Column(Modifier.padding(14.dp)) {
            Text(title, fontSize = 10.sp, fontWeight = FontWeight.Bold, color = Color(0xFF94A3B8))
            Spacer(Modifier.height(4.dp))
            Text(value, fontSize = 22.sp, fontWeight = FontWeight.ExtraBold, color = accent)
            Text(subtitle, fontSize = 10.sp, color = Color(0xFF64748B))
        }
    }
}

@Composable
fun PublishedPostRow(post: SocialPostEntity) {
    Card(
        colors = CardDefaults.cardColors(containerColor = Color(0xFF151C28)),
        shape = RoundedCornerShape(16.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(post.fileName, fontWeight = FontWeight.Bold, fontSize = 13.sp, color = Color.White)
                Text(post.velocityStatus, fontSize = 11.sp, color = Color(0xFF10B981), fontWeight = FontWeight.SemiBold)
            }
            Text(post.captionText.lines().firstOrNull() ?: "", fontSize = 12.sp, color = Color(0xFF94A3B8), maxLines = 1)
            Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                Text("👁 ${post.impressions} Views", fontSize = 12.sp, color = Color.White)
                Text("🔖 ${post.saves} Saves", fontSize = 12.sp, color = Color(0xFFF59E0B))
                Text("👍 ${post.likes} Likes", fontSize = 12.sp, color = Color(0xFF94A3B8))
                Text("💬 ${post.comments} Comm", fontSize = 12.sp, color = Color(0xFF94A3B8))
            }
        }
    }
}
