package com.example

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DeleteForever
import androidx.compose.material.icons.filled.DownloadForOffline
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Layers
import androidx.compose.material.icons.filled.PhoneAndroid
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Terminal
import androidx.compose.material.icons.filled.WarningAmber
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.ui.AppTab
import com.example.ui.DowngradePromptData
import com.example.ui.MainViewModel
import com.example.ui.components.M3PrimaryButton
import com.example.ui.components.M3TopHeader
import com.example.ui.screens.ConsoleScreen
import com.example.ui.screens.InstalledStatusScreen
import com.example.ui.screens.ManualXapkScreen
import com.example.ui.screens.SettingsScreen
import com.example.ui.screens.VersionListScreen
import com.example.ui.theme.MagisterVanGisterTheme
import com.example.ui.theme.StatusWarning

class MainActivity : ComponentActivity() {

    private val viewModel: MainViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        setContent {
            MagisterVanGisterTheme {
                val uiState by viewModel.uiState.collectAsStateWithLifecycle()

                val uninstallLauncher = rememberLauncherForActivityResult(
                    contract = ActivityResultContracts.StartActivityForResult()
                ) { _ ->
                    viewModel.onUninstallReturned()
                }

                LaunchedEffect(Unit) {
                    viewModel.checkInstallPermission()
                    viewModel.checkInstalledMagister()
                }

                LaunchedEffect(Unit) {
                    viewModel.launchUninstallEvent.collect { pkg ->
                        val intent = Intent(Intent.ACTION_DELETE).apply {
                            data = Uri.parse("package:$pkg")
                            putExtra(Intent.EXTRA_RETURN_RESULT, true)
                        }
                        try {
                            uninstallLauncher.launch(intent)
                        } catch (e: Exception) {
                            val fallbackIntent = Intent(Intent.ACTION_UNINSTALL_PACKAGE).apply {
                                data = Uri.parse("package:$pkg")
                                putExtra(Intent.EXTRA_RETURN_RESULT, true)
                            }
                            try {
                                uninstallLauncher.launch(fallbackIntent)
                            } catch (e2: Exception) {
                                // Ignore
                            }
                        }
                    }
                }

                BackHandler(enabled = uiState.activeTab != AppTab.VERSIONS) {
                    viewModel.setTab(AppTab.VERSIONS)
                }

                // Downgrade uninstall confirmation popup dialog with password warning
                uiState.downgradePrompt?.let { prompt ->
                    DowngradeUninstallDialog(
                        prompt = prompt,
                        onConfirm = { viewModel.confirmUninstallAndReinstall() },
                        onDismiss = { viewModel.dismissDowngradePrompt() }
                    )
                }

                Scaffold(
                    modifier = Modifier.fillMaxSize(),
                    topBar = {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .windowInsetsPadding(WindowInsets.statusBars)
                        ) {
                            M3TopHeader(
                                title = stringResource(R.string.app_name),
                                subtitle = stringResource(R.string.app_subtitle),
                                onRefresh = {
                                    viewModel.loadReleases()
                                    viewModel.checkInstalledMagister()
                                    viewModel.checkInstallPermission()
                                }
                            )

                            AnimatedVisibility(
                                visible = uiState.userNotice != null,
                                enter = slideInVertically() + fadeIn(),
                                exit = slideOutVertically() + fadeOut()
                            ) {
                                uiState.userNotice?.let { notice ->
                                    M3NotificationBanner(
                                        message = notice,
                                        onDismiss = { viewModel.dismissUserNotice() }
                                    )
                                }
                            }
                        }
                    },
                    bottomBar = {
                        NavigationBar(
                            modifier = Modifier
                                .fillMaxWidth()
                                .windowInsetsPadding(WindowInsets.navigationBars)
                        ) {
                            NavigationBarItem(
                                selected = uiState.activeTab == AppTab.VERSIONS,
                                onClick = { viewModel.setTab(AppTab.VERSIONS) },
                                icon = { Icon(Icons.Default.Layers, contentDescription = null) },
                                label = { Text(stringResource(R.string.tab_versions)) },
                                modifier = Modifier.testTag("nav_tab_versions")
                            )

                            NavigationBarItem(
                                selected = uiState.activeTab == AppTab.MANUAL,
                                onClick = { viewModel.setTab(AppTab.MANUAL) },
                                icon = { Icon(Icons.Default.DownloadForOffline, contentDescription = null) },
                                label = { Text(stringResource(R.string.tab_manual)) },
                                modifier = Modifier.testTag("nav_tab_manual")
                            )

                            NavigationBarItem(
                                selected = uiState.activeTab == AppTab.STATUS,
                                onClick = { viewModel.setTab(AppTab.STATUS) },
                                icon = { Icon(Icons.Default.PhoneAndroid, contentDescription = null) },
                                label = { Text(stringResource(R.string.tab_installed)) },
                                modifier = Modifier.testTag("nav_tab_status")
                            )

                            NavigationBarItem(
                                selected = uiState.activeTab == AppTab.CONSOLE,
                                onClick = { viewModel.setTab(AppTab.CONSOLE) },
                                icon = { Icon(Icons.Default.Terminal, contentDescription = null) },
                                label = { Text(stringResource(R.string.tab_console)) },
                                modifier = Modifier.testTag("nav_tab_console")
                            )

                            NavigationBarItem(
                                selected = uiState.activeTab == AppTab.SETTINGS,
                                onClick = { viewModel.setTab(AppTab.SETTINGS) },
                                icon = { Icon(Icons.Default.Settings, contentDescription = null) },
                                label = { Text(stringResource(R.string.tab_settings)) },
                                modifier = Modifier.testTag("nav_tab_settings")
                            )
                        }
                    },
                    containerColor = MaterialTheme.colorScheme.background
                ) { innerPadding ->
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(innerPadding)
                    ) {
                        when (uiState.activeTab) {
                            AppTab.VERSIONS -> {
                                VersionListScreen(
                                    state = uiState,
                                    onDownloadInstall = { viewModel.requestDownloadAndInstall(it) },
                                    onResetDownload = { viewModel.resetDownloadState() },
                                    onNavigateToStatus = { viewModel.setTab(AppTab.STATUS) }
                                )
                            }

                            AppTab.MANUAL -> {
                                ManualXapkScreen(
                                    state = uiState,
                                    onDownloadToPublic = { viewModel.downloadToPublicDownloads(it) }
                                )
                            }

                            AppTab.STATUS -> {
                                InstalledStatusScreen(
                                    state = uiState,
                                    onRefreshStatus = { viewModel.checkInstalledMagister() },
                                    onCustomPackageChange = { viewModel.onCustomPackageInputChanged(it) },
                                    onTestCustomPackage = { viewModel.testCustomPackageCheck() },
                                    onNavigateToVersions = { viewModel.setTab(AppTab.VERSIONS) }
                                )
                            }

                            AppTab.CONSOLE -> {
                                ConsoleScreen(
                                    state = uiState,
                                    onClearLogs = { viewModel.clearConsoleLogs() }
                                )
                            }

                            AppTab.SETTINGS -> {
                                SettingsScreen(
                                    state = uiState,
                                    onSaveRepository = { owner, repo -> viewModel.updateRepositoryConfig(owner, repo) },
                                    onResetRepository = { viewModel.resetToDefaultRepo() },
                                    onClearCache = { viewModel.clearCache() },
                                    onCheckPermission = { viewModel.checkInstallPermission() }
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        viewModel.checkInstallPermission()
        viewModel.checkInstalledMagister()
    }
}

@Composable
private fun M3NotificationBanner(
    message: String,
    onDismiss: () -> Unit
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 6.dp),
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.primaryContainer,
        contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
        shadowElevation = 2.dp
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.weight(1f)
            ) {
                Icon(
                    imageVector = Icons.Default.Info,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(modifier = Modifier.width(10.dp))
                Text(
                    text = message,
                    style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Medium)
                )
            }

            IconButton(
                onClick = onDismiss,
                modifier = Modifier.size(24.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Close,
                    contentDescription = "Sluiten",
                    modifier = Modifier.size(16.dp)
                )
            }
        }
    }
}

@Composable
private fun DowngradeUninstallDialog(
    prompt: DowngradePromptData,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = {
            Icon(
                imageVector = Icons.Default.WarningAmber,
                contentDescription = null,
                tint = StatusWarning,
                modifier = Modifier.size(32.dp)
            )
        },
        title = {
            Text(
                text = stringResource(R.string.downgrade_dialog_title),
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
            )
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                val description = if (prompt.isFromConflictError) {
                    stringResource(
                        R.string.downgrade_dialog_conflict_desc,
                        prompt.targetRelease.versionName
                    )
                } else {
                    stringResource(
                        R.string.downgrade_dialog_desc,
                        prompt.installedVersionName,
                        prompt.targetRelease.versionName
                    )
                }

                Text(
                    text = description,
                    style = MaterialTheme.typography.bodyMedium.copy(
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        lineHeight = 20.sp
                    )
                )

                // Password / Login Warning Banner
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = MaterialTheme.colorScheme.errorContainer,
                    border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.error),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(10.dp)) {
                        Text(
                            text = stringResource(R.string.warning_login_title),
                            style = MaterialTheme.typography.labelMedium.copy(
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onErrorContainer
                            )
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = stringResource(R.string.warning_login_desc),
                            style = MaterialTheme.typography.bodySmall.copy(
                                color = MaterialTheme.colorScheme.onErrorContainer,
                                fontSize = 11.5.sp,
                                lineHeight = 16.sp
                            )
                        )
                    }
                }

                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(10.dp)) {
                        Text(
                            text = "Doelversie: ${prompt.targetRelease.assetName}",
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary
                            )
                        )
                        Text(
                            text = "Geïnstalleerd pakket: ${prompt.installedPackageName}",
                            style = MaterialTheme.typography.labelSmall.copy(
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        )
                    }
                }
            }
        },
        confirmButton = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                M3PrimaryButton(
                    text = stringResource(R.string.downgrade_dialog_btn_uninstall_install),
                    onClick = onConfirm,
                    leadingIcon = {
                        Icon(
                            imageVector = Icons.Default.DeleteForever,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                    },
                    modifier = Modifier.fillMaxWidth(),
                    testTag = "confirm_uninstall_button"
                )

                TextButton(
                    onClick = onDismiss,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("cancel_uninstall_button")
                ) {
                    Text(
                        text = stringResource(R.string.btn_cancel),
                        style = MaterialTheme.typography.bodyMedium.copy(color = MaterialTheme.colorScheme.onSurfaceVariant)
                    )
                }
            }
        },
        dismissButton = null,
        shape = RoundedCornerShape(20.dp),
        containerColor = MaterialTheme.colorScheme.surfaceContainerHighest
    )
}
