package com.example.bilidownloader.features.home

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import com.example.bilidownloader.core.database.HistoryEntity
import com.example.bilidownloader.di.AppViewModelProvider
import com.example.bilidownloader.features.home.components.*

/**
 * 首页主屏幕 (HomeScreen).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    viewModel: HomeViewModel = viewModel(factory = AppViewModelProvider.Factory),
    authViewModel: AuthViewModel = viewModel(factory = AppViewModelProvider.Factory),
    historyViewModel: HistoryViewModel = viewModel(factory = AppViewModelProvider.Factory),
    onNavigateToTranscribe: (String, String) -> Unit,
    onNavigateToLogin: () -> Unit
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    val notificationPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission(),
        onResult = { }
    )
    LaunchedEffect(Unit) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        }
    }

    val state by viewModel.state.collectAsState()
    val currentUser by authViewModel.currentUser.collectAsState()
    val userList by authViewModel.userList.collectAsState()
    val historyList by historyViewModel.historyList.collectAsState()

    val historyTab by historyViewModel.historyTab.collectAsState()
    val cloudHistoryList by historyViewModel.cloudHistoryList.collectAsState()
    val isCloudHistoryLoading by historyViewModel.isCloudHistoryLoading.collectAsState()
    val cloudHistoryError by historyViewModel.cloudHistoryError.collectAsState()

    var inputText by remember { mutableStateOf("") }
    var isSelectionMode by remember { mutableStateOf(false) }
    val selectedItems = remember { mutableStateListOf<HistoryEntity>() }

    var showAccountDialog by remember { mutableStateOf(false) }
    var showManualCookieInput by remember { mutableStateOf(false) }
    var showSubtitleDialog by remember { mutableStateOf(false) }
    var showManualDialog by remember { mutableStateOf(false) }

    fun exitSelectionMode() {
        isSelectionMode = false
        selectedItems.clear()
    }

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) authViewModel.syncCookieToUserDB()
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    // 当用户切换或退出登录时，清理云端历史缓存
    LaunchedEffect(currentUser) {
        historyViewModel.clearCloudHistory()
        if (historyTab == HistoryTab.Cloud && currentUser != null) {
            historyViewModel.refreshCloudHistory()
        }
    }

    BackHandler(enabled = isSelectionMode || state !is HomeState.Idle) {
        if (isSelectionMode) exitSelectionMode()
        else if (state !is HomeState.Idle) viewModel.reset()
    }

    // --- Dialogs ---
    if (showAccountDialog) {
        AccountDialog(
            userList = userList,
            currentUser = currentUser,
            onDismiss = { showAccountDialog = false },
            onSwitchAccount = { authViewModel.switchAccount(it) },
            onLogout = { authViewModel.logoutAndRemove(it) },
            onManualAdd = { showManualCookieInput = true; showAccountDialog = false },
            onQuitToGuest = { authViewModel.quitToGuestMode(); showAccountDialog = false }
        )
    }

    if (showManualCookieInput) {
        ManualCookieInputDialog(
            onDismiss = { showManualCookieInput = false },
            onConfirm = {
                authViewModel.addOrUpdateAccount(it)
                showManualCookieInput = false
                showAccountDialog = true
            },
            onSmsLogin = { showManualCookieInput = false; onNavigateToLogin() }
        )
    }

    if (showSubtitleDialog && state is HomeState.ChoiceSelect) {
        SubtitleDialog(
            currentState = state as HomeState.ChoiceSelect,
            viewModel = viewModel,
            onDismiss = { showSubtitleDialog = false },
            onNavigateToTranscribe = { path, title ->
                showSubtitleDialog = false
                onNavigateToTranscribe(path, title)
            }
        )
    }

    if (showManualDialog) ManualDialog(onDismiss = { showManualDialog = false })

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text(if (isSelectionMode) "已选 ${selectedItems.size} 项" else "B 站下载器") },
                navigationIcon = {
                    if (state !is HomeState.Idle && !isSelectionMode) {
                        IconButton(onClick = { viewModel.reset() }) {
                            Icon(Icons.Default.ArrowBack, "返回")
                        }
                    }
                },
                actions = {
                    if (isSelectionMode) {
                        IconButton(onClick = { historyViewModel.deleteHistories(selectedItems.toList()); exitSelectionMode() }) {
                            Icon(Icons.Default.Delete, "删除")
                        }
                    } else {
                        IconButton(onClick = { showManualDialog = true }) {
                            Icon(
                                Icons.Default.HelpOutline,
                                "使用手册",
                                tint = MaterialTheme.colorScheme.primary
                            )
                        }
                        IconButton(onClick = { showAccountDialog = true }) {
                            if (currentUser != null) {
                                AsyncImage(
                                    model = currentUser?.face,
                                    contentDescription = "Avatar",
                                    modifier = Modifier
                                        .size(28.dp)
                                        .clip(CircleShape)
                                )
                            } else {
                                Icon(Icons.Default.Person, contentDescription = "账号")
                            }
                        }
                    }
                }
            )
        }
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 24.dp, vertical = 16.dp)
        ) {
            when (val currentState = state) {
                is HomeState.Idle -> {
                    HomeIdleContent(
                        inputText = inputText,
                        onInputTextChange = { inputText = it },
                        historyTab = historyTab,
                        historyList = historyList,
                        isSelectionMode = isSelectionMode,
                        selectedItems = selectedItems,
                        historyViewModel = historyViewModel,
                        currentUser = currentUser,
                        cloudHistoryList = cloudHistoryList,
                        isCloudHistoryLoading = isCloudHistoryLoading,
                        cloudHistoryError = cloudHistoryError,
                        onAnalyze = { viewModel.analyzeInput(inputText) },
                        onHistoryClick = { history ->
                            if (isSelectionMode) {
                                if (selectedItems.contains(history)) selectedItems.remove(history)
                                else selectedItems.add(history)
                            } else {
                                inputText = history.bvid
                            }
                        },
                        onHistoryLongClick = { history ->
                            if (!isSelectionMode) {
                                isSelectionMode = true
                                selectedItems.add(history)
                            }
                        },
                        onManualCookieClick = { showManualCookieInput = true }
                    )
                }

                is HomeState.Analyzing -> CircularProgressIndicator(
                    modifier = Modifier
                        .align(
                            Alignment.TopCenter
                        )
                        .padding(top = 100.dp)
                )
                is HomeState.ChoiceSelect -> {
                    HomeChoiceSelectContent(
                        state = currentState,
                        viewModel = viewModel,
                        onShowSubtitleDialog = { showSubtitleDialog = true },
                        formatTime = ::formatTime
                    )
                }
                is HomeState.Processing -> {
                    HomeProcessingContent(state = currentState, viewModel = viewModel)
                }
                is HomeState.Success -> {
                    HomeSuccessContent(state = currentState, onReset = { viewModel.reset() })
                }
                is HomeState.Error -> {
                    HomeErrorContent(state = currentState, onReset = { viewModel.reset() })
                }
            }
        }
    }
}

private fun formatTime(seconds: Long): String {
    val h = seconds / 3600
    val m = (seconds % 3600) / 60
    val s = seconds % 60
    return if (h > 0) String.format("%02d:%02d:%02d", h, m, s) else String.format("%02d:%02d", m, s)
}
