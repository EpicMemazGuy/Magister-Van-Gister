package com.example.ui.screens

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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.installer.XapkInstaller
import com.example.ui.MainUiState
import com.example.ui.components.M3Badge
import com.example.ui.components.M3Card
import com.example.ui.components.M3PrimaryButton
import com.example.ui.components.M3TonalButton
import com.example.ui.theme.StatusSuccess
import com.example.ui.theme.StatusWarning

@Composable
fun SettingsScreen(
    state: MainUiState,
    onSaveRepository: (owner: String, repo: String) -> Unit,
    onResetRepository: () -> Unit,
    onClearCache: () -> Unit,
    onCheckPermission: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current

    var ownerInput by remember(state.repoOwner) { mutableStateOf(state.repoOwner) }
    var repoInput by remember(state.repoName) { mutableStateOf(state.repoName) }

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp),
        contentPadding = PaddingValues(bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        // Unknown Apps Permission (Material 3)
        item {
            M3Card {
                Column(modifier = Modifier.fillMaxWidth()) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Surface(
                                modifier = Modifier.size(40.dp),
                                shape = CircleShape,
                                color = if (state.canInstallPackages) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Icon(
                                        imageVector = if (state.canInstallPackages) Icons.Default.CheckCircle else Icons.Default.Warning,
                                        contentDescription = null,
                                        tint = if (state.canInstallPackages) StatusSuccess else StatusWarning,
                                        modifier = Modifier.size(22.dp)
                                    )
                                }
                            }
                            Spacer(modifier = Modifier.width(12.dp))
                            Column {
                                Text(
                                    text = "Installatietoestemming",
                                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold)
                                )
                                Text(
                                    text = if (state.canInstallPackages) "Toegestaan door Android" else "Toestemming vereist",
                                    style = MaterialTheme.typography.bodySmall.copy(color = MaterialTheme.colorScheme.onSurfaceVariant)
                                )
                            }
                        }

                        M3Badge(text = if (state.canInstallPackages) "Actief" else "Vereist")
                    }

                    if (!state.canInstallPackages) {
                        Spacer(modifier = Modifier.height(10.dp))
                        Text(
                            text = "Om pakketten via de PackageInstaller Session API te kunnen plaatsen, moet 'Onbekende apps installeren' zijn ingeschakeld.",
                            style = MaterialTheme.typography.bodySmall.copy(color = MaterialTheme.colorScheme.onSurfaceVariant)
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        M3PrimaryButton(
                            text = "Toestemming Inschakelen",
                            onClick = {
                                XapkInstaller.openInstallPermissionSettings(context)
                                onCheckPermission()
                            },
                            testTag = "open_permission_settings_button"
                        )
                    }
                }
            }
        }

        // GitHub Repository Configuration (Material 3)
        item {
            M3Card {
                Column(modifier = Modifier.fillMaxWidth()) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.Code,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(22.dp)
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Column {
                            Text(
                                text = "GitHub Opslagplaats",
                                style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold)
                            )
                            Text(
                                text = "Bron-repository voor Magister release bestanden",
                                style = MaterialTheme.typography.bodySmall.copy(color = MaterialTheme.colorScheme.onSurfaceVariant)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    OutlinedTextField(
                        value = ownerInput,
                        onValueChange = { ownerInput = it },
                        label = { Text("GitHub Eigenaar / Organisatie") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp)
                    )

                    Spacer(modifier = Modifier.height(10.dp))

                    OutlinedTextField(
                        value = repoInput,
                        onValueChange = { repoInput = it },
                        label = { Text("Repository Naam") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp)
                    )

                    Spacer(modifier = Modifier.height(14.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        M3PrimaryButton(
                            text = "Opslaan & Laden",
                            onClick = { onSaveRepository(ownerInput, repoInput) },
                            modifier = Modifier.weight(1f),
                            testTag = "save_repo_button"
                        )

                        M3TonalButton(
                            text = "Standaard",
                            onClick = {
                                onResetRepository()
                                ownerInput = state.repoOwner
                                repoInput = state.repoName
                            },
                            modifier = Modifier.weight(1f),
                            testTag = "reset_repo_button"
                        )
                    }
                }
            }
        }

        // Cache Management (Material 3)
        item {
            M3Card {
                Column(modifier = Modifier.fillMaxWidth()) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.Folder,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(22.dp)
                            )
                            Spacer(modifier = Modifier.width(10.dp))
                            Column {
                                Text(
                                    text = "Lokale Cache",
                                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold)
                                )
                                val mbSize = state.cacheSizeBytes / (1024.0 * 1024.0)
                                Text(
                                    text = String.format(java.util.Locale.US, "Gedownloade bestanden: %.1f MB", mbSize),
                                    style = MaterialTheme.typography.bodySmall.copy(color = MaterialTheme.colorScheme.onSurfaceVariant)
                                )
                            }
                        }

                        M3TonalButton(
                            text = "Wissen",
                            onClick = onClearCache,
                            leadingIcon = {
                                Icon(imageVector = Icons.Default.DeleteSweep, contentDescription = null, modifier = Modifier.size(16.dp))
                            },
                            modifier = Modifier.width(110.dp),
                            testTag = "clear_cache_button"
                        )
                    }
                }
            }
        }

        // F-Droid Readiness Card (Material 3)
        item {
            M3Card {
                Column(modifier = Modifier.fillMaxWidth()) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.Security,
                            contentDescription = null,
                            tint = StatusSuccess,
                            modifier = Modifier.size(22.dp)
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Column {
                            Text(
                                text = "F-Droid Gereedheid",
                                style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold)
                            )
                            Text(
                                text = "100% Vrij & Open Source (FOSS)",
                                style = MaterialTheme.typography.bodySmall.copy(color = StatusSuccess)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    Text(
                        text = "• Pakket ID: com.epicmemazguy.magistervangister\n" +
                                "• Licentie: GNU General Public License v3.0 (GPL-3.0)\n" +
                                "• Geen closed-source SDK's of tracking bibliotheken\n" +
                                "• Voldoet aan alle F-Droid reproduceerbare build vereisten.",
                        style = MaterialTheme.typography.bodySmall.copy(
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            lineHeight = 20.sp
                        )
                    )
                }
            }
        }
    }
}
