package com.example.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.BookmarkAdd
import androidx.compose.material.icons.filled.CalendarToday
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.RadioButtonUnchecked
import androidx.compose.material.icons.filled.RecordVoiceOver
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.StopCircle
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.R
import com.example.data.NoteTag
import com.example.data.ScheduleCategory
import com.example.data.ScheduleItem
import com.example.data.SchedulePriority
import com.example.ui.theme.AmberGold
import com.example.ui.theme.ElectricCyan
import com.example.ui.theme.EmeraldSuccess
import com.example.ui.theme.RoseAlert
import com.example.ui.theme.SapphireDark
import com.example.ui.theme.SapphireNavy
import com.example.viewmodel.AutoScheduleUiState
import com.example.viewmodel.DataUiState
import com.example.viewmodel.ProductivityViewModel

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun DashboardAndScheduleScreen(
    viewModel: ProductivityViewModel,
    userDisplayName: String,
    isSpeaking: Boolean,
    onSpeakText: (String) -> Unit,
    onStopSpeaking: () -> Unit,
    modifier: Modifier = Modifier
) {
    val schedulesState by viewModel.schedulesState.collectAsStateWithLifecycle()
    val selectedDateStr by viewModel.selectedDateStr.collectAsStateWithLifecycle()
    val autoScheduleState by viewModel.autoScheduleState.collectAsStateWithLifecycle()
    val dailyBriefingText by viewModel.dailyBriefingText.collectAsStateWithLifecycle()
    val isGeneratingBriefing by viewModel.isGeneratingBriefing.collectAsStateWithLifecycle()

    var autoPromptInput by rememberSaveable { mutableStateOf("") }
    var showAddDialog by rememberSaveable { mutableStateOf(false) }
    var selectedCategoryFilter by rememberSaveable { mutableStateOf("ALL") }

    val allSchedules = (schedulesState as? DataUiState.Success)?.data.orEmpty()
    val filteredSchedules = remember(allSchedules, selectedCategoryFilter) {
        if (selectedCategoryFilter == "ALL") {
            allSchedules
        } else {
            allSchedules.filter { it.category == selectedCategoryFilter }
        }
    }

    val completedCount = remember(allSchedules) { allSchedules.count { it.completed } }
    val totalCount = allSchedules.size
    val progressFraction = if (totalCount > 0) completedCount.toFloat() / totalCount.toFloat() else 0f

    Box(modifier = modifier.fillMaxSize()) {
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .testTag("dashboard_schedule_list"),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // 1. Hero Executive Card with Logo & Completion Progress
            item {
                HeroExecutiveBanner(
                    userDisplayName = userDisplayName,
                    selectedDateStr = selectedDateStr,
                    completedCount = completedCount,
                    totalCount = totalCount,
                    progressFraction = progressFraction
                )
            }

            // 2. Daily AI Productivity Briefing Card with TTS Read-Aloud
            item {
                DailyBriefingCard(
                    briefingText = dailyBriefingText,
                    isGenerating = isGeneratingBriefing,
                    isSpeaking = isSpeaking,
                    onGenerateBriefing = { viewModel.generateDailyBriefing(allSchedules) },
                    onSpeakBriefing = {
                        if (isSpeaking) onStopSpeaking() else onSpeakText(dailyBriefingText)
                    },
                    onSaveToNotes = {
                        viewModel.addNote(
                            title = "Briefing Produktivitas ($selectedDateStr)",
                            content = dailyBriefingText,
                            tag = NoteTag.DAILY_BRIEF.name
                        )
                    }
                )
            }

            // 3. AI Smart Auto-Scheduler Card
            item {
                AutoSchedulerCard(
                    promptInput = autoPromptInput,
                    onPromptChange = { autoPromptInput = it },
                    autoScheduleState = autoScheduleState,
                    onGenerateSchedule = {
                        viewModel.generateAutoSchedule(autoPromptInput, selectedDateStr)
                    },
                    onQuickTemplateClick = { template ->
                        autoPromptInput = template
                    }
                )
            }

            // 4. Category Filter Chips & Section Header
            item {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Agenda & Jadwal Aktif ($totalCount)",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold
                        )
                        TextButton(
                            onClick = { showAddDialog = true },
                            modifier = Modifier.testTag("open_add_schedule_dialog_btn")
                        ) {
                            Icon(
                                imageVector = Icons.Default.Add,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(text = stringResource(id = R.string.add_manual_schedule))
                        }
                    }

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        FilterChip(
                            selected = selectedCategoryFilter == "ALL",
                            onClick = { selectedCategoryFilter = "ALL" },
                            label = { Text("Semua") }
                        )
                        ScheduleCategory.entries.forEach { cat ->
                            FilterChip(
                                selected = selectedCategoryFilter == cat.name,
                                onClick = { selectedCategoryFilter = cat.name },
                                label = { Text(cat.label) }
                            )
                        }
                    }
                }
            }

            // 5. Schedule Items List or Loading / Error / Empty State
            when (val state = schedulesState) {
                is DataUiState.Loading -> {
                    item {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(32.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            CircularProgressIndicator()
                        }
                    }
                }
                is DataUiState.Error -> {
                    item {
                        Card(
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.errorContainer
                            ),
                            shape = RoundedCornerShape(16.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                text = state.message,
                                color = MaterialTheme.colorScheme.onErrorContainer,
                                modifier = Modifier.padding(16.dp),
                                style = MaterialTheme.typography.bodyMedium
                            )
                        }
                    }
                }
                is DataUiState.Success -> {
                    if (filteredSchedules.isEmpty()) {
                        item {
                            EmptySchedulePlaceholder(
                                onAddManualClick = { showAddDialog = true }
                            )
                        }
                    } else {
                        items(
                            items = filteredSchedules,
                            key = { it.id }
                        ) { item ->
                            ScheduleItemCard(
                                item = item,
                                onToggleCompleted = {
                                    viewModel.toggleScheduleCompleted(item.id, !item.completed)
                                },
                                onSpeakItem = {
                                    onSpeakText("Jadwal pukul ${item.timeSlot}: ${item.title}. ${item.description}")
                                },
                                onDelete = {
                                    viewModel.deleteSchedule(item.id)
                                }
                            )
                        }
                    }
                }
            }

            item {
                Spacer(modifier = Modifier.height(76.dp))
            }
        }

        FloatingActionButton(
            onClick = { showAddDialog = true },
            containerColor = MaterialTheme.colorScheme.primary,
            contentColor = MaterialTheme.colorScheme.onPrimary,
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(20.dp)
                .testTag("add_manual_schedule_fab")
        ) {
            Icon(
                imageVector = Icons.Default.Add,
                contentDescription = stringResource(id = R.string.add_manual_schedule)
            )
        }
    }

    if (showAddDialog) {
        AddManualScheduleDialog(
            onDismiss = { showAddDialog = false },
            onConfirm = { title, desc, category, priority, timeSlot ->
                viewModel.addManualSchedule(
                    title = title,
                    description = desc,
                    category = category,
                    priority = priority,
                    timeSlot = timeSlot
                )
                showAddDialog = false
            }
        )
    }
}

@Composable
private fun HeroExecutiveBanner(
    userDisplayName: String,
    selectedDateStr: String,
    completedCount: Int,
    totalCount: Int,
    progressFraction: Float
) {
    val gradient = Brush.linearGradient(
        colors = listOf(SapphireDark, SapphireNavy, Color(0xFF0369A1))
    )

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(gradient)
                .padding(20.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Image(
                    painter = painterResource(id = R.drawable.logo_hidayat),
                    contentDescription = "Logo Hidayat AI",
                    modifier = Modifier
                        .size(56.dp)
                        .clip(RoundedCornerShape(14.dp)),
                    contentScale = ContentScale.Crop
                )
                Spacer(modifier = Modifier.width(14.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Halo, $userDisplayName",
                        style = MaterialTheme.typography.titleLarge,
                        color = Color.White,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.CalendarToday,
                            contentDescription = null,
                            tint = ElectricCyan,
                            modifier = Modifier.size(14.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "Target Produktivitas • $selectedDateStr",
                            style = MaterialTheme.typography.labelMedium,
                            color = Color(0xFFE0F2FE)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(18.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Progres Eksekusi Harian",
                    style = MaterialTheme.typography.labelLarge,
                    color = Color.White
                )
                Text(
                    text = "$completedCount / $totalCount Selesai (${(progressFraction * 100).toInt()}%)",
                    style = MaterialTheme.typography.labelLarge,
                    color = AmberGold,
                    fontWeight = FontWeight.Bold
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            LinearProgressIndicator(
                progress = { progressFraction },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(10.dp)
                    .clip(RoundedCornerShape(50)),
                color = EmeraldSuccess,
                trackColor = Color.White.copy(alpha = 0.2f)
            )
        }
    }
}

@Composable
private fun DailyBriefingCard(
    briefingText: String,
    isGenerating: Boolean,
    isSpeaking: Boolean,
    onGenerateBriefing: () -> Unit,
    onSpeakBriefing: () -> Unit,
    onSaveToNotes: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(modifier = Modifier.padding(18.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.AutoAwesome,
                        contentDescription = null,
                        tint = AmberGold,
                        modifier = Modifier.size(22.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = stringResource(id = R.string.daily_briefing_title),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                }

                Row {
                    IconButton(
                        onClick = onSpeakBriefing,
                        modifier = Modifier.testTag("speak_briefing_button")
                    ) {
                        Icon(
                            imageVector = if (isSpeaking) Icons.Default.StopCircle else Icons.Default.RecordVoiceOver,
                            contentDescription = stringResource(id = R.string.speak_briefing),
                            tint = if (isSpeaking) RoseAlert else MaterialTheme.colorScheme.primary
                        )
                    }
                    IconButton(
                        onClick = onSaveToNotes,
                        modifier = Modifier.testTag("save_briefing_note_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.BookmarkAdd,
                            contentDescription = stringResource(id = R.string.save_to_notes),
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = briefingText,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(modifier = Modifier.height(12.dp))

            OutlinedButton(
                onClick = onGenerateBriefing,
                enabled = !isGenerating,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("generate_briefing_button"),
                shape = RoundedCornerShape(12.dp)
            ) {
                if (isGenerating) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(18.dp),
                        strokeWidth = 2.dp
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Menyusun Briefing AI...")
                } else {
                    Icon(
                        imageVector = Icons.Default.AutoAwesome,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Perbarui Briefing AI dari Jadwal Saat Ini")
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun AutoSchedulerCard(
    promptInput: String,
    onPromptChange: (String) -> Unit,
    autoScheduleState: AutoScheduleUiState,
    onGenerateSchedule: () -> Unit,
    onQuickTemplateClick: (String) -> Unit
) {
    val templates = listOf(
        "Fokus kerja proyek pagi, rapat tim jam 13:00, olahraga sore, & belajar malam",
        "Rutinitas produktif WFH: deep work coding 3 jam, review dokumen, dan istirahat seimbang",
        "Persiapan presentasi bisnis, riset pasar dengan AI, dan evaluasi mingguan"
    )

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(22.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.55f)
        )
    ) {
        Column(modifier = Modifier.padding(18.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Surface(
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(36.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = Icons.Default.Schedule,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onPrimary,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
                Spacer(modifier = Modifier.width(10.dp))
                Column {
                    Text(
                        text = stringResource(id = R.string.auto_schedule_title),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "AI menyusun blok waktu & prioritas otomatis ke Cloud",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            OutlinedTextField(
                value = promptInput,
                onValueChange = onPromptChange,
                placeholder = { Text(stringResource(id = R.string.auto_schedule_hint)) },
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("auto_schedule_prompt_input"),
                minLines = 2,
                maxLines = 4,
                shape = RoundedCornerShape(14.dp)
            )

            Spacer(modifier = Modifier.height(10.dp))

            Text(
                text = "Ide Cepat:",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(modifier = Modifier.height(6.dp))

            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                templates.forEachIndexed { idx, tpl ->
                    AssistChip(
                        onClick = { onQuickTemplateClick(tpl) },
                        label = {
                            Text(
                                text = tpl,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                style = MaterialTheme.typography.labelSmall
                            )
                        },
                        modifier = Modifier.testTag("auto_schedule_template_$idx")
                    )
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            val isLoading = autoScheduleState is AutoScheduleUiState.Loading

            Button(
                onClick = onGenerateSchedule,
                enabled = !isLoading,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(50.dp)
                    .testTag("auto_schedule_button"),
                shape = RoundedCornerShape(14.dp)
            ) {
                if (isLoading) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(20.dp),
                        color = MaterialTheme.colorScheme.onPrimary,
                        strokeWidth = 2.dp
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Text("Hidayat AI Sedang Menyusun Jadwal...")
                } else {
                    Icon(
                        imageVector = Icons.Default.AutoAwesome,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = stringResource(id = R.string.generate_schedule_btn),
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            AnimatedVisibility(visible = autoScheduleState is AutoScheduleUiState.Error) {
                val err = (autoScheduleState as? AutoScheduleUiState.Error)?.message.orEmpty()
                Text(
                    text = err,
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(top = 10.dp)
                )
            }
        }
    }
}

@Composable
private fun ScheduleItemCard(
    item: ScheduleItem,
    onToggleCompleted: () -> Unit,
    onSpeakItem: () -> Unit,
    onDelete: () -> Unit
) {
    val categoryEnum = ScheduleCategory.fromRaw(item.category)
    val priorityEnum = SchedulePriority.fromRaw(item.priority)

    val priorityColor = when (priorityEnum) {
        SchedulePriority.HIGH -> RoseAlert
        SchedulePriority.MEDIUM -> AmberGold
        SchedulePriority.LOW -> EmeraldSuccess
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("schedule_card_${item.id}"),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (item.completed) {
                MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f)
            } else {
                MaterialTheme.colorScheme.surface
            }
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = if (item.completed) 0.dp else 2.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { onToggleCompleted() }
                .padding(16.dp),
            verticalAlignment = Alignment.Top
        ) {
            IconButton(
                onClick = onToggleCompleted,
                modifier = Modifier
                    .size(36.dp)
                    .testTag("toggle_schedule_${item.id}")
            ) {
                Icon(
                    imageVector = if (item.completed) Icons.Default.CheckCircle else Icons.Default.RadioButtonUnchecked,
                    contentDescription = "Ubah status selesai",
                    tint = if (item.completed) EmeraldSuccess else MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(26.dp)
                )
            }

            Spacer(modifier = Modifier.width(10.dp))

            Column(modifier = Modifier.weight(1f)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Surface(
                        color = MaterialTheme.colorScheme.primaryContainer,
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Text(
                            text = item.timeSlot,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                        )
                    }

                    Surface(
                        color = priorityColor.copy(alpha = 0.16f),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Text(
                            text = priorityEnum.label,
                            style = MaterialTheme.typography.labelSmall,
                            color = priorityColor,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                        )
                    }

                    Surface(
                        color = MaterialTheme.colorScheme.surfaceVariant,
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Text(
                            text = categoryEnum.label,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                        )
                    }

                    if (item.aiGenerated) {
                        Surface(
                            color = AmberGold.copy(alpha = 0.2f),
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Text(
                                text = "AI Auto",
                                style = MaterialTheme.typography.labelSmall,
                                color = Color(0xFFB45309),
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp)
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(6.dp))

                Text(
                    text = item.title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    textDecoration = if (item.completed) TextDecoration.LineThrough else TextDecoration.None,
                    color = if (item.completed) {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    } else {
                        MaterialTheme.colorScheme.onSurface
                    }
                )

                if (item.description.isNotBlank()) {
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = item.description,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                IconButton(
                    onClick = onSpeakItem,
                    modifier = Modifier.size(36.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.RecordVoiceOver,
                        contentDescription = "Bacakan jadwal",
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(18.dp)
                    )
                }
                IconButton(
                    onClick = onDelete,
                    modifier = Modifier
                        .size(36.dp)
                        .testTag("delete_schedule_${item.id}")
                ) {
                    Icon(
                        imageVector = Icons.Default.DeleteOutline,
                        contentDescription = "Hapus jadwal",
                        tint = MaterialTheme.colorScheme.error,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun EmptySchedulePlaceholder(
    onAddManualClick: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(28.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(
                imageVector = Icons.Default.Schedule,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(48.dp)
            )
            Spacer(modifier = Modifier.height(12.dp))
            Text(
                text = stringResource(id = R.string.empty_schedules),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(14.dp))
            OutlinedButton(onClick = onAddManualClick) {
                Icon(imageVector = Icons.Default.Add, contentDescription = null)
                Spacer(modifier = Modifier.width(6.dp))
                Text(stringResource(id = R.string.add_manual_schedule))
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun AddManualScheduleDialog(
    onDismiss: () -> Unit,
    onConfirm: (title: String, desc: String, category: String, priority: String, timeSlot: String) -> Unit
) {
    var title by rememberSaveable { mutableStateOf("") }
    var description by rememberSaveable { mutableStateOf("") }
    var timeSlot by rememberSaveable { mutableStateOf("09:00 - 10:30") }
    var selectedCategory by rememberSaveable { mutableStateOf(ScheduleCategory.WORK.name) }
    var selectedPriority by rememberSaveable { mutableStateOf(SchedulePriority.HIGH.name) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = "Tambah Jadwal Baru",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold
            )
        },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it },
                    label = { Text("Nama Aktivitas") },
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("manual_schedule_title_input")
                )

                OutlinedTextField(
                    value = timeSlot,
                    onValueChange = { timeSlot = it },
                    label = { Text("Rentang Waktu (misal 09:00 - 10:30)") },
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("manual_schedule_time_input")
                )

                OutlinedTextField(
                    value = description,
                    onValueChange = { description = it },
                    label = { Text("Catatan / Langkah Eksekusi") },
                    minLines = 2,
                    maxLines = 3,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("manual_schedule_desc_input")
                )

                Text(
                    text = "Kategori:",
                    style = MaterialTheme.typography.labelMedium
                )
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    ScheduleCategory.entries.forEach { cat ->
                        FilterChip(
                            selected = selectedCategory == cat.name,
                            onClick = { selectedCategory = cat.name },
                            label = { Text(cat.label) }
                        )
                    }
                }

                Text(
                    text = "Prioritas:",
                    style = MaterialTheme.typography.labelMedium
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    SchedulePriority.entries.forEach { prio ->
                        FilterChip(
                            selected = selectedPriority == prio.name,
                            onClick = { selectedPriority = prio.name },
                            label = { Text(prio.label) }
                        )
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (title.isNotBlank()) {
                        onConfirm(title, description, selectedCategory, selectedPriority, timeSlot)
                    }
                },
                enabled = title.isNotBlank(),
                modifier = Modifier.testTag("confirm_add_schedule_button")
            ) {
                Text("Simpan")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Batal")
            }
        }
    )
}
