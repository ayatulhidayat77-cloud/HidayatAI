package com.example

import android.os.Bundle
import android.speech.tts.TextToSpeech
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.StopCircle
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.Image
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.credentials.CredentialManager
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.example.auth.authStateFlow
import com.example.auth.signOut
import com.example.data.NoteTag
import com.example.data.ProductivityRepository
import com.example.firebase.configureAppCheck
import com.example.ui.screens.AssistantChatScreen
import com.example.ui.screens.AuthScreen
import com.example.ui.screens.DashboardAndScheduleScreen
import com.example.ui.screens.NotesScreen
import com.example.ui.screens.StudioAndVisionScreen
import com.example.ui.theme.HidayatAITheme
import com.example.ui.theme.RoseAlert
import com.example.viewmodel.AiAssistantViewModel
import com.example.viewmodel.ProductivityViewModel
import com.google.firebase.Firebase
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.auth
import com.google.firebase.firestore.FirebaseFirestore
import java.util.Locale

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // STEP 1: Configure App Check with launch intent before any UI, ViewModels, or Firebase operations
        configureAppCheck(this, intent)

        enableEdgeToEdge()

        // STEP 2: Initialize Compose UI
        setContent {
            HidayatAITheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    AppNavigation()
                }
            }
        }
    }
}

enum class HidayatTab(
    val routeId: String,
    val labelRes: Int,
    val selectedIcon: ImageVector,
    val unselectedIcon: ImageVector
) {
    SCHEDULE(
        routeId = "dashboard_schedule",
        labelRes = R.string.nav_scheduler,
        selectedIcon = Icons.Filled.CalendarMonth,
        unselectedIcon = Icons.Outlined.CalendarMonth
    ),
    ASSISTANT(
        routeId = "ai_assistant",
        labelRes = R.string.nav_assistant,
        selectedIcon = Icons.Filled.AutoAwesome,
        unselectedIcon = Icons.Outlined.AutoAwesome
    ),
    STUDIO(
        routeId = "visual_studio",
        labelRes = R.string.nav_studio,
        selectedIcon = Icons.Filled.Image,
        unselectedIcon = Icons.Outlined.Image
    ),
    NOTES(
        routeId = "cloud_notes",
        labelRes = R.string.nav_notes,
        selectedIcon = Icons.Filled.Description,
        unselectedIcon = Icons.Outlined.Description
    )
}

@Composable
fun AppNavigation(auth: FirebaseAuth = Firebase.auth) {
    val currentUser by auth.authStateFlow().collectAsStateWithLifecycle(initialValue = auth.currentUser)
    val user = currentUser

    if (user == null) {
        AuthScreen(
            onAuthSuccess = { /* AuthStateListener updates currentUser reactively */ }
        )
    } else {
        val displayName = user.displayName?.takeIf { it.isNotBlank() }
            ?: user.email?.substringBefore("@")
            ?: "Eksekutif Hidayat"

        key(user.uid) {
            MainFeatureScaffold(
                currentUserId = user.uid,
                userDisplayName = displayName
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainFeatureScaffold(
    currentUserId: String,
    userDisplayName: String
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val credentialManager = remember(context) { CredentialManager.create(context) }
    val snackbarHostState = remember { SnackbarHostState() }

    // Instantiate ProductivityViewModel keyed by currentUserId with named Firestore database ID
    val productivityViewModel: ProductivityViewModel = viewModel(
        key = currentUserId,
        factory = viewModelFactory {
            initializer {
                val app = checkNotNull(this[APPLICATION_KEY]) {
                    "APPLICATION_KEY missing from CreationExtras"
                }
                val databaseId = app.getString(R.string.firestore_database_id)
                val db = FirebaseFirestore.getInstance(databaseId)
                ProductivityViewModel(ProductivityRepository(db), currentUserId)
            }
        }
    )

    val assistantViewModel: AiAssistantViewModel = viewModel(key = "assistant_$currentUserId")

    val mutationBanner by productivityViewModel.mutationBanner.collectAsStateWithLifecycle()

    LaunchedEffect(mutationBanner) {
        val msg = mutationBanner
        if (!msg.isNullOrBlank()) {
            snackbarHostState.showSnackbar(msg)
            productivityViewModel.clearBanner()
        }
    }

    // Android Text-to-Speech (TTS) Engine Integration
    var isSpeaking by remember { mutableStateOf(false) }
    var ttsEngine by remember { mutableStateOf<TextToSpeech?>(null) }

    DisposableEffect(context) {
        var engineRef: TextToSpeech? = null
        engineRef = TextToSpeech(context) { status ->
            if (status == TextToSpeech.SUCCESS) {
                val idLocale = Locale.forLanguageTag("id-ID")
                val res = engineRef?.setLanguage(idLocale)
                if (res == TextToSpeech.LANG_MISSING_DATA || res == TextToSpeech.LANG_NOT_SUPPORTED) {
                    engineRef?.setLanguage(Locale.getDefault())
                }
            }
        }
        ttsEngine = engineRef
        onDispose {
            engineRef?.stop()
            engineRef?.shutdown()
            ttsEngine = null
        }
    }

    val speakText: (String) -> Unit = { textToSpeak ->
        val cleanText = textToSpeak.replace("*", "").replace("#", "").trim()
        if (cleanText.isNotEmpty()) {
            isSpeaking = true
            ttsEngine?.speak(cleanText, TextToSpeech.QUEUE_FLUSH, null, "hidayat_tts_utterance")
        }
    }

    val stopSpeaking: () -> Unit = {
        ttsEngine?.stop()
        isSpeaking = false
    }

    var currentTab by rememberSaveable { mutableStateOf(HidayatTab.SCHEDULE) }

    // Custom state navigation BackHandler per Android guidelines
    BackHandler(enabled = currentTab != HidayatTab.SCHEDULE) {
        currentTab = HidayatTab.SCHEDULE
    }

    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val isWideScreen = maxWidth >= 600.dp

        Scaffold(
            contentWindowInsets = WindowInsets.safeDrawing,
            snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
            topBar = {
                TopAppBar(
                    title = {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Image(
                                painter = painterResource(id = R.drawable.logo_hidayat),
                                contentDescription = stringResource(id = R.string.app_name),
                                modifier = Modifier
                                    .size(34.dp)
                                    .clip(RoundedCornerShape(8.dp)),
                                contentScale = ContentScale.Crop
                            )
                            Spacer(modifier = Modifier.width(10.dp))
                            Column {
                                Text(
                                    text = stringResource(id = R.string.app_name),
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    text = stringResource(id = R.string.app_tagline),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    },
                    actions = {
                        if (isSpeaking) {
                            IconButton(
                                onClick = stopSpeaking,
                                modifier = Modifier.testTag("top_stop_tts_button")
                            ) {
                                Icon(
                                    imageVector = Icons.Default.StopCircle,
                                    contentDescription = stringResource(id = R.string.stop_speaking),
                                    tint = RoseAlert
                                )
                            }
                        }
                        IconButton(
                            onClick = {
                                stopSpeaking()
                                signOut(
                                    credentialManager = credentialManager,
                                    scope = scope
                                )
                            },
                            modifier = Modifier.testTag("sign_out_button")
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.Logout,
                                contentDescription = stringResource(id = R.string.sign_out)
                            )
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.surface
                    )
                )
            },
            bottomBar = {
                if (!isWideScreen) {
                    NavigationBar {
                        HidayatTab.entries.forEach { tab ->
                            val selected = currentTab == tab
                            NavigationBarItem(
                                selected = selected,
                                onClick = { currentTab = tab },
                                icon = {
                                    Icon(
                                        imageVector = if (selected) tab.selectedIcon else tab.unselectedIcon,
                                        contentDescription = stringResource(id = tab.labelRes)
                                    )
                                },
                                label = { Text(stringResource(id = tab.labelRes)) },
                                modifier = Modifier.testTag("nav_tab_${tab.routeId}")
                            )
                        }
                    }
                }
            }
        ) { innerPadding ->
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
            ) {
                if (isWideScreen) {
                    NavigationRail(
                        modifier = Modifier.fillMaxHeight()
                    ) {
                        HidayatTab.entries.forEach { tab ->
                            val selected = currentTab == tab
                            NavigationRailItem(
                                selected = selected,
                                onClick = { currentTab = tab },
                                icon = {
                                    Icon(
                                        imageVector = if (selected) tab.selectedIcon else tab.unselectedIcon,
                                        contentDescription = stringResource(id = tab.labelRes)
                                    )
                                },
                                label = { Text(stringResource(id = tab.labelRes)) },
                                modifier = Modifier.testTag("nav_rail_${tab.routeId}")
                            )
                        }
                    }
                }

                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                ) {
                    when (currentTab) {
                        HidayatTab.SCHEDULE -> DashboardAndScheduleScreen(
                            viewModel = productivityViewModel,
                            userDisplayName = userDisplayName,
                            isSpeaking = isSpeaking,
                            onSpeakText = speakText,
                            onStopSpeaking = stopSpeaking,
                            modifier = Modifier.fillMaxSize()
                        )
                        HidayatTab.ASSISTANT -> AssistantChatScreen(
                            assistantViewModel = assistantViewModel,
                            onSaveReplyToNotes = { title, content ->
                                productivityViewModel.addNote(
                                    title = title,
                                    content = content,
                                    tag = NoteTag.AI_INSIGHT.name
                                )
                            },
                            onSpeakText = speakText,
                            modifier = Modifier.fillMaxSize()
                        )
                        HidayatTab.STUDIO -> StudioAndVisionScreen(
                            assistantViewModel = assistantViewModel,
                            onSaveAnalysisToNotes = { title, content ->
                                productivityViewModel.addNote(
                                    title = title,
                                    content = content,
                                    tag = NoteTag.VISION_ANALYSIS.name
                                )
                            },
                            onSpeakText = speakText,
                            modifier = Modifier.fillMaxSize()
                        )
                        HidayatTab.NOTES -> NotesScreen(
                            viewModel = productivityViewModel,
                            onSpeakText = speakText,
                            modifier = Modifier.fillMaxSize()
                        )
                    }
                }
            }
        }
    }
}
