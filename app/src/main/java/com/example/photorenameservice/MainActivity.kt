package com.example.photorenameservice

import android.app.PendingIntent
import android.content.ContentUris
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.MediaStore
import android.util.Log
import androidx.appcompat.app.AppCompatActivity
import com.example.photorenameservice.databinding.ActivityMainBinding
import android.Manifest
import android.content.pm.PackageManager
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.RequiresApi

class MainActivity : AppCompatActivity() {

    companion object {
        private const val REQUEST_PERMS = 1001
        private const val TAG = "MainActivity"
    }

    private lateinit var binding: ActivityMainBinding

    // Register for permission result
    private val permissionResultLauncher = registerForActivityResult(
        ActivityResultContracts.StartIntentSenderForResult()
    ) { result ->
        Log.d(TAG, "Permission result: ${result.resultCode}")
        if (result.resultCode == RESULT_OK) {
            binding.tvStatus.text = "Permission granted - photo renamed"
            Toast.makeText(this, "Permission granted", Toast.LENGTH_SHORT).show()
        } else {
            binding.tvStatus.text = "Permission denied"
            Toast.makeText(this, "Permission denied", Toast.LENGTH_SHORT).show()
        }
    }

    @RequiresApi(Build.VERSION_CODES.Q)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        Log.d(TAG, "=== MainActivity onCreate ===")
        Log.d(TAG, "Intent action: ${intent.action}")
        Log.d(TAG, "Intent extras: ${intent.extras?.keySet()?.joinToString(", ")}")

        // Check if this was launched from a permission notification
        handlePermissionRequest()

        // Request permissions if needed
        if (!hasAllPermissions()) {
            ActivityCompat.requestPermissions(
                this,
                getRequiredPermissions(),
                REQUEST_PERMS
            )
        }

        // Check and request notification permission separately for older Android versions
        checkNotificationPermission()

        binding.btnStart.setOnClickListener {
            if (hasAllPermissions()) {
                controlService(true)
            } else Toast.makeText(
                this,
                "Storage permission required",
                Toast.LENGTH_SHORT
            ).show()
        }
        binding.btnStop.setOnClickListener { controlService(false) }

        // Add test notification button for debugging
        binding.root.setOnLongClickListener {
            testNotification()
            true
        }

        // Add double-tap to check for pending permissions
        var lastTapTime = 0L
        binding.tvStatus.setOnClickListener {
            val currentTime = System.currentTimeMillis()
            if (currentTime - lastTapTime < 500) { // Double tap within 500ms
                checkPendingPermissions()
            }
            lastTapTime = currentTime
        }

        // Add a manual test button for permission requests
        binding.btnStart.setOnLongClickListener {
            testManualPermissionRequest()
            true
        }
    }

    private fun handlePermissionRequest() {
        Log.d(TAG, "=== handlePermissionRequest ===")
        Log.d(TAG, "Intent: $intent")
        Log.d(TAG, "Action: ${intent.action}")
        Log.d(TAG, "Extras: ${intent.extras}")

        if (intent.extras != null) {
            for (key in intent.extras!!.keySet()) {
                val value = intent.extras!!.get(key)
                Log.d(TAG, "Extra: $key = $value (${value?.javaClass?.simpleName})")
            }
        }

        // Check if this activity was launched from a permission notification
        when (intent.action) {
            "REQUEST_PERMISSION" -> {
                Log.d(TAG, "Processing permission request...")

                // Get the permission intent from the global storage
                val permissionIntent = PhotoRenameService.pendingPermissionIntent
                val pendingUri = PhotoRenameService.pendingUri
                val uriString = intent.getStringExtra("URI")

                Log.d(TAG, "Global permission intent: $permissionIntent")
                Log.d(TAG, "Global pending URI: $pendingUri")
                Log.d(TAG, "Intent URI string: $uriString")

                if (permissionIntent != null) {
                    try {
                        Log.d(TAG, "Launching permission dialog...")
                        binding.tvStatus.text = "Requesting photo permission..."

                        // Launch the permission dialog
                        permissionResultLauncher.launch(
                            androidx.activity.result.IntentSenderRequest.Builder(permissionIntent.intentSender).build()
                        )

                        // Clear the global permission intent after using it
                        PhotoRenameService.pendingPermissionIntent = null
                        PhotoRenameService.pendingUri = null

                    } catch (e: Exception) {
                        Log.e(TAG, "Failed to launch permission intent", e)
                        Toast.makeText(this, "Failed to request permission: ${e.message}", Toast.LENGTH_LONG).show()
                        binding.tvStatus.text = "Permission request failed"
                    }
                } else {
                    Log.e(TAG, "No global permission intent found")
                    Toast.makeText(this, "No permission request found", Toast.LENGTH_SHORT).show()
                    binding.tvStatus.text = "No permission request found"
                }
            }
            else -> {
                Log.d(TAG, "Not a permission request action: ${intent.action}")
            }
        }

        Log.d(TAG, "=== end handlePermissionRequest ===")
    }

    /** Build required permissions based on API level */
    private fun getRequiredPermissions(): Array<String> {
        val perms = mutableListOf<String>()
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
            perms.add(Manifest.permission.READ_EXTERNAL_STORAGE)
        }
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
            perms.add(Manifest.permission.WRITE_EXTERNAL_STORAGE)
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            perms.add(Manifest.permission.READ_MEDIA_IMAGES)
            perms.add(Manifest.permission.POST_NOTIFICATIONS) // Add notification permission
        }
        return perms.toTypedArray()
    }

    private fun hasAllPermissions(): Boolean =
        getRequiredPermissions().all { perm ->
            ContextCompat.checkSelfPermission(
                this,
                perm
            ) == PackageManager.PERMISSION_GRANTED
        }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == REQUEST_PERMS) {
            val denied = grantResults.any { it != PackageManager.PERMISSION_GRANTED }
            binding.tvStatus.text = if (denied) "Permissions denied" else "Permissions granted"
        }
    }

    private fun controlService(start: Boolean) {
        val intent = Intent(this, PhotoRenameService::class.java)
        if (start) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) startForegroundService(intent)
            else startService(intent)
            binding.tvStatus.text = "Service running"
        } else {
            stopService(intent)
            binding.tvStatus.text = "Service stopped"
        }
    }

    private fun testNotification() {
        Log.d(TAG, "Testing notification...")

        try {
            val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as android.app.NotificationManager

            // Create test channel if needed
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                val channel = android.app.NotificationChannel(
                    "test_channel",
                    "Test Notifications",
                    android.app.NotificationManager.IMPORTANCE_HIGH
                )
                notificationManager.createNotificationChannel(channel)
            }

            val testNotification = androidx.core.app.NotificationCompat.Builder(this, "test_channel")
                .setContentTitle("Test Notification")
                .setContentText("Long press on main area triggered this test")
                .setSmallIcon(android.R.drawable.ic_dialog_info)
                .setPriority(androidx.core.app.NotificationCompat.PRIORITY_HIGH)
                .setDefaults(androidx.core.app.NotificationCompat.DEFAULT_ALL)
                .build()

            notificationManager.notify(12345, testNotification)
            Toast.makeText(this, "Test notification sent", Toast.LENGTH_SHORT).show()

        } catch (e: Exception) {
            Log.e(TAG, "Test notification failed", e)
            Toast.makeText(this, "Test notification failed: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }

    private fun checkNotificationPermission() {
        val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as android.app.NotificationManager

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            val areNotificationsEnabled = notificationManager.areNotificationsEnabled()
            Log.d(TAG, "Notifications enabled: $areNotificationsEnabled")

            if (!areNotificationsEnabled) {
                // Show dialog to guide user to enable notifications
                androidx.appcompat.app.AlertDialog.Builder(this)
                    .setTitle("Enable Notifications")
                    .setMessage("This app needs notifications to request permission for renaming photos. Please enable notifications in the next screen.")
                    .setPositiveButton("Open Settings") { _, _ ->
                        openNotificationSettings()
                    }
                    .setNegativeButton("Skip", null)
                    .show()
            }
        }
    }

    private fun openNotificationSettings() {
        try {
            val intent = Intent().apply {
                when {
                    Build.VERSION.SDK_INT >= Build.VERSION_CODES.O -> {
                        action = android.provider.Settings.ACTION_APP_NOTIFICATION_SETTINGS
                        putExtra(android.provider.Settings.EXTRA_APP_PACKAGE, packageName)
                    }
                    else -> {
                        action = android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS
                        data = Uri.fromParts("package", packageName, null)
                    }
                }
            }
            startActivity(intent)
        } catch (e: Exception) {
            Toast.makeText(this, "Could not open notification settings", Toast.LENGTH_SHORT).show()
        }
    }

    private fun checkPendingPermissions() {
        Log.d(TAG, "Checking for pending permissions...")

        val prefs = getSharedPreferences("pending_permissions", Context.MODE_PRIVATE)
        val pendingUri = prefs.getString("pending_uri", null)
        val pendingTime = prefs.getLong("pending_time", 0)

        if (pendingUri != null) {
            val age = System.currentTimeMillis() - pendingTime
            Log.d(TAG, "Found pending permission for $pendingUri (${age}ms old)")

            Toast.makeText(this, "Found pending permission request from ${age/1000}s ago", Toast.LENGTH_LONG).show()

            // Clear the pending request
            prefs.edit().clear().apply()
        } else {
            Log.d(TAG, "No pending permissions found")
            Toast.makeText(this, "No pending permissions found", Toast.LENGTH_SHORT).show()
        }
    }

    @RequiresApi(Build.VERSION_CODES.Q)
    private fun testManualPermissionRequest() {
        Log.d(TAG, "Testing manual permission request...")

        // Get the most recent photo to test permission on
        try {
            val cursor = contentResolver.query(
                MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                arrayOf(MediaStore.Images.Media._ID, MediaStore.Images.Media.DISPLAY_NAME),
                null,
                null,
                "${MediaStore.Images.Media.DATE_ADDED} DESC"
            )

            if (cursor != null && cursor.moveToFirst()) {
                val id = cursor.getLong(0)
                val name = cursor.getString(1)
                cursor.close()

                val uri = ContentUris.withAppendedId(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, id)
                Log.d(TAG, "Testing permission on: $name (URI: $uri)")

                // Try to rename it to test permission
                val cv = ContentValues().apply {
                    put(MediaStore.Images.Media.DISPLAY_NAME, "test_rename_$name")
                }

                try {
                    val updated = contentResolver.update(uri, cv, null, null)
                    Log.d(TAG, "Successfully renamed without permission request (updated: $updated)")
                    Toast.makeText(this, "Rename successful - no permission needed", Toast.LENGTH_SHORT).show()

                    // Revert the name
                    val revertCv = ContentValues().apply {
                        put(MediaStore.Images.Media.DISPLAY_NAME, name)
                    }
                    contentResolver.update(uri, revertCv, null, null)

                } catch (securityEx: android.app.RecoverableSecurityException) {
                    Log.d(TAG, "Got RecoverableSecurityException - testing permission flow")

                    // Test the permission request manually
                    try {
                        permissionResultLauncher.launch(
                            androidx.activity.result.IntentSenderRequest.Builder(
                                securityEx.userAction.actionIntent.intentSender
                            ).build()
                        )
                        binding.tvStatus.text = "Manual permission test launched..."
                    } catch (e: Exception) {
                        Log.e(TAG, "Failed to launch manual permission test", e)
                        Toast.makeText(this, "Failed to launch permission test: ${e.message}", Toast.LENGTH_LONG).show()
                    }
                }

            } else {
                cursor?.close()
                Toast.makeText(this, "No photos found to test on", Toast.LENGTH_SHORT).show()
            }

        } catch (e: Exception) {
            Log.e(TAG, "Error in manual permission test", e)
            Toast.makeText(this, "Error testing permission: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }
}