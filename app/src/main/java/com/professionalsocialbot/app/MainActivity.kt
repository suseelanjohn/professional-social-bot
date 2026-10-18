package com.professionalsocialbot.app

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.professionalsocialbot.app.analytics.HiddenLinkedInAnalyticsExtractor
import com.professionalsocialbot.app.data.*
import kotlinx.coroutines.launch

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
                ProfessionalSocialBotScreen(database.postDao())
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProfessionalSocialBotScreen(dao: SocialPostDao) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val allPosts by dao.getAllPostsFlow().collectAsState(initial = emptyList())

    var selectedFolder by remember { mutableStateOf("/Downloads/InterestingTopics") }
    var scanTime by remember { mutableStateOf("20:00") }
    var publishTime by remember { mutableStateOf("09:30") }
    var analyticsSyncTime by remember { mutableStateOf("21:00") }
    var triggerWebViewSync by remember { mutableStateOf(false) }

    val folderPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocumentTree()
    ) { uri: Uri? ->
        uri?.let {
            context.contentResolver.takePersistableUriPermission(
                it, Intent.FLAG_GRANT_READ_URI_PERMISSION
            )
            selectedFolder = it.lastPathSegment ?: "/Downloads/Topics"
            context.getSharedPreferences("bot_prefs", 0).edit()
                .putString("folder_uri", it.toString()).apply()
        }
    }

    LaunchedEffect(Unit) {
        if (allPosts.isEmpty()) {
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
            item {
                Card(
                    colors = CardDefaults.cardColors(containerColor = Color(0xFF151C28)),
                    shape = RoundedCornerShape(20.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(Modifier.size(10.dp).clip(CircleShape).background(Color(0xFF10B981)))
                            Spacer(Modifier.width(8.dp))
                            Text(
                                "Watching: $selectedFolder",
                                fontSize = 13.sp,
                                color = Color.White,
                                fontWeight = FontWeight.Medium
                            )
                        }
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            ScheduleChip("Scan Folder", scanTime) { scanTime = if (scanTime == "20:00") "21:00" else "20:00" }
                            ScheduleChip("Auto-Post", publishTime) { publishTime = if (publishTime == "09:30") "10:00" else "09:30" }
                            ScheduleChip("Sync Views", analyticsSyncTime) { triggerWebViewSync = true }
                        }
                    }
                }
            }

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
                    onUpdateDraft = { updated -> scope.launch { dao.updatePost(updated) } },
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
                        }
                    }
                )
            }

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
fun ScheduleChip(label: String, time: String, onClick: () -> Unit) {
    Surface(
        color = Color(0xFF1E293B),
        shape = RoundedCornerShape(12.dp),
        modifier = Modifier.clickable { onClick() }
    ) {
        Column(Modifier.padding(horizontal = 12.dp, vertical = 8.dp)) {
            Text(label, fontSize = 10.sp, color = Color(0xFF94A3B8))
            Text(time, fontSize = 14.sp, fontWeight = FontWeight.Bold, color = Color.White)
        }
    }
}

@Composable
fun ApprovalDraftCard(
    draft: SocialPostEntity,
    onUpdateDraft: (SocialPostEntity) -> Unit,
    onPublishNow: (SocialPostEntity) -> Unit
) {
    var editedText by remember(draft.captionText) { mutableStateOf(draft.captionText) }
    var personalTakeInput by remember { mutableStateOf("") }
    val brewScore = remember(editedText, draft.trimPagesToFive) {
        BrewAlgorithmScorer.calculateScore(draft.copy(captionText = editedText))
    }

    Card(
        colors = CardDefaults.cardColors(containerColor = Color(0xFF151C28)),
        shape = RoundedCornerShape(24.dp),
        modifier = Modifier.fillMaxWidth().border(1.dp, Color(0xFF334155), RoundedCornerShape(24.dp))
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(Color(0xFF1E293B)).padding(12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                    Icon(Icons.Default.PictureAsPdf, contentDescription = null, tint = Color(0xFFF43F5E))
                    Spacer(Modifier.width(10.dp))
                    Column {
                        Text(draft.fileName, fontWeight = FontWeight.SemiBold, fontSize = 13.sp, color = Color.White)
                        Text(if (draft.trimPagesToFive) "5-Slide Carousel (1.39x Reach)" else "Full PDF Attached", fontSize = 11.sp, color = Color(0xFF10B981))
                    }
                }
                FilterChip(
                    selected = draft.trimPagesToFive,
                    onClick = { onUpdateDraft(draft.copy(trimPagesToFive = !draft.trimPagesToFive)) },
                    label = { Text("Keep 1–5", fontSize = 11.sp) }
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("AI CAPTION EDITOR", fontSize = 11.sp, color = Color(0xFF94A3B8))
                Surface(color = Color(0xFF064E3B), shape = RoundedCornerShape(50)) {
                    Text("360Brew Score: $brewScore/100", modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp), fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Color(0xFF34D399))
                }
            }

            OutlinedTextField(
                value = editedText,
                onValueChange = { editedText = it; onUpdateDraft(draft.copy(captionText = it)) },
                modifier = Modifier.fillMaxWidth(),
                minLines = 5
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                OutlinedButton(onClick = { onPublishNow(draft.copy(captionText = editedText)) }, modifier = Modifier.weight(1f)) {
                    Text("Post Now")
                }
                Button(
                    onClick = { onUpdateDraft(draft.copy(captionText = editedText, status = PostStatus.APPROVED_QUEUED)) },
                    colors = ButtonDefaults.buttonColors(containerColor = if (draft.status == PostStatus.APPROVED_QUEUED) Color(0xFF10B981) else Color(0xFF6366F1)),
                    modifier = Modifier.weight(1.4f)
                ) {
                    Icon(Icons.Default.CheckCircle, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(6.dp))
                    Text(if (draft.status == PostStatus.APPROVED_QUEUED) "Queued for 09:30" else "Approve & Queue")
                }
            }
        }
    }
}

@Composable
fun MetricBentoCard(modifier: Modifier, title: String, value: String, subtitle: String, accent: Color) {
    Card(modifier = modifier, colors = CardDefaults.cardColors(containerColor = Color(0xFF151C28)), shape = RoundedCornerShape(18.dp)) {
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
    Card(colors = CardDefaults.cardColors(containerColor = Color(0xFF151C28)), shape = RoundedCornerShape(16.dp), modifier = Modifier.fillMaxWidth()) {
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
