package com.example.ui.screens

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings
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
import androidx.compose.material.icons.filled.FindInPage
import androidx.compose.material.icons.filled.HelpOutline
import androidx.compose.material.icons.filled.Launch
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SuggestionChip
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.model.DiscoveredApp
import com.example.ui.MainUiState
import com.example.ui.components.M3Badge
import com.example.ui.components.M3Card
import com.example.ui.components.M3PrimaryButton
import com.example.ui.components.M3TonalButton
import com.example.ui.theme.StatusSuccess
import com.example.ui.theme.StatusWarning
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun InstalledStatusScreen(
    state: MainUiState,
    onRefreshStatus: () -> Unit,
    onCustomPackageChange: (String) -> Unit,
    onTestCustomPackage: () -> Unit,
    onNavigateToVersions: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val installed = state.installedAppInfo.isInstalled

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp),
        contentPadding = PaddingValues(bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        // Primary Status Card
        item {
            M3Card {
                Column(modifier = Modifier.fillMaxWidth()) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Surface(
                            modifier = Modifier.size(48.dp),
                            shape = CircleShape,
                            color = if (installed) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                if (state.isScanningPackages) {
                                    CircularProgressIndicator(
                                        modifier = Modifier.size(24.dp),
                                        strokeWidth = 2.dp
                                    )
                                } else {
                                    Icon(
                                        imageVector = if (installed) Icons.Default.CheckCircle else Icons.Default.Warning,
                                        contentDescription = null,
                                        tint = if (installed) StatusSuccess else StatusWarning,
                                        modifier = Modifier.size(26.dp)
                                    )
                                }
                            }
                        }

                        Spacer(modifier = Modifier.width(14.dp))

                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = if (installed) state.installedAppInfo.appLabel else "Geen Magister Gevonden",
                                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                            )
                            Text(
                                text = if (installed) "Geïnstalleerd op dit apparaat" else "Geen actieve installatie aangetroffen",
                                style = MaterialTheme.typography.bodySmall.copy(color = MaterialTheme.colorScheme.onSurfaceVariant)
                            )
                        }

                        M3Badge(text = if (installed) "Actief" else "Niet aanwezig")
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    if (installed) {
                        M3StatusRow(label = "Pakket-ID", value = state.installedAppInfo.packageName)
                        M3StatusRow(label = "Versienaam", value = state.installedAppInfo.versionName)
                        M3StatusRow(label = "Versiecode / Build", value = "${state.installedAppInfo.versionCode}")

                        if (state.installedAppInfo.targetSdkVersion > 0) {
                            M3StatusRow(label = "Doel Android SDK", value = "API ${state.installedAppInfo.targetSdkVersion}")
                        }

                        val formattedDate = if (state.installedAppInfo.lastUpdateTime > 0) {
                            SimpleDateFormat("dd MMMM yyyy HH:mm", Locale("nl", "NL"))
                                .format(Date(state.installedAppInfo.lastUpdateTime))
                        } else {
                            "Onbekend"
                        }
                        M3StatusRow(label = "Laatst bijgewerkt", value = formattedDate)

                        Spacer(modifier = Modifier.height(14.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            M3PrimaryButton(
                                text = "Start Magister",
                                onClick = { launchApp(context, state.installedAppInfo.packageName) },
                                modifier = Modifier.weight(1f),
                                leadingIcon = {
                                    Icon(imageVector = Icons.Default.Launch, contentDescription = null, modifier = Modifier.size(16.dp))
                                },
                                testTag = "open_magister_button"
                            )

                            M3TonalButton(
                                text = "App-info",
                                onClick = { openAppDetails(context, state.installedAppInfo.packageName) },
                                modifier = Modifier.weight(1f),
                                leadingIcon = {
                                    Icon(imageVector = Icons.Default.Settings, contentDescription = null, modifier = Modifier.size(16.dp))
                                },
                                testTag = "app_details_button"
                            )
                        }
                    } else {
                        Text(
                            text = "Magister Van Gister heeft het systeem gecontroleerd op Magister installaties (nl.topicus.magister.parent, school.magister.app). Er is geen actieve installatie gevonden.",
                            style = MaterialTheme.typography.bodySmall.copy(
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                lineHeight = 18.sp
                            )
                        )

                        Spacer(modifier = Modifier.height(12.dp))

                        M3PrimaryButton(
                            text = "Kies Versie uit Repository",
                            onClick = onNavigateToVersions,
                            testTag = "browse_versions_button"
                        )
                    }
                }
            }
        }

        // Live Scanner & Verification
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
                                imageVector = Icons.Default.FindInPage,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(modifier = Modifier.width(10.dp))
                            Column {
                                Text(
                                    text = "Toestel-Scan",
                                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold)
                                )
                                Text(
                                    text = "Zoekt alle geïnstalleerde pakketten",
                                    style = MaterialTheme.typography.bodySmall.copy(color = MaterialTheme.colorScheme.onSurfaceVariant)
                                )
                            }
                        }

                        M3TonalButton(
                            text = if (state.isScanningPackages) "Scannen…" else "Nu Scannen",
                            onClick = onRefreshStatus,
                            modifier = Modifier.width(130.dp),
                            testTag = "scan_packages_button"
                        )
                    }

                    if (state.installedAppInfo.allDetectedMagisterApps.isNotEmpty()) {
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(
                            text = "Gedetecteerde Magister apps:",
                            style = MaterialTheme.typography.labelSmall.copy(
                                color = MaterialTheme.colorScheme.primary,
                                fontWeight = FontWeight.Bold
                            )
                        )
                        Spacer(modifier = Modifier.height(6.dp))

                        state.installedAppInfo.allDetectedMagisterApps.forEach { app ->
                            M3DiscoveredAppItem(app = app, onOpen = { launchApp(context, app.packageName) })
                        }
                    }
                }
            }
        }

        // Manual Package Check Tool
        item {
            M3Card {
                Column(modifier = Modifier.fillMaxWidth()) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.Search,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Pakketnaam Controleren",
                            style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold)
                        )
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    Text(
                        text = "Voer een specifieke pakketnaam in om te controleren of deze aanwezig is:",
                        style = MaterialTheme.typography.bodySmall.copy(color = MaterialTheme.colorScheme.onSurfaceVariant)
                    )

                    Spacer(modifier = Modifier.height(10.dp))

                    OutlinedTextField(
                        value = state.customPackageCheckInput,
                        onValueChange = onCustomPackageChange,
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp)
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        listOf(
                            "nl.topicus.magister.parent",
                            "nl.topicus.magister",
                            "school.magister.app"
                        ).forEach { pkg ->
                            SuggestionChip(
                                onClick = {
                                    onCustomPackageChange(pkg)
                                    onTestCustomPackage()
                                },
                                label = { Text(pkg.substringAfterLast("."), fontSize = 11.sp) }
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    M3TonalButton(
                        text = "Controleren",
                        onClick = onTestCustomPackage,
                        testTag = "verify_custom_package_button"
                    )
                }
            }
        }

        // Downgrade Troubleshooting Guide (Material 3)
        item {
            M3Card {
                Column(modifier = Modifier.fillMaxWidth()) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.HelpOutline,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(22.dp)
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Text(
                            text = "Hoe Werkt Downgraden?",
                            style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold)
                        )
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    Text(
                        text = "Android blokkeert het overschrijven van nieuwere versies door oudere versies ('STATUS_FAILURE_CONFLICT').",
                        style = MaterialTheme.typography.bodySmall.copy(
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            lineHeight = 18.sp
                        )
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    M3StepItem(number = "1", text = "Als Android de installatie weigert, verwijder eerst de huidige Magister app.")
                    M3StepItem(number = "2", text = "Installeer daarna de gewenste versie uit de repository via Magister Van Gister.")
                    M3StepItem(number = "3", text = "Al uw schoolgegevens worden direct na het inloggen opgehaald van de Magister server.")
                }
            }
        }
    }
}

@Composable
private fun M3DiscoveredAppItem(
    app: DiscoveredApp,
    onOpen: () -> Unit
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        shape = RoundedCornerShape(10.dp),
        color = MaterialTheme.colorScheme.surfaceVariant
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "${app.appName} (v${app.versionName})",
                    style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold)
                )
                Text(
                    text = app.packageName,
                    style = MaterialTheme.typography.labelSmall.copy(color = MaterialTheme.colorScheme.onSurfaceVariant)
                )
            }

            M3TonalButton(
                text = "Openen",
                onClick = onOpen,
                modifier = Modifier.width(90.dp),
                testTag = "open_app_${app.packageName}"
            )
        }
    }
}

@Composable
private fun M3StatusRow(label: String, value: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 3.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall.copy(color = MaterialTheme.colorScheme.onSurfaceVariant)
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodySmall.copy(
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface
            )
        )
    }
}

@Composable
private fun M3StepItem(number: String, text: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 3.dp),
        verticalAlignment = Alignment.Top
    ) {
        Surface(
            modifier = Modifier.size(20.dp),
            shape = CircleShape,
            color = MaterialTheme.colorScheme.primaryContainer
        ) {
            Box(contentAlignment = Alignment.Center) {
                Text(
                    text = number,
                    style = MaterialTheme.typography.labelSmall.copy(
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                )
            }
        }

        Spacer(modifier = Modifier.width(10.dp))

        Text(
            text = text,
            style = MaterialTheme.typography.bodySmall.copy(
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                lineHeight = 17.sp
            )
        )
    }
}

private fun launchApp(context: Context, packageName: String) {
    try {
        val launchIntent = context.packageManager.getLaunchIntentForPackage(packageName)
        if (launchIntent != null) {
            context.startActivity(launchIntent)
        }
    } catch (e: Exception) {
        // App could not be launched directly
    }
}

private fun openAppDetails(context: Context, packageName: String) {
    try {
        val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
            data = Uri.parse("package:$packageName")
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(intent)
    } catch (e: Exception) {
        // Fallback
    }
}
