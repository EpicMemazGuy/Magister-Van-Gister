package com.example.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.R
import com.example.model.DownloadState
import com.example.model.MagisterRelease
import com.example.ui.MainUiState
import com.example.ui.components.M3Badge
import com.example.ui.components.M3Card
import com.example.ui.components.M3PrimaryButton
import com.example.ui.components.M3ProgressSection
import com.example.ui.theme.StatusError
import com.example.ui.theme.StatusSuccess
import com.example.ui.theme.StatusWarning

@Composable
fun VersionListScreen(
    state: MainUiState,
    onDownloadInstall: (MagisterRelease) -> Unit,
    onResetDownload: () -> Unit,
    onNavigateToStatus: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp)
    ) {
        // Installed status banner in Material 3
        InstalledStatusM3Banner(
            state = state,
            onBannerClick = onNavigateToStatus
        )

        Spacer(modifier = Modifier.height(16.dp))

        // Title Header
        Text(
            text = stringResource(R.string.title_available_versions),
            style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
            color = MaterialTheme.colorScheme.onBackground
        )
        Text(
            text = stringResource(R.string.subtitle_available_versions),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Spacer(modifier = Modifier.height(12.dp))

        // Releases List (ONLY 2.7.5 & 2.7.0)
        if (state.isLoading) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = stringResource(R.string.state_downloading),
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
            }
        } else {
            val versionsToShow = if (state.mainVersions.isNotEmpty()) state.mainVersions else state.releases.filter { it.versionName == "2.7.5" || it.versionName == "2.7.0" }

            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                contentPadding = PaddingValues(bottom = 24.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                items(versionsToShow, key = { it.id }) { release ->
                    M3ReleaseCard(
                        release = release,
                        downloadState = state.downloadState,
                        onDownloadInstall = { onDownloadInstall(release) },
                        onResetDownload = onResetDownload
                    )
                }
            }
        }
    }
}

@Composable
private fun InstalledStatusM3Banner(
    state: MainUiState,
    onBannerClick: () -> Unit
) {
    val isInstalled = state.installedAppInfo.isInstalled

    M3Card(
        modifier = Modifier.clickable(onClick = onBannerClick)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.weight(1f)
            ) {
                Surface(
                    modifier = Modifier.size(40.dp),
                    shape = CircleShape,
                    color = if (isInstalled) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = if (isInstalled) Icons.Default.CheckCircle else Icons.Default.Warning,
                            contentDescription = null,
                            tint = if (isInstalled) StatusSuccess else StatusWarning,
                            modifier = Modifier.size(22.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.width(12.dp))

                Column {
                    Text(
                        text = if (isInstalled) {
                            "${stringResource(R.string.status_installed)}: ${state.installedAppInfo.appLabel}"
                        } else {
                            stringResource(R.string.status_not_installed)
                        },
                        style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold)
                    )
                    Text(
                        text = if (isInstalled) {
                            stringResource(R.string.current_version, state.installedAppInfo.versionName, state.installedAppInfo.versionCode)
                        } else {
                            stringResource(R.string.subtitle_available_versions)
                        },
                        style = MaterialTheme.typography.bodySmall.copy(
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            fontSize = 12.sp
                        )
                    )
                }
            }
        }
    }
}

@Composable
private fun M3ReleaseCard(
    release: MagisterRelease,
    downloadState: DownloadState,
    onDownloadInstall: () -> Unit,
    onResetDownload: () -> Unit
) {
    val isThisDownloading = when (downloadState) {
        is DownloadState.Downloading -> downloadState.releaseId == release.id
        is DownloadState.Extracting -> downloadState.releaseId == release.id
        is DownloadState.Installing -> downloadState.releaseId == release.id
        is DownloadState.Completed -> downloadState.releaseId == release.id
        is DownloadState.Error -> downloadState.releaseId == release.id
        else -> false
    }

    val is270 = release.versionName == "2.7.0"
    val is275 = release.versionName == "2.7.5"

    M3Card(isRecommended = is275) {
        Column(modifier = Modifier.fillMaxWidth()) {
            // Header Row: Asset Name and Format Badge
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Magister v${release.versionName} (${release.assetName})",
                    style = MaterialTheme.typography.titleMedium.copy(
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                )

                Row(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (is275) {
                        M3Badge(text = "⭐ Aanbevolen", isRecommended = true)
                    }
                    M3Badge(text = if (release.isXapk) "XAPK" else "APK")
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Warning or Recommendation Banner
            if (is270) {
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.85f),
                    border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.error),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.Warning,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onErrorContainer,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = stringResource(R.string.version_270_warning),
                            style = MaterialTheme.typography.bodySmall.copy(
                                color = MaterialTheme.colorScheme.onErrorContainer,
                                fontWeight = FontWeight.Bold,
                                fontSize = 12.sp
                            )
                        )
                    }
                }
                Spacer(modifier = Modifier.height(8.dp))
            } else if (is275) {
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.6f),
                    border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.6f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = stringResource(R.string.version_275_recommended),
                            style = MaterialTheme.typography.bodySmall.copy(
                                color = MaterialTheme.colorScheme.onPrimaryContainer,
                                fontWeight = FontWeight.SemiBold,
                                fontSize = 12.sp
                            )
                        )
                    }
                }
                Spacer(modifier = Modifier.height(8.dp))
            }

            // Metadata: Size
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Grootte: ${release.formattedSize}",
                    style = MaterialTheme.typography.bodySmall.copy(
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.SemiBold
                    )
                )

                if (release.sha256.isNotBlank()) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.Lock,
                            contentDescription = "SHA-256",
                            modifier = Modifier.size(12.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = "sha256: ${release.sha256.take(16)}…",
                            style = MaterialTheme.typography.bodySmall.copy(
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                fontFamily = FontFamily.Monospace,
                                fontSize = 11.sp
                            )
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Active Progress or Install Button
            if (isThisDownloading) {
                when (downloadState) {
                    is DownloadState.Downloading -> {
                        M3ProgressSection(
                            progress = downloadState.progress,
                            label = downloadState.formattedProgress
                        )
                    }

                    is DownloadState.Extracting -> {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(18.dp),
                                color = MaterialTheme.colorScheme.primary,
                                strokeWidth = 2.dp
                            )
                            Spacer(modifier = Modifier.width(10.dp))
                            Text(
                                text = downloadState.message,
                                style = MaterialTheme.typography.bodySmall.copy(
                                    color = MaterialTheme.colorScheme.primary,
                                    fontWeight = FontWeight.SemiBold
                                )
                            )
                        }
                    }

                    is DownloadState.Installing -> {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(18.dp),
                                strokeWidth = 2.dp
                            )
                            Spacer(modifier = Modifier.width(10.dp))
                            Text(
                                text = downloadState.message,
                                style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.SemiBold)
                            )
                        }
                    }

                    is DownloadState.Completed -> {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.CheckCircle,
                                contentDescription = null,
                                tint = StatusSuccess,
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "Installatiesessie gestart!",
                                style = MaterialTheme.typography.bodySmall.copy(
                                    color = StatusSuccess,
                                    fontWeight = FontWeight.Bold
                                )
                            )
                        }
                    }

                    is DownloadState.Error -> {
                        Column(modifier = Modifier.fillMaxWidth()) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = Icons.Default.Error,
                                    contentDescription = null,
                                    tint = StatusError,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = downloadState.errorMessage,
                                    style = MaterialTheme.typography.bodySmall.copy(color = StatusError)
                                )
                            }
                            Spacer(modifier = Modifier.height(8.dp))
                            M3PrimaryButton(
                                text = stringResource(R.string.btn_retry),
                                onClick = onDownloadInstall,
                                testTag = "retry_button_${release.id}"
                            )
                        }
                    }

                    else -> Unit
                }
            } else {
                M3PrimaryButton(
                    text = stringResource(R.string.btn_download_install),
                    onClick = onDownloadInstall,
                    leadingIcon = {
                        Icon(
                            imageVector = Icons.Default.Download,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                    },
                    testTag = "install_button_${release.id}"
                )
            }
        }
    }
}
