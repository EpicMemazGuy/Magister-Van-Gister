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

        try {
            val apkFiles = mutableListOf<File>()

            if (isXapk) {
                onStatusUpdate("XAPK archief uitpakken in cache…")
                if (!tempExtractDir.exists()) tempExtractDir.mkdirs()
                
                extractZip(archiveFile, tempExtractDir)
                
                // Discover all .apk files recursively
                tempExtractDir.walkTopDown().filter { it.isFile && it.name.endsWith(".apk", ignoreCase = true) }.forEach {
                    apkFiles.add(it)
                }

                if (apkFiles.isEmpty()) {
                    return@withContext Result.failure(IllegalStateException("Geen APK bestanden aangetroffen in XAPK bundel"))
                }

                Log.d(TAG, "Gevonden APK splits in XAPK: ${apkFiles.map { it.name }}")
            } else {
                apkFiles.add(archiveFile)
            }

            onStatusUpdate("PackageInstaller sessie initialiseren…")
            val packageInstaller = context.packageManager.packageInstaller
            val params = PackageInstaller.SessionParams(PackageInstaller.SessionParams.MODE_FULL_INSTALL)
            
            // Set required properties for full install
            params.setSize(apkFiles.sumOf { it.length() })
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                params.setRequireUserAction(PackageInstaller.SessionParams.USER_ACTION_NOT_REQUIRED)
            }

            sessionId = packageInstaller.createSession(params)
            session = packageInstaller.openSession(sessionId)

            // Stream each APK file into the PackageInstaller Session
            apkFiles.forEachIndexed { index, file ->
                val entryName = if (isXapk) {
                    file.name
                } else {
                    "base_${index}.apk"
                }

                onStatusUpdate("Pakketbestand streamen: ${file.name} (${index + 1}/${apkFiles.size})…")
                Log.d(TAG, "Streaming $entryName into install session (size: ${file.length()} bytes)")

                FileInputStream(file).use { input ->
                    session.openWrite(entryName, 0, file.length()).use { output ->
                        input.copyTo(output)
                        session.fsync(output)
                    }
                }
            }

            onStatusUpdate("Sessie bevestigen en Android installatiedialoog starten…")
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

            Log.i(TAG, "PackageInstaller session $sessionId committed successfully")
            onStatusUpdate("Installatieverzoek verzonden naar het Android-systeem!")

            Result.success(Unit)
        } catch (e: Exception) {
            Log.e(TAG, "Error installing package via session", e)
            try {
                session?.abandon()
            } catch (abandonEx: Exception) {
                Log.e(TAG, "Failed to abandon session $sessionId", abandonEx)
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
        ZipInputStream(FileInputStream(zipFile)).use { zis ->
            var entry: ZipEntry? = zis.nextEntry
            val buffer = ByteArray(8192)
            while (entry != null) {
                val newFile = File(targetDir, entry.name)
                // Guard against Zip Slip vulnerability
                if (!newFile.canonicalPath.startsWith(targetDir.canonicalPath)) {
                    throw SecurityException("Zip Slip poging gedetecteerd: ${entry.name}")
                }

                if (entry.isDirectory) {
                    newFile.mkdirs()
                } else {
                    newFile.parentFile?.mkdirs()
                    FileOutputStream(newFile).use { fos ->
                        var len: Int
                        while (zis.read(buffer).also { len = it } > 0) {
                            fos.write(buffer, 0, len)
                        }
                    }
                }
                zis.closeEntry()
                entry = zis.nextEntry
            }
        }
    }
}
