package com.example.ui.screens

import android.content.Intent
import android.net.Uri
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.OpenInNew
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
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
import com.example.ui.components.M3TonalButton
import com.example.ui.theme.StatusError
import com.example.ui.theme.StatusSuccess

@Composable
fun ManualXapkScreen(
    state: MainUiState,
    onDownloadToPublic: (MagisterRelease) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp)
    ) {
        // Title & Description
        Text(
            text = stringResource(R.string.manual_title),
            style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
            color = MaterialTheme.colorScheme.onBackground
        )
        Text(
            text = stringResource(R.string.manual_subtitle),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Spacer(modifier = Modifier.height(12.dp))

        LazyColumn(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
            contentPadding = PaddingValues(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Card 1: Recommend XAPK Installer from Google Play Store
            item {
                M3Card {
                    Column(modifier = Modifier.fillMaxWidth()) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.Info,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(24.dp)
                            )
                            Spacer(modifier = Modifier.width(10.dp))
                            Text(
                                text = stringResource(R.string.xapk_installer_card_title),
                                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }

                        Spacer(modifier = Modifier.height(6.dp))

                        Text(
                            text = stringResource(R.string.xapk_installer_card_desc),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )

                        Spacer(modifier = Modifier.height(12.dp))

                        M3TonalButton(
                            text = stringResource(R.string.btn_open_play_store),
                            onClick = {
                                val playStoreIntent = Intent(
                                    Intent.ACTION_VIEW,
                                    Uri.parse("https://play.google.com/store/apps/details?id=com.wuliang.xapkinstaller&hl=en")
                                ).apply { addFlags(Intent.FLAG_ACTIVITY_NEW_TASK) }
                                context.startActivity(playStoreIntent)
                            },
                            leadingIcon = {
                                Icon(
                                    imageVector = Icons.Default.OpenInNew,
                                    contentDescription = null,
                                    modifier = Modifier.size(18.dp)
                                )
                            },
                            testTag = "open_play_store_button"
                        )
                    }
                }
            }

            // Card 2: Step-by-Step Tutorial
            item {
                M3Card {
                    Column(modifier = Modifier.fillMaxWidth()) {
                        Text(
                            text = stringResource(R.string.tutorial_title),
                            style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                            color = MaterialTheme.colorScheme.primary
                        )

                        Spacer(modifier = Modifier.height(8.dp))

                        val steps = listOf(
                            stringResource(R.string.tutorial_step_1),
                            stringResource(R.string.tutorial_step_2),
                            stringResource(R.string.tutorial_step_3)
                        )

                        steps.forEach { stepText ->
                            Text(
                                text = stepText,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurface,
                                modifier = Modifier.padding(vertical = 2.dp)
                            )
                        }
                    }
                }
            }

            // List Header for XAPK releases
            item {
                Text(
                    text = "XAPK Bestanden in Archief:",
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                    color = MaterialTheme.colorScheme.onBackground,
                    modifier = Modifier.padding(top = 8.dp)
                )
            }

            // List of XAPK releases
            items(state.xapkVersions, key = { it.id }) { release ->
                ManualXapkReleaseCard(
                    release = release,
                    downloadState = state.downloadState,
                    onDownloadToPublic = { onDownloadToPublic(release) }
                )
            }
        }
    }
}

@Composable
private fun ManualXapkReleaseCard(
    release: MagisterRelease,
    downloadState: DownloadState,
    onDownloadToPublic: () -> Unit
) {
    val isThisDownloading = when (downloadState) {
        is DownloadState.Downloading -> downloadState.releaseId == release.id
        is DownloadState.Completed -> downloadState.releaseId == release.id
        is DownloadState.Error -> downloadState.releaseId == release.id
        else -> false
    }

    M3Card {
        Column(modifier = Modifier.fillMaxWidth()) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = release.assetName,
                    style = MaterialTheme.typography.titleMedium.copy(
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                )
                M3Badge(text = "XAPK")
            }

            Spacer(modifier = Modifier.height(4.dp))

            Text(
                text = "Grootte: ${release.formattedSize}",
                style = MaterialTheme.typography.bodySmall.copy(
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.SemiBold
                )
            )

            Spacer(modifier = Modifier.height(6.dp))

            Text(
                text = release.description,
                style = MaterialTheme.typography.bodySmall.copy(
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            )

            Spacer(modifier = Modifier.height(12.dp))

            if (isThisDownloading) {
                when (downloadState) {
                    is DownloadState.Downloading -> {
                        M3ProgressSection(
                            progress = downloadState.progress,
                            label = downloadState.formattedProgress
                        )
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
                                text = "Opgeslagen in Downloads!",
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
                                text = stringResource(R.string.btn_download_to_downloads),
                                onClick = onDownloadToPublic,
                                leadingIcon = {
                                    Icon(
                                        imageVector = Icons.Default.Folder,
                                        contentDescription = null,
                                        modifier = Modifier.size(18.dp)
                                    )
                                },
                                testTag = "retry_public_download_${release.id}"
                            )
                        }
                    }

                    else -> Unit
                }
            } else {
                M3PrimaryButton(
                    text = stringResource(R.string.btn_download_to_downloads),
                    onClick = onDownloadToPublic,
                    leadingIcon = {
                        Icon(
                            imageVector = Icons.Default.Folder,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                    },
                    testTag = "public_download_button_${release.id}"
                )
            }
        }
    }
}
