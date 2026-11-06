package com.example.ui

import android.app.Application
import android.content.pm.PackageManager
import android.os.Build
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.ReleaseRepository
import com.example.installer.InstallStatusReceiver
import com.example.installer.XapkInstaller
import com.example.model.DiscoveredApp
import com.example.model.DownloadState
import com.example.model.InstalledAppInfo
import com.example.model.MagisterRelease
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

enum class AppTab {
    VERSIONS,
    MANUAL,
    STATUS,
    CONSOLE,
    SETTINGS
}

data class ConsoleLogEntry(
    val timestamp: String,
    val level: String, // INFO, CMD, OK, ERR
    val message: String
)

data class DowngradePromptData(
    val targetRelease: MagisterRelease,
    val installedPackageName: String,
    val installedVersionName: String,
    val isFromConflictError: Boolean = false
)

data class MainUiState(
    val releases: List<MagisterRelease> = emptyList(),
    val mainVersions: List<MagisterRelease> = emptyList(), // ONLY 2.7.5 and 2.7.0
    val xapkVersions: List<MagisterRelease> = emptyList(), // All .xapk releases for Manual tab
    val filteredReleases: List<MagisterRelease> = emptyList(),
    val searchQuery: String = "",
    val selectedEra: String = "ALL", // ALL, REC, XAPK, APK
    val isLoading: Boolean = false,
    val installedAppInfo: InstalledAppInfo = InstalledAppInfo(isInstalled = false),
    val isScanningPackages: Boolean = false,
    val downloadState: DownloadState = DownloadState.Idle,
    val activeTab: AppTab = AppTab.VERSIONS,
    val repoOwner: String = ReleaseRepository.DEFAULT_OWNER,
    val repoName: String = ReleaseRepository.DEFAULT_REPO,
    val cacheSizeBytes: Long = 0L,
    val userNotice: String? = null,
    val canInstallPackages: Boolean = false,
    val customPackageCheckInput: String = "nl.topicus.magister.parent",
    val downgradePrompt: DowngradePromptData? = null,
    val consoleLogs: List<ConsoleLogEntry> = emptyList()
)

class MainViewModel(application: Application) : AndroidViewModel(application) {

    companion object {
        private const val TAG = "MainViewModel"
        val KNOWN_MAGISTER_PACKAGES = listOf(
            "nl.topicus.magister.parent",
            "nl.topicus.magister",
            "nl.topicus.magister.leerling",
            "nl.topicus.magister.docent",
            "nl.topicus.magister6",
            "nl.schoolmaster.magister.mobile",
            "school.magister.app",
            "nl.magister.app",
            "nl.magister"
        )
    }

    private val repository = ReleaseRepository(application)

    private val _uiState = MutableStateFlow(MainUiState())
    val uiState: StateFlow<MainUiState> = _uiState.asStateFlow()

    private val _launchUninstallEvent = MutableSharedFlow<String>(extraBufferCapacity = 1)
    val launchUninstallEvent = _launchUninstallEvent.asSharedFlow()

    private var pendingReinstallRelease: MagisterRelease? = null
    private var pendingUninstallPackage: String? = null
    private var lastAttemptedRelease: MagisterRelease? = null

    init {
        checkInstallPermission()
        checkInstalledMagister()
        loadReleases()
        updateCacheSize()
        observeInstallEvents()
    }

    fun checkInstallPermission() {
        val allowed = XapkInstaller.canInstallPackages(getApplication())
        _uiState.update { it.copy(canInstallPackages = allowed) }
    }

    /**
     * Thoroughly checks if Magister is installed on the device.
     * Uses targeted getPackageInfo queries and comprehensive device-wide package scan.
     */
    fun checkInstalledMagister(customPackageName: String? = null) {
        viewModelScope.launch {
            _uiState.update { it.copy(isScanningPackages = true) }

            val detected = withContext(Dispatchers.IO) {
                val pm = getApplication<Application>().packageManager
                val discoveredList = mutableListOf<DiscoveredApp>()
                var primaryDetected: InstalledAppInfo? = null

                // 1. Direct candidate queries
                val candidates = mutableListOf<String>()
                if (!customPackageName.isNullOrBlank()) {
                    candidates.add(customPackageName.trim())
                }
                candidates.addAll(KNOWN_MAGISTER_PACKAGES)

                val myPackage = getApplication<Application>().packageName.lowercase()

                for (pkg in candidates) {
                    if (pkg.lowercase() == myPackage || pkg.lowercase() == "com.epicmemazguy.magistervangister") {
                        continue
                    }
                    try {
                        val packageInfo = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                            pm.getPackageInfo(pkg, PackageManager.PackageInfoFlags.of(0))
                        } else {
                            @Suppress("DEPRECATION")
                            pm.getPackageInfo(pkg, 0)
                        }

                        val appLabel = try {
                            pm.getApplicationLabel(packageInfo.applicationInfo!!).toString()
                        } catch (e: Exception) {
                            "Magister"
                        }

                        val longCode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                            packageInfo.longVersionCode
                        } else {
                            @Suppress("DEPRECATION")
                            packageInfo.versionCode.toLong()
                        }

                        val app = DiscoveredApp(
                            packageName = pkg,
                            appName = appLabel,
                            versionName = packageInfo.versionName ?: "Onbekend",
                            versionCode = longCode
                        )

                        if (!discoveredList.any { it.packageName == pkg }) {
                            discoveredList.add(app)
                        }

                        if (primaryDetected == null) {
                            primaryDetected = InstalledAppInfo(
                                isInstalled = true,
                                packageName = pkg,
                                versionName = packageInfo.versionName ?: "Onbekend",
                                versionCode = longCode,
                                appLabel = appLabel,
                                lastUpdateTime = packageInfo.lastUpdateTime,
                                targetSdkVersion = packageInfo.applicationInfo?.targetSdkVersion ?: 0,
                                sourceDir = packageInfo.applicationInfo?.sourceDir ?: ""
                            )
                        }
                    } catch (e: PackageManager.NameNotFoundException) {
                        // Package not installed under this exact name
                    }
                }

                // 2. Comprehensive device package scan for any target Magister package
                try {
                    val myPackageName = getApplication<Application>().packageName.lowercase()
                    val allPackages = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                        pm.getInstalledPackages(PackageManager.PackageInfoFlags.of(0))
                    } else {
                        @Suppress("DEPRECATION")
                        pm.getInstalledPackages(0)
                    }

                    for (pkgInfo in allPackages) {
                        val pkgName = pkgInfo.packageName.lowercase()
                        
                        // CRITICAL: Exclude Magister Van Gister itself to prevent suicide uninstall prompt!
                        if (pkgName == myPackageName || pkgName == "com.epicmemazguy.magistervangister") {
                            continue
                        }

                        val label = try {
                            pm.getApplicationLabel(pkgInfo.applicationInfo!!).toString()
                        } catch (e: Exception) {
                            ""
                        }

                        if (pkgName.contains("magister") || label.lowercase().contains("magister")) {
                            val longCode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                                pkgInfo.longVersionCode
                            } else {
                                @Suppress("DEPRECATION")
                                pkgInfo.versionCode.toLong()
                            }

                            val discovered = DiscoveredApp(
                                packageName = pkgInfo.packageName,
                                appName = label.ifBlank { "Magister" },
                                versionName = pkgInfo.versionName ?: "Onbekend",
                                versionCode = longCode
                            )

                            if (!discoveredList.any { it.packageName == pkgInfo.packageName }) {
                                discoveredList.add(discovered)
                            }

                            if (primaryDetected == null) {
                                primaryDetected = InstalledAppInfo(
                                    isInstalled = true,
                                    packageName = pkgInfo.packageName,
                                    versionName = pkgInfo.versionName ?: "Onbekend",
                                    versionCode = longCode,
                                    appLabel = label.ifBlank { "Magister" },
                                    lastUpdateTime = pkgInfo.lastUpdateTime,
                                    targetSdkVersion = pkgInfo.applicationInfo?.targetSdkVersion ?: 0,
                                    sourceDir = pkgInfo.applicationInfo?.sourceDir ?: ""
                                )
                            }
                        }
                    }
                } catch (e: Exception) {
                    Log.w(TAG, "Device-wide package scan encountered an error", e)
                }

                val finalResult = primaryDetected?.copy(
                    allDetectedMagisterApps = discoveredList,
                    detectedPackageCandidates = discoveredList.map { it.packageName }
                ) ?: InstalledAppInfo(
                    isInstalled = false,
                    allDetectedMagisterApps = discoveredList,
                    detectedPackageCandidates = discoveredList.map { it.packageName }
                )

                finalResult
            }

            _uiState.update {
                it.copy(
                    installedAppInfo = detected,
                    isScanningPackages = false,
                    userNotice = if (detected.isInstalled) {
                        "Magister gedetecteerd: ${detected.appLabel} v${detected.versionName}"
                    } else null
                )
            }
        }
    }

    fun onCustomPackageInputChanged(pkg: String) {
        _uiState.update { it.copy(customPackageCheckInput = pkg) }
    }

    fun testCustomPackageCheck() {
        checkInstalledMagister(_uiState.value.customPackageCheckInput)
    }

    fun loadReleases() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, userNotice = null) }
            val currentOwner = _uiState.value.repoOwner
            val currentRepo = _uiState.value.repoName

            val releases = repository.getReleases(currentOwner, currentRepo)

            // Main screen receives ONLY 2.7.5 and 2.7.0
            val mainVersions = releases.filter { it.versionName == "2.7.5" || it.versionName == "2.7.0" }
                .sortedByDescending { it.versionCode }

            // Manual tab receives all XAPK releases
            val xapkVersions = releases.filter { it.isXapk }
                .sortedByDescending { it.versionCode }

            _uiState.update { state ->
                state.copy(
                    releases = releases,
                    mainVersions = mainVersions,
                    xapkVersions = xapkVersions,
                    filteredReleases = filterReleases(releases, state.searchQuery, state.selectedEra),
                    isLoading = false
                )
            }
        }
    }

    fun downloadToPublicDownloads(release: MagisterRelease) {
        viewModelScope.launch {
            try {
                _uiState.update {
                    it.copy(
                        downloadState = DownloadState.Downloading(
                            releaseId = release.id,
                            progress = 0f,
                            downloadedBytes = 0,
                            totalBytes = release.sizeBytes
                        )
                    )
                }
                logToConsole("PUBLIC DOWNLOAD STARTED: ${release.assetName} to Downloads folder", "CMD")

                val downloadedFile = repository.downloadToPublicDownloads(release) { downloaded, total ->
                    val progressRatio = if (total > 0) (downloaded.toFloat() / total).coerceIn(0f, 1f) else 0f
                    _uiState.update { state ->
                        state.copy(
                            downloadState = DownloadState.Downloading(
                                releaseId = release.id,
                                progress = progressRatio,
                                downloadedBytes = downloaded,
                                totalBytes = total
                            )
                        )
                    }
                }

                logToConsole("PUBLIC DOWNLOAD COMPLETED: ${downloadedFile.absolutePath}", "OK")
                _uiState.update {
                    it.copy(
                        downloadState = DownloadState.Completed(
                            release.id,
                            "Opslagen in Downloads-map voltooid!"
                        ),
                        userNotice = getApplication<Application>().getString(com.example.R.string.toast_download_complete, downloadedFile.name)
                    )
                }
            } catch (e: Exception) {
                Log.e(TAG, "Public download error: ${e.message}", e)
                logToConsole("PUBLIC DOWNLOAD ERROR: ${e.message}", "ERR")
                _uiState.update {
                    it.copy(
                        downloadState = DownloadState.Error(
                            release.id,
                            e.message ?: "Fout bij opslaan in Downloads"
                        ),
                        userNotice = "Fout bij downloaden: ${e.message}"
                    )
                }
            }
        }
    }

    fun onSearchQueryChanged(query: String) {
        _uiState.update { state ->
            state.copy(
                searchQuery = query,
                filteredReleases = filterReleases(state.releases, query, state.selectedEra)
            )
        }
    }

    fun onEraFilterSelected(era: String) {
        _uiState.update { state ->
            state.copy(
                selectedEra = era,
                filteredReleases = filterReleases(state.releases, state.searchQuery, era)
            )
        }
    }

    private fun filterReleases(
        releases: List<MagisterRelease>,
        query: String,
        era: String
    ): List<MagisterRelease> {
        return releases.filter { release ->
            val matchesQuery = if (query.isBlank()) {
                true
            } else {
                val clean = query.trim().lowercase()
                release.versionName.lowercase().contains(clean) ||
                        release.tagName.lowercase().contains(clean) ||
                        release.description.lowercase().contains(clean) ||
                        release.assetName.lowercase().contains(clean) ||
                        release.era.lowercase().contains(clean)
            }

            val matchesEra = when (era) {
                "ALL" -> true
                "REC" -> release.isRecommendedLegacy
                "XAPK" -> release.isXapk
                "APK" -> !release.isXapk
                else -> release.era.equals(era, ignoreCase = true) || release.majorEra.equals(era, ignoreCase = true)
            }

            matchesQuery && matchesEra
        }
    }

    fun requestDownloadAndInstall(release: MagisterRelease) {
        lastAttemptedRelease = release

        // Check if Magister is currently installed on the device
        if (_uiState.value.installedAppInfo.isInstalled) {
            val installedPkg = _uiState.value.installedAppInfo.packageName.ifBlank { "nl.topicus.magister.parent" }
            val installedVer = _uiState.value.installedAppInfo.versionName.ifBlank { "Onbekend" }

            // Android blocks in-place downgrades or signature conflicts.
            // Prompt the user to automatically uninstall the old version and proceed with install!
            _uiState.update {
                it.copy(
                    downgradePrompt = DowngradePromptData(
                        targetRelease = release,
                        installedPackageName = installedPkg,
                        installedVersionName = installedVer,
                        isFromConflictError = false
                    )
                )
            }
            return
        }

        downloadAndInstall(release)
    }

    fun confirmUninstallAndReinstall() {
        val prompt = _uiState.value.downgradePrompt ?: return
        val pkg = prompt.installedPackageName
        val release = prompt.targetRelease

        pendingReinstallRelease = release
        pendingUninstallPackage = pkg

        _uiState.update {
            it.copy(
                downgradePrompt = null,
                userNotice = "Deïnstallatie van Magister gestart…"
            )
        }

        _launchUninstallEvent.tryEmit(pkg)
    }

    fun dismissDowngradePrompt() {
        _uiState.update { it.copy(downgradePrompt = null) }
    }

    fun onUninstallReturned() {
        viewModelScope.launch {
            val targetRelease = pendingReinstallRelease ?: return@launch
            val targetPkg = pendingUninstallPackage ?: "nl.topicus.magister.parent"

            // Wait briefly for Android PackageManager database sync
            kotlinx.coroutines.delay(600)
            checkInstalledMagister()

            val isStillInstalled = withContext(Dispatchers.IO) {
                try {
                    val pm = getApplication<Application>().packageManager
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                        pm.getPackageInfo(targetPkg, PackageManager.PackageInfoFlags.of(0))
                    } else {
                        @Suppress("DEPRECATION")
                        pm.getPackageInfo(targetPkg, 0)
                    }
                    true
                } catch (e: Exception) {
                    false
                }
            }

            if (!isStillInstalled) {
                _uiState.update {
                    it.copy(
                        userNotice = "Magister verwijderd. Versie ${targetRelease.versionName} wordt nu direct geïnstalleerd…"
                    )
                }
                pendingReinstallRelease = null
                pendingUninstallPackage = null
                downloadAndInstall(targetRelease)
            } else {
                _uiState.update {
                    it.copy(
                        userNotice = "Deïnstallatie geannuleerd. Huidige Magister app is behouden.",
                        downloadState = DownloadState.Idle
                    )
                }
                pendingReinstallRelease = null
                pendingUninstallPackage = null
            }
        }
    }

    fun logToConsole(message: String, level: String = "INFO") {
        val time = java.text.SimpleDateFormat("HH:mm:ss.SSS", java.util.Locale.getDefault()).format(java.util.Date())
        val entry = ConsoleLogEntry(time, level, message)
        _uiState.update { state ->
            state.copy(consoleLogs = state.consoleLogs + entry)
        }
    }

    fun clearConsoleLogs() {
        _uiState.update { it.copy(consoleLogs = emptyList()) }
    }

    fun downloadAndInstall(release: MagisterRelease) {
        lastAttemptedRelease = release
        viewModelScope.launch {
            try {
                checkInstallPermission()
                if (!_uiState.value.canInstallPackages) {
                    _uiState.update {
                        it.copy(
                            userNotice = "Toestemming vereist om onbekende apps te installeren.",
                            activeTab = AppTab.SETTINGS
                        )
                    }
                    return@launch
                }

                // Switch to CONSOLE view so nerds can see real-time commands & extraction progress!
                _uiState.update {
                    it.copy(
                        activeTab = AppTab.CONSOLE,
                        downloadState = DownloadState.Downloading(
                            releaseId = release.id,
                            progress = 0f,
                            downloadedBytes = 0,
                            totalBytes = release.sizeBytes
                        )
                    )
                }

                logToConsole("=== INSTALLATION INITIATED: ${release.assetName} (v${release.versionName}) ===", "CMD")
                logToConsole("URL: ${release.downloadUrl}", "INFO")
                logToConsole("Target Package: nl.topicus.magister.parent | Size: ${release.formattedSize}", "INFO")

                val downloadedFile = repository.downloadAssetFile(release) { downloaded, total ->
                    val progressRatio = if (total > 0) (downloaded.toFloat() / total).coerceIn(0f, 1f) else 0f
                    _uiState.update { state ->
                        state.copy(
                            downloadState = DownloadState.Downloading(
                                releaseId = release.id,
                                progress = progressRatio,
                                downloadedBytes = downloaded,
                                totalBytes = total
                            )
                        )
                    }
                }

                logToConsole("DOWNLOAD COMPLETED: ${downloadedFile.absolutePath} (${downloadedFile.length()} bytes)", "OK")

                _uiState.update {
                    it.copy(
                        downloadState = if (release.isXapk) {
                            DownloadState.Extracting(release.id, "XAPK splits uitpakken in cache…")
                        } else {
                            DownloadState.Installing(release.id, "APK voorbereiden voor sessie…")
                        }
                    )
                }

                val installResult = XapkInstaller.install(
                    context = getApplication(),
                    archiveFile = downloadedFile
                ) { stepMessage ->
                    logToConsole(stepMessage, if (stepMessage.contains("🚀") || stepMessage.contains("⚡")) "CMD" else "INFO")
                    _uiState.update {
                        it.copy(downloadState = DownloadState.Installing(release.id, stepMessage))
                    }
                }

                installResult.fold(
                    onSuccess = {
                        logToConsole("SESSION COMMIT SUCCESSFUL: PackageInstaller.commit() executed", "OK")
                        _uiState.update {
                            it.copy(
                                downloadState = DownloadState.Completed(
                                    release.id,
                                    "Installatieverzoek verzonden! Bevestig op het scherm."
                                ),
                                userNotice = "Installatiesessie gestart!"
                            )
                        }
                        updateCacheSize()
                    },
                    onFailure = { error ->
                        Log.e(TAG, "Install error: ${error.message}", error)
                        logToConsole("INSTALLATION SESSION ERROR: ${error.message}", "ERR")
                        _uiState.update {
                            it.copy(
                                downloadState = DownloadState.Error(
                                    release.id,
                                    error.message ?: "Onbekende fout tijdens installatie"
                                ),
                                userNotice = "Fout bij installatie: ${error.message}"
                            )
                        }
                    }
                )
            } catch (e: Exception) {
                Log.e(TAG, "Download and install flow failed", e)
                logToConsole("FATAL EXCEPTION: ${e.message}", "ERR")
                _uiState.update {
                    it.copy(
                        downloadState = DownloadState.Error(
                            release.id,
                            e.message ?: "Er is een onverwachte fout opgetreden"
                        ),
                        userNotice = "Fout: ${e.message}"
                    )
                }
            }
        }
    }

    private fun observeInstallEvents() {
        viewModelScope.launch {
            InstallStatusReceiver.installEvents.collect { event ->
                when (event) {
                    is InstallStatusReceiver.InstallResult.Success -> {
                        checkInstalledMagister()
                        _uiState.update {
                            it.copy(
                                downloadState = DownloadState.Idle,
                                userNotice = "Magister is succesvol geïnstalleerd!"
                            )
                        }
                    }
                    is InstallStatusReceiver.InstallResult.DowngradeConflict -> {
                        val pkg = event.packageName?.ifEmpty { null }
                            ?: _uiState.value.installedAppInfo.packageName.ifEmpty { null }
                            ?: "nl.topicus.magister.parent"
                        val release = lastAttemptedRelease
                        if (release != null) {
                            _uiState.update {
                                it.copy(
                                    downloadState = DownloadState.Idle,
                                    downgradePrompt = DowngradePromptData(
                                        targetRelease = release,
                                        installedPackageName = pkg,
                                        installedVersionName = _uiState.value.installedAppInfo.versionName.ifEmpty { "Huidige versie" },
                                        isFromConflictError = true
                                    )
                                )
                            }
                        } else {
                            _uiState.update {
                                it.copy(
                                    downloadState = DownloadState.Error(null, event.message),
                                    userNotice = event.message
                                )
                            }
                        }
                    }
                    is InstallStatusReceiver.InstallResult.Error -> {
                        _uiState.update {
                            it.copy(
                                downloadState = DownloadState.Error(null, event.message),
                                userNotice = event.message
                            )
                        }
                    }
                    is InstallStatusReceiver.InstallResult.UserActionRequired -> {
                        _uiState.update {
                            it.copy(userNotice = "Bevestig de installatie op uw scherm.")
                        }
                    }
                }
            }
        }
    }

    fun updateRepositoryConfig(owner: String, repo: String) {
        val cleanOwner = owner.trim().ifEmpty { ReleaseRepository.DEFAULT_OWNER }
        val cleanRepo = repo.trim().ifEmpty { ReleaseRepository.DEFAULT_REPO }
        _uiState.update {
            it.copy(repoOwner = cleanOwner, repoName = cleanRepo)
        }
        loadReleases()
    }

    fun resetToDefaultRepo() {
        _uiState.update {
            it.copy(
                repoOwner = ReleaseRepository.DEFAULT_OWNER,
                repoName = ReleaseRepository.DEFAULT_REPO
            )
        }
        loadReleases()
    }

    fun clearCache() {
        viewModelScope.launch {
            val cleared = repository.clearCache()
            updateCacheSize()
            _uiState.update {
                it.copy(userNotice = if (cleared) "Cache gewist" else "Sommige bestanden konden niet worden gewist")
            }
        }
    }

    fun updateCacheSize() {
        val size = repository.getCacheSize()
        _uiState.update { it.copy(cacheSizeBytes = size) }
    }

    fun setTab(tab: AppTab) {
        _uiState.update { it.copy(activeTab = tab) }
        if (tab == AppTab.STATUS) {
            checkInstalledMagister()
        }
    }

    fun dismissUserNotice() {
        _uiState.update { it.copy(userNotice = null) }
    }

    fun resetDownloadState() {
        _uiState.update { it.copy(downloadState = DownloadState.Idle) }
    }
}
