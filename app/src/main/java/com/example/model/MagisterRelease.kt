package com.example.model

data class MagisterRelease(
    val id: Long,
    val tagName: String,
    val versionName: String,
    val versionCode: Int,
    val releaseDate: String,
    val description: String,
    val assetName: String,
    val downloadUrl: String,
    val sizeBytes: Long,
    val isXapk: Boolean,
    val isRecommendedLegacy: Boolean = false,
    val isVerified: Boolean = true,
    val era: String = "v2.x",
    val sha256: String = ""
) {
    val formattedSize: String
        get() {
            if (sizeBytes <= 0) return "Onbekende grootte"
            val mb = sizeBytes / (1024.0 * 1024.0)
            return String.format(java.util.Locale.US, "%.1f MB", mb)
        }

    val majorEra: String
        get() {
            val major = versionName.substringBefore(".", "2")
            return "v$major.x"
        }
}

sealed interface DownloadState {
    object Idle : DownloadState
    
    data class Downloading(
        val releaseId: Long,
        val progress: Float, // 0.0 to 1.0
        val downloadedBytes: Long,
        val totalBytes: Long
    ) : DownloadState {
        val percentage: Int
            get() = (progress * 100).toInt().coerceIn(0, 100)

        val formattedProgress: String
            get() {
                val downloadedMb = downloadedBytes / (1024.0 * 1024.0)
                val totalMb = if (totalBytes > 0) totalBytes / (1024.0 * 1024.0) else 0.0
                return if (totalBytes > 0) {
                    String.format(java.util.Locale.US, "%.1f MB / %.1f MB (%d%%)", downloadedMb, totalMb, percentage)
                } else {
                    String.format(java.util.Locale.US, "%.1f MB gedownload", downloadedMb)
                }
            }
    }

    data class Extracting(val releaseId: Long, val message: String = "XAPK splits uitpakken in cache…") : DownloadState
    
    data class Installing(val releaseId: Long, val message: String = "PackageInstaller sessie starten…") : DownloadState
    
    data class Completed(val releaseId: Long, val message: String = "Installatieverzoek verzonden!") : DownloadState
    
    data class Error(val releaseId: Long?, val errorMessage: String) : DownloadState
}

data class InstalledAppInfo(
    val isInstalled: Boolean,
    val packageName: String = "nl.topicus.magister.parent",
    val versionName: String = "Niet geïnstalleerd",
    val versionCode: Long = 0,
    val appLabel: String = "Magister",
    val lastUpdateTime: Long = 0,
    val targetSdkVersion: Int = 0,
    val sourceDir: String = "",
    val detectedPackageCandidates: List<String> = emptyList(),
    val allDetectedMagisterApps: List<DiscoveredApp> = emptyList()
)

data class DiscoveredApp(
    val packageName: String,
    val appName: String,
    val versionName: String,
    val versionCode: Long
)
