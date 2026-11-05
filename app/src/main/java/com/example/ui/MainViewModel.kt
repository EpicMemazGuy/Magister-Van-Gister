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
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

enum class AppTab {
    VERSIONS,
    STATUS,
    SETTINGS
}

data class MainUiState(
    val releases: List<MagisterRelease> = emptyList(),
    val filteredReleases: List<MagisterRelease> = emptyList(),
    val searchQuery: String = "",
    val selectedEra: String = "ALL", // ALL, v6.x, v5.x, v4.x, v3.x, v2.x, v1.x, REC
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
    val customPackageCheckInput: String = "nl.topicus.magister.parent"
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
            "school.magister.app",
            "nl.magister.app",
            "nl.magister"
        )
    }

    private val repository = ReleaseRepository(application)

    private val _uiState = MutableStateFlow(MainUiState())
    val uiState: StateFlow<MainUiState> = _uiState.asStateFlow()

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

                for (pkg in candidates) {
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

                // 2. Comprehensive device package scan for any package containing 'magister'
                try {
                    val allPackages = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                        pm.getInstalledPackages(PackageManager.PackageInfoFlags.of(0))
                    } else {
                        @Suppress("DEPRECATION")
                        pm.getInstalledPackages(0)
                    }

                    for (pkgInfo in allPackages) {
                        val pkgName = pkgInfo.packageName.lowercase()
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

            _uiState.update { state ->
                state.copy(
                    releases = releases,
                    filteredReleases = filterReleases(releases, state.searchQuery, state.selectedEra),
                    isLoading = false
                )
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

    fun downloadAndInstall(release: MagisterRelease) {
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
                    _uiState.update {
                        it.copy(downloadState = DownloadState.Installing(release.id, stepMessage))
                    }
                }

                installResult.fold(
                    onSuccess = {
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
