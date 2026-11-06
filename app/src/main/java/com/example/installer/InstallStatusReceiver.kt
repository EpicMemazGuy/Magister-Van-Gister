package com.example.installer

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInstaller
import android.os.Build
import android.util.Log
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow

class InstallStatusReceiver : BroadcastReceiver() {

    companion object {
        const val ACTION_INSTALL_STATUS = "com.epicmemazguy.magistervangister.INSTALL_STATUS"
        private const val TAG = "InstallStatusReceiver"

        private val _installEvents = MutableSharedFlow<InstallResult>(extraBufferCapacity = 5)
        val installEvents = _installEvents.asSharedFlow()
    }

    sealed interface InstallResult {
        object Success : InstallResult
        data class Error(val status: Int, val message: String) : InstallResult
        data class DowngradeConflict(val packageName: String?, val message: String) : InstallResult
        object UserActionRequired : InstallResult
    }

    override fun onReceive(context: Context, intent: Intent) {
        val status = intent.getIntExtra(PackageInstaller.EXTRA_STATUS, PackageInstaller.STATUS_FAILURE)
        val message = intent.getStringExtra(PackageInstaller.EXTRA_STATUS_MESSAGE) ?: "Status code: $status"

        Log.d(TAG, "Package installation status received: status=$status, message=$message")

        when (status) {
            PackageInstaller.STATUS_PENDING_USER_ACTION -> {
                Log.d(TAG, "Install requires user confirmation prompt")
                val confirmIntent = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    intent.getParcelableExtra(Intent.EXTRA_INTENT, Intent::class.java)
                } else {
                    @Suppress("DEPRECATION")
                    intent.getParcelableExtra(Intent.EXTRA_INTENT)
                }

                if (confirmIntent != null) {
                    confirmIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    context.startActivity(confirmIntent)
                }
                _installEvents.tryEmit(InstallResult.UserActionRequired)
            }

            PackageInstaller.STATUS_SUCCESS -> {
                Log.i(TAG, "Magister installation session succeeded")
                _installEvents.tryEmit(InstallResult.Success)
            }

            PackageInstaller.STATUS_FAILURE_CONFLICT -> {
                val conflictPackage = intent.getStringExtra(PackageInstaller.EXTRA_PACKAGE_NAME)
                val errorMsg = "Downgrade conflict (STATUS_FAILURE_CONFLICT): Android weigert installatie over een nieuwere of conflicterende versie."
                Log.e(TAG, errorMsg)
                _installEvents.tryEmit(InstallResult.DowngradeConflict(conflictPackage, errorMsg))
            }

            PackageInstaller.STATUS_FAILURE_STORAGE -> {
                val errorMsg = "Onvoldoende opslagruimte op het apparaat."
                Log.e(TAG, errorMsg)
                _installEvents.tryEmit(InstallResult.Error(status, errorMsg))
            }

            PackageInstaller.STATUS_FAILURE_INVALID -> {
                val errorMsg = "Ongeldig APK pakketbestand: $message"
                Log.e(TAG, errorMsg)
                _installEvents.tryEmit(InstallResult.Error(status, errorMsg))
            }

            else -> {
                val errorMsg = "Installatie mislukt ($status): $message"
                Log.e(TAG, errorMsg)
                _installEvents.tryEmit(InstallResult.Error(status, errorMsg))
            }
        }
    }
}
