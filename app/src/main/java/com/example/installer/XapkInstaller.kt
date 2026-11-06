package com.example.installer

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInstaller
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.InputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream

object XapkInstaller {

    private const val TAG = "XapkInstaller"

    /**
     * Checks if the app has permission to request package installs.
     */
    fun canInstallPackages(context: Context): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            context.packageManager.canRequestPackageInstalls()
        } else {
            true
        }
    }

    /**
     * Opens system settings to grant unknown app install permission for this app.
     */
    fun openInstallPermissionSettings(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val intent = Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES).apply {
                data = Uri.parse("package:${context.packageName}")
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
        }
    }

    /**
     * Installs an APK or XAPK file using Android's native PackageInstaller Session API.
     */
    suspend fun install(
        context: Context,
        archiveFile: File,
        onStatusUpdate: (String) -> Unit
    ): Result<Unit> = withContext(Dispatchers.IO) {
        if (!archiveFile.exists() || archiveFile.length() == 0L) {
            return@withContext Result.failure(IllegalArgumentException("Bestand bestaat niet of is leeg"))
        }

        if (!canInstallPackages(context)) {
            return@withContext Result.failure(
                SecurityException("Toestemming vereist om onbekende apps te installeren")
            )
        }

        val isXapk = archiveFile.name.endsWith(".xapk", ignoreCase = true) ||
                archiveFile.name.endsWith(".zip", ignoreCase = true)

        var sessionId = -1
        var session: PackageInstaller.Session? = null

        val tempExtractDir = File(context.cacheDir, "xapk_extracted_${System.currentTimeMillis()}")
        val apkFiles = mutableListOf<File>()

        try {
            if (isXapk) {
                onStatusUpdate("📦 XAPK bundel '${archiveFile.name}' uitpakken in cache…")
                if (!tempExtractDir.exists()) tempExtractDir.mkdirs()
                
                extractZip(archiveFile, tempExtractDir)
                
                // Discover all .apk files recursively
                tempExtractDir.walkTopDown().filter { it.isFile && it.name.endsWith(".apk", ignoreCase = true) }.forEach {
                    apkFiles.add(it)
                }

                // Sort apkFiles so the main/base APK comes FIRST (index == 0)
                apkFiles.sortWith { a, b ->
                    val aIsBase = a.name.equals("base.apk", ignoreCase = true) || 
                            a.name.contains("main", ignoreCase = true) || 
                            a.name.contains("mobile", ignoreCase = true) ||
                            a.name.contains("parent", ignoreCase = true)
                    val bIsBase = b.name.equals("base.apk", ignoreCase = true) || 
                            b.name.contains("main", ignoreCase = true) || 
                            b.name.contains("mobile", ignoreCase = true) ||
                            b.name.contains("parent", ignoreCase = true)
                    when {
                        aIsBase && !bIsBase -> -1
                        !aIsBase && bIsBase -> 1
                        else -> b.length().compareTo(a.length()) // largest APK file first
                    }
                }

                // Parse package_name dynamically from manifest.json if present
                var targetPackageName: String? = null
                val manifestFile = tempExtractDir.walkTopDown().firstOrNull { it.isFile && it.name.equals("manifest.json", ignoreCase = true) }
                if (manifestFile != null) {
                    try {
                        val text = manifestFile.readText()
                        val match = Regex(""""package_name"\s*:\s*"([^"]+)"""").find(text)
                            ?: Regex(""""packageName"\s*:\s*"([^"]+)"""").find(text)
                        targetPackageName = match?.groupValues?.get(1)
                    } catch (e: Exception) {
                        Log.w(TAG, "Could not parse manifest.json package_name", e)
                    }
                }

                if (apkFiles.isEmpty()) {
                    return@withContext Result.failure(IllegalStateException("Geen APK bestanden aangetroffen in XAPK bundel"))
                }

                val splitSummary = apkFiles.joinToString(", ") { "${it.name} (${it.length() / 1024} KB)" }
                Log.d(TAG, "Gevonden APK splits in XAPK (Package: ${targetPackageName ?: "auto"}): $splitSummary")
                onStatusUpdate("📦 XAPK uitgepakt (${apkFiles.size} splits: $splitSummary)…")
            } else {
                apkFiles.add(archiveFile)
            }

            val totalSize = apkFiles.sumOf { it.length() }
            onStatusUpdate("⚡ PackageInstaller sessie openen voor ${archiveFile.name} (Totaal: ${totalSize / (1024 * 1024)} MB)…")
            val packageInstaller = context.packageManager.packageInstaller
            val params = PackageInstaller.SessionParams(PackageInstaller.SessionParams.MODE_FULL_INSTALL)
            
            params.setSize(totalSize)

            sessionId = packageInstaller.createSession(params)
            session = packageInstaller.openSession(sessionId)

            // Stream each APK file into the PackageInstaller Session
            apkFiles.forEachIndexed { index, file ->
                val entryName = if (isXapk) {
                    if (index == 0) {
                        "base.apk"
                    } else {
                        val cleanName = file.nameWithoutExtension.replace("[^a-zA-Z0-9_]".toRegex(), "_")
                        "split_config_${index}_${cleanName}.apk"
                    }
                } else {
                    "base.apk"
                }

                val sizeMb = String.format("%.1f", file.length() / (1024f * 1024f))
                onStatusUpdate("📲 Streamen naar sessie (${index + 1}/${apkFiles.size}): '$entryName' ($sizeMb MB)…")
                Log.d(TAG, "Streaming $entryName (from ${file.name}) into session (size: ${file.length()} bytes)")

                FileInputStream(file).use { input ->
                    session.openWrite(entryName, 0, file.length()).use { output ->
                        val buffer = ByteArray(65536)
                        var bytesRead: Int
                        while (input.read(buffer).also { bytesRead = it } != -1) {
                            output.write(buffer, 0, bytesRead)
                        }
                        session.fsync(output)
                    }
                }
            }

            onStatusUpdate("🚀 Installatiesessie bevestigen voor Android systeem (Sessie ID #$sessionId)…")
            val receiverIntent = Intent(context, InstallStatusReceiver::class.java).apply {
                action = InstallStatusReceiver.ACTION_INSTALL_STATUS
            }

            val flags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE
            } else {
                PendingIntent.FLAG_UPDATE_CURRENT
            }

            val pendingIntent = PendingIntent.getBroadcast(
                context,
                sessionId,
                receiverIntent,
                flags
            )

            session.commit(pendingIntent.intentSender)
            session.close()
            session = null

            Log.i(TAG, "PackageInstaller session $sessionId committed successfully for ${apkFiles.size} splits")
            onStatusUpdate("✅ Installatieverzoek voor Magister (${apkFiles.size} pakketten) verzonden!")

            Result.success(Unit)
        } catch (e: Exception) {
            Log.e(TAG, "Error installing package via session: ${e.message}", e)
            try {
                session?.abandon()
            } catch (abandonEx: Exception) {
                Log.e(TAG, "Failed to abandon session $sessionId", abandonEx)
            }

            // Fallback: If PackageInstaller session fails, launch direct system PackageInstaller for extracted base.apk via FileProvider
            val targetApk = apkFiles.firstOrNull { it.name.endsWith(".apk", ignoreCase = true) } ?: archiveFile
            if (targetApk.exists() && targetApk.length() > 0) {
                try {
                    onStatusUpdate("⚠️ Sessie mislukt, starten van rechtstreekse pakketinstallatie via FileProvider voor ${targetApk.name}…")
                    val apkUri: Uri = androidx.core.content.FileProvider.getUriForFile(
                        context,
                        "${context.packageName}.fileprovider",
                        targetApk
                    )
                    val installIntent = Intent(Intent.ACTION_VIEW).apply {
                        setDataAndType(apkUri, "application/vnd.android.package-archive")
                        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    }
                    context.startActivity(installIntent)
                    return@withContext Result.success(Unit)
                } catch (fallbackEx: Exception) {
                    Log.e(TAG, "FileProvider fallback installation failed", fallbackEx)
                }
            }

            Result.failure(e)
        } finally {
            try {
                if (tempExtractDir.exists()) {
                    tempExtractDir.deleteRecursively()
                }
            } catch (delEx: Exception) {
                Log.w(TAG, "Failed to clean up temp extract directory", delEx)
            }
        }
    }

    private fun extractZip(zipFile: File, targetDir: File) {
        java.util.zip.ZipFile(zipFile).use { zip ->
            val entries = zip.entries()
            val buffer = ByteArray(65536)
            while (entries.hasMoreElements()) {
                val entry = entries.nextElement()
                val newFile = File(targetDir, entry.name)
                // Guard against Zip Slip vulnerability
                if (!newFile.canonicalPath.startsWith(targetDir.canonicalPath)) {
                    throw SecurityException("Zip Slip poging gedetecteerd: ${entry.name}")
                }

                if (entry.isDirectory) {
                    newFile.mkdirs()
                } else {
                    newFile.parentFile?.mkdirs()
                    zip.getInputStream(entry).use { input ->
                        FileOutputStream(newFile).use { output ->
                            var len: Int
                            while (input.read(buffer).also { len = it } > 0) {
                                output.write(buffer, 0, len)
                            }
                        }
                    }
                }
            }
        }
    }
}
