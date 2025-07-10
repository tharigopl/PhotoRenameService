package com.example.photorenameservice

import android.app.*
import android.content.ContentUris
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.database.ContentObserver
import android.location.Geocoder
import android.net.Uri
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.provider.DocumentsContract
import android.provider.MediaStore
import android.util.Log
import androidx.annotation.RequiresApi
import androidx.core.app.NotificationCompat
import androidx.exifinterface.media.ExifInterface
import java.io.InputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class PhotoRenameService : Service() {
    private lateinit var observer: ContentObserver
    private val tag = "PhotoRenameService"
    private val PERMISSION_REQUEST_NOTIFICATION_ID = 1001

    companion object {
        // Store pending permission requests globally
        var pendingPermissionIntent: PendingIntent? = null
        var pendingUri: Uri? = null
    }

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        startForeground(1, buildNotification())

        // Check permissions and MediaStore access
        checkPermissionsAndMediaStore()

        // Create a test image if no images exist
        //createTestImage()

        // Start monitoring MediaStore for changes
        //startMediaStoreMonitoring()

        // Register the observer
        registerContentObserver()

        Log.d(tag, "Service started and observing media store")
    }

    private fun queryLatestImage() {
        try {
            val cursor = contentResolver.query(
                MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                arrayOf(
                    MediaStore.Images.Media._ID,
                    MediaStore.Images.Media.DISPLAY_NAME,
                    MediaStore.Images.Media.DATE_ADDED,
                    MediaStore.Images.Media.BUCKET_DISPLAY_NAME,
                    MediaStore.Images.Media.DATA
                ),
                null,
                null,
                "${MediaStore.Images.Media.DATE_ADDED} DESC"
            )

            if (cursor != null && cursor.moveToFirst()) {
                val id = cursor.getLong(0)
                val name = cursor.getString(1)
                val dateAdded = cursor.getLong(2)
                val bucket = cursor.getString(3)
                val data = cursor.getString(4)

                Log.d(tag, "Latest image:")
                Log.d(tag, "  ID: $id")
                Log.d(tag, "  Name: $name")
                Log.d(tag, "  Date Added: $dateAdded")
                Log.d(tag, "  Bucket: $bucket")
                Log.d(tag, "  Data: $data")

                val uri = ContentUris.withAppendedId(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, id)
                Log.d(tag, "  URI: $uri")

                // Test processing this image
                Log.d(tag, "Testing image processing...")
                debugMediaStoreColumns(uri)
            }

            cursor?.close()

        } catch (e: Exception) {
            Log.e(tag, "Error querying latest image", e)
        }
    }

    private fun startMediaStoreMonitoring() {
        Log.d(tag, "=== Starting MediaStore Monitoring ===")

        // Monitor every 5 seconds to see if new images appear
        val handler = Handler(Looper.getMainLooper())
        var lastCount = 0

        val runnable = object : Runnable {
            override fun run() {
                try {
                    val cursor = contentResolver.query(
                        MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                        arrayOf(MediaStore.Images.Media._ID),
                        null,
                        null,
                        "${MediaStore.Images.Media.DATE_ADDED} DESC"
                    )

                    val currentCount = cursor?.count ?: 0
                    cursor?.close()

                    if (currentCount != lastCount) {
                        Log.d(tag, "MediaStore image count changed: $lastCount -> $currentCount")
                        lastCount = currentCount

                        if (currentCount > 0) {
                            Log.d(tag, "Found images! Querying latest...")
                            queryLatestImage()
                        }
                    }

                    // Schedule next check
                    handler.postDelayed(this, 5000)

                } catch (e: Exception) {
                    Log.e(tag, "Error monitoring MediaStore", e)
                }
            }
        }

        handler.post(runnable)
    }

    private fun createTestImage() {
        Log.d(tag, "=== Creating Test Image ===")

        try {
            // Create a simple bitmap
            val bitmap = android.graphics.Bitmap.createBitmap(100, 100, android.graphics.Bitmap.Config.ARGB_8888)
            val canvas = android.graphics.Canvas(bitmap)
            canvas.drawColor(android.graphics.Color.RED)

            // Prepare values for MediaStore
            val values = ContentValues().apply {
                put(MediaStore.Images.Media.DISPLAY_NAME, "test_image_${System.currentTimeMillis()}.jpg")
                put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg")
                put(MediaStore.Images.Media.DATE_ADDED, System.currentTimeMillis() / 1000)
                put(MediaStore.Images.Media.DATE_TAKEN, System.currentTimeMillis())

                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    put(MediaStore.Images.Media.RELATIVE_PATH, "DCIM/Camera")
                }
            }

            // Insert into MediaStore
            val uri = contentResolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values)

            if (uri != null) {
                Log.d(tag, "Created test image with URI: $uri")

                // Write the bitmap to the URI
                contentResolver.openOutputStream(uri)?.use { outputStream ->
                    bitmap.compress(android.graphics.Bitmap.CompressFormat.JPEG, 100, outputStream)
                    Log.d(tag, "Successfully saved test image")
                }

                // Try to query it immediately
                debugMediaStoreColumns(uri)

            } else {
                Log.e(tag, "Failed to create test image URI")
            }

        } catch (e: Exception) {
            Log.e(tag, "Error creating test image", e)
        }

        Log.d(tag, "=== End Create Test Image ===")
    }

    private fun checkPermissionsAndMediaStore() {
        Log.d(tag, "=== Permission and MediaStore Check ===")

        // Check permissions
        val readPermission = checkSelfPermission(android.Manifest.permission.READ_EXTERNAL_STORAGE)
        val writePermission = checkSelfPermission(android.Manifest.permission.WRITE_EXTERNAL_STORAGE)

        Log.d(tag, "READ_EXTERNAL_STORAGE: ${if (readPermission == android.content.pm.PackageManager.PERMISSION_GRANTED) "GRANTED" else "DENIED"}")
        Log.d(tag, "WRITE_EXTERNAL_STORAGE: ${if (writePermission == android.content.pm.PackageManager.PERMISSION_GRANTED) "GRANTED" else "DENIED"}")

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val readMediaImages = checkSelfPermission(android.Manifest.permission.READ_MEDIA_IMAGES)
            Log.d(tag, "READ_MEDIA_IMAGES: ${if (readMediaImages == android.content.pm.PackageManager.PERMISSION_GRANTED) "GRANTED" else "DENIED"}")
        }

        // Check if external storage is available
        val state = android.os.Environment.getExternalStorageState()
        Log.d(tag, "External storage state: $state")

        // Try to query all media content URIs
        val urisToCheck = listOf(
            MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
            MediaStore.Images.Media.INTERNAL_CONTENT_URI,
            MediaStore.Files.getContentUri("external")
        )

        urisToCheck.forEach { uri ->
            try {
                val cursor = contentResolver.query(uri, null, null, null, null)
                val count = cursor?.count ?: -1
                cursor?.close()
                Log.d(tag, "URI $uri: $count items")
            } catch (e: Exception) {
                Log.e(tag, "Error querying $uri: ${e.message}")
            }
        }

        Log.d(tag, "=== End Permission Check ===")
    }

    private fun simpleMediaStoreTest() {
        Log.d(tag, "=== Simple MediaStore Test ===")

        try {
            // First, just try to query with basic columns
            val cursor = contentResolver.query(
                MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                arrayOf(MediaStore.Images.Media._ID, MediaStore.Images.Media.DISPLAY_NAME),
                null,
                null,
                null
            )

            if (cursor == null) {
                Log.e(tag, "Query returned null - no permission or MediaStore not available")
                return
            }

            Log.d(tag, "Query successful - found ${cursor.count} images")

            if (cursor.count > 0 && cursor.moveToFirst()) {
                val id = cursor.getLong(0)
                val name = cursor.getString(1)
                Log.d(tag, "First image: ID=$id, Name=$name")

                // Test individual URI
                val uri = ContentUris.withAppendedId(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, id)
                Log.d(tag, "Testing URI: $uri")

                // Try to query this specific URI
                val singleCursor = contentResolver.query(uri, null, null, null, null)
                if (singleCursor != null) {
                    Log.d(tag, "Single URI query successful, columns: ${singleCursor.columnNames.size}")
                    singleCursor.close()
                } else {
                    Log.e(tag, "Single URI query failed")
                }
            }

            cursor.close()

        } catch (e: Exception) {
            Log.e(tag, "Simple test failed", e)
        }

        Log.d(tag, "=== End Simple Test ===")
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            // Main service channel (low importance)
            val serviceChannel = NotificationChannel(
                "photo_organizer_channel",
                "Photo Rename Service",
                NotificationManager.IMPORTANCE_LOW
            )

            // Permission request channel (high importance for visibility)
            val permissionChannel = NotificationChannel(
                "permission_channel",
                "Permission Requests",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Notifications for requesting photo rename permissions"
                enableVibration(true)
                enableLights(true)
            }

            val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            nm.createNotificationChannel(serviceChannel)
            nm.createNotificationChannel(permissionChannel)

            Log.d(tag, "Notification channels created")
        }
    }

    private fun buildNotification() = NotificationCompat.Builder(this, "photo_organizer_channel")
        .setContentTitle("Photo Rename Service Running")
        .setSmallIcon(R.drawable.ic_launcher_foreground)
        .build()

    private fun registerContentObserver() {
        observer = object : ContentObserver(Handler(Looper.getMainLooper())) {
            override fun onChange(selfChange: Boolean, uri: Uri?) {
                super.onChange(selfChange, uri)

                Log.d(tag, "=== onChange triggered ===")
                Log.d(tag, "selfChange: $selfChange")
                Log.d(tag, "uri: $uri")

                if (uri == null) {
                    Log.w(tag, "URI is null, trying to query all recent images")
                    queryRecentImages()
                    return
                }

                Log.d(tag, "Processing URI: $uri")
                handleNewImage(uri)
            }
        }

        contentResolver.registerContentObserver(
            MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
            true,
            observer
        )

        Log.d(tag, "Registered observer for: ${MediaStore.Images.Media.EXTERNAL_CONTENT_URI}")
    }

    private fun queryRecentImages() {
        Log.d(tag, "Querying recent images as fallback...")

        try {
            val projection = arrayOf(
                MediaStore.Images.Media._ID,
                MediaStore.Images.Media.DISPLAY_NAME,
                MediaStore.Images.Media.DATE_ADDED
            )

            val sortOrder = "${MediaStore.Images.Media.DATE_ADDED} DESC"

            contentResolver.query(
                MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                projection,
                null,
                null,
                sortOrder
            )?.use { cursor ->
                Log.d(tag, "Found ${cursor.count} images")

                if (cursor.moveToFirst()) {
                    do {
                        val id = cursor.getLong(cursor.getColumnIndexOrThrow(MediaStore.Images.Media._ID))
                        val name = cursor.getString(cursor.getColumnIndexOrThrow(MediaStore.Images.Media.DISPLAY_NAME))
                        val dateAdded = cursor.getLong(cursor.getColumnIndexOrThrow(MediaStore.Images.Media.DATE_ADDED))
                        val uri = ContentUris.withAppendedId(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, id)

                        Log.d(tag, "Recent image: $name (added: $dateAdded, URI: $uri)")

                        // Process only very recent images (last 10 seconds)
                        if (System.currentTimeMillis() - (dateAdded * 1000) < 10000) {
                            Log.d(tag, "Processing recent image: $name")
                            handleNewImage(uri)
                            break // Only process the most recent one
                        }
                    } while (cursor.moveToNext())
                }
            }
        } catch (e: Exception) {
            Log.e(tag, "Error querying recent images", e)
        }
    }

    private fun testMediaStoreAccess() {
        Log.d(tag, "=== Testing MediaStore Access ===")

        try {
            val projection = arrayOf(
                MediaStore.Images.Media._ID,
                MediaStore.Images.Media.DISPLAY_NAME,
                MediaStore.Images.Media.DATA,
                MediaStore.Images.Media.BUCKET_DISPLAY_NAME,
                MediaStore.Images.Media.DATE_TAKEN,
                MediaStore.Images.Media.DATE_ADDED
            )

            // Add RELATIVE_PATH for Android Q+
            val fullProjection = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                projection + MediaStore.Images.Media.RELATIVE_PATH
            } else {
                projection
            }

            val sortOrder = "${MediaStore.Images.Media.DATE_ADDED} DESC LIMIT 5"

            contentResolver.query(
                MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                fullProjection,
                null,
                null,
                sortOrder
            )?.use { cursor ->
                Log.d(tag, "MediaStore query successful - found ${cursor.count} images")

                if (cursor.count > 0) {
                    Log.d(tag, "Available columns: ${cursor.columnNames.joinToString(", ")}")

                    var index = 0
                    while (cursor.moveToNext() && index < 3) {
                        val id = cursor.getLong(cursor.getColumnIndexOrThrow(MediaStore.Images.Media._ID))
                        val name = cursor.getString(cursor.getColumnIndexOrThrow(MediaStore.Images.Media.DISPLAY_NAME))
                        val data = cursor.getString(cursor.getColumnIndexOrThrow(MediaStore.Images.Media.DATA))
                        val bucket = cursor.getString(cursor.getColumnIndexOrThrow(MediaStore.Images.Media.BUCKET_DISPLAY_NAME))
                        val dateTaken = cursor.getLong(cursor.getColumnIndexOrThrow(MediaStore.Images.Media.DATE_TAKEN))
                        val dateAdded = cursor.getLong(cursor.getColumnIndexOrThrow(MediaStore.Images.Media.DATE_ADDED))

                        Log.d(tag, "Image $index:")
                        Log.d(tag, "  ID: $id")
                        Log.d(tag, "  Name: $name")
                        Log.d(tag, "  Data: $data")
                        Log.d(tag, "  Bucket: $bucket")
                        Log.d(tag, "  Date Taken: $dateTaken")
                        Log.d(tag, "  Date Added: $dateAdded")

                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                            try {
                                val relativePath = cursor.getString(cursor.getColumnIndexOrThrow(MediaStore.Images.Media.RELATIVE_PATH))
                                Log.d(tag, "  Relative Path: $relativePath")
                            } catch (e: Exception) {
                                Log.d(tag, "  Relative Path: <not available>")
                            }
                        }

                        // Test individual URI access
                        val uri = ContentUris.withAppendedId(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, id)
                        Log.d(tag, "  URI: $uri")

                        // Test if we can query this specific URI
                        try {
                            contentResolver.query(uri, arrayOf(MediaStore.Images.Media.DISPLAY_NAME), null, null, null)?.use {
                                Log.d(tag, "  ✓ URI query successful")
                            } ?: Log.d(tag, "  ✗ URI query returned null")
                        } catch (e: Exception) {
                            Log.d(tag, "  ✗ URI query failed: ${e.message}")
                        }

                        index++
                    }
                } else {
                    Log.w(tag, "No images found in MediaStore")
                }
            } ?: Log.e(tag, "MediaStore query returned null")

        } catch (e: Exception) {
            Log.e(tag, "Error testing MediaStore access", e)
        }

        Log.d(tag, "=== End MediaStore Test ===")
    }

    private fun handleNewImage(uri: Uri) {
        try {
            Log.d(tag, "Processing URI: $uri")

            // Add debug to see what's available
            debugMediaStoreColumns(uri)

            // Try multiple approaches to determine if this is a camera image
            val isCameraImage = checkIfCameraImage(uri)

            if (!isCameraImage) {
                Log.d(tag, "Not a camera image, skipping")
                return
            }

            Log.d(tag, "Confirmed camera image, processing...")

            // Continue with EXIF and renaming logic
            processImageRename(uri)

        } catch (e: Exception) {
            Log.e(tag, "Failed to process $uri", e)
        }
    }

    private fun debugMediaStoreColumns(uri: Uri) {
        Log.d(tag, "=== DEBUG MediaStore for URI: $uri ===")

        try {
            val cursor = contentResolver.query(uri, null, null, null, null)

            if (cursor == null) {
                Log.e(tag, "Query returned null cursor!")
                return
            }

            Log.d(tag, "Cursor count: ${cursor.count}")

            if (cursor.count == 0) {
                Log.w(tag, "Cursor is empty!")
                cursor.close()
                return
            }

            val columns = cursor.columnNames
            Log.d(tag, "Available columns (${columns.size}): ${columns.joinToString(", ")}")

            if (cursor.moveToFirst()) {
                Log.d(tag, "Reading column values:")
                columns.forEachIndexed { index, columnName ->
                    try {
                        val type = cursor.getType(index)
                        val typeStr = when (type) {
                            android.database.Cursor.FIELD_TYPE_NULL -> "NULL"
                            android.database.Cursor.FIELD_TYPE_INTEGER -> "INTEGER"
                            android.database.Cursor.FIELD_TYPE_FLOAT -> "FLOAT"
                            android.database.Cursor.FIELD_TYPE_STRING -> "STRING"
                            android.database.Cursor.FIELD_TYPE_BLOB -> "BLOB"
                            else -> "UNKNOWN"
                        }

                        val value = when (type) {
                            android.database.Cursor.FIELD_TYPE_NULL -> "NULL"
                            android.database.Cursor.FIELD_TYPE_INTEGER -> cursor.getLong(index).toString()
                            android.database.Cursor.FIELD_TYPE_FLOAT -> cursor.getDouble(index).toString()
                            android.database.Cursor.FIELD_TYPE_STRING -> cursor.getString(index) ?: "NULL"
                            android.database.Cursor.FIELD_TYPE_BLOB -> "<BLOB>"
                            else -> cursor.getString(index) ?: "NULL"
                        }

                        Log.d(tag, "  $columnName [$typeStr]: $value")
                    } catch (e: Exception) {
                        Log.e(tag, "  $columnName: <error reading value: ${e.message}>")
                    }
                }
            } else {
                Log.w(tag, "Cannot move to first row!")
            }

            cursor.close()

        } catch (e: Exception) {
            Log.e(tag, "Error in debugMediaStoreColumns", e)
        }

        Log.d(tag, "=== END DEBUG ===")
    }

    private fun checkIfCameraImage(uri: Uri): Boolean {
        // Method 1: Check BUCKET_DISPLAY_NAME
        val bucketName = contentResolver.query(
            uri,
            arrayOf(MediaStore.Images.Media.BUCKET_DISPLAY_NAME),
            null, null, null
        )?.use { c -> if (c.moveToFirst()) c.getString(0) else null }

        Log.d(tag, "Bucket name: $bucketName")

        if (bucketName != null) {
            val cameraFolders = listOf("Camera", "DCIM", "100ANDRO", "100MEDIA", "OpenCamera")
            if (cameraFolders.any { folder -> bucketName.contains(folder, ignoreCase = true) }) {
                Log.d(tag, "Camera image detected via bucket name: $bucketName")
                return true
            }
        }

        // Method 2: Check RELATIVE_PATH (Android Q+)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val relativePath = contentResolver.query(
                uri,
                arrayOf(MediaStore.Images.Media.RELATIVE_PATH),
                null, null, null
            )?.use { c -> if (c.moveToFirst()) c.getString(0) else null }

            Log.d(tag, "Relative path: $relativePath")

            if (relativePath?.contains("DCIM", ignoreCase = true) == true) {
                Log.d(tag, "Camera image detected via relative path: $relativePath")
                return true
            }
        }

        // Method 3: Check DATA column (older versions and fallback)
        val dataPath = contentResolver.query(
            uri,
            arrayOf(MediaStore.Images.Media.DATA),
            null, null, null
        )?.use { c -> if (c.moveToFirst()) c.getString(0) else null }

        Log.d(tag, "Data path: $dataPath")

        if (dataPath != null) {
            if (dataPath.contains("DCIM", ignoreCase = true) ||
                dataPath.contains("Camera", ignoreCase = true)) {
                Log.d(tag, "Camera image detected via data path: $dataPath")
                return true
            }
        }

        // Method 4: Check if it's from the default camera app (last resort)
        // This is a fallback for emulators that might not have proper folder structure
        val isFromCamera = checkIfFromCameraApp(uri)
        if (isFromCamera) {
            Log.d(tag, "Camera image detected via camera app check")
            return true
        }

        Log.d(tag, "Not detected as camera image")
        return false
    }

    private fun checkIfFromCameraApp(uri: Uri): Boolean {
        try {
            // Check if the image was just taken (within the last 5 seconds)
            val dateTaken = contentResolver.query(
                uri,
                arrayOf(MediaStore.Images.Media.DATE_TAKEN),
                null, null, null
            )?.use { c -> if (c.moveToFirst()) c.getLong(0) else null }

            if (dateTaken != null) {
                val timeDiff = System.currentTimeMillis() - dateTaken
                if (timeDiff < 5000) { // 5 seconds
                    Log.d(tag, "Image was taken recently ($timeDiff ms ago), likely from camera")
                    return true
                }
            }
        } catch (e: Exception) {
            Log.w(tag, "Error checking if from camera app", e)
        }
        return false
    }

    private fun processImageRename(uri: Uri) {
        try {
            // Read EXIF
            var lat: Double? = null
            var lon: Double? = null
            contentResolver.openInputStream(uri)?.use { stream ->
                ExifInterface(stream).latLong?.let { coords ->
                    lat = coords[0]
                    lon = coords[1]
                }
            }

            val place = if (lat != null && lon != null) {
                try {
                    Geocoder(this, Locale.getDefault())
                        .getFromLocation(lat!!, lon!!, 1)
                        ?.firstOrNull()
                        ?.let { addr ->
                            listOfNotNull(addr.locality, addr.countryName)
                                .joinToString("_") { it.replace(" ", "") }
                        } ?: "Unknown"
                } catch (e: Exception) {
                    Log.w(tag, "Geocoding failed", e)
                    "Unknown"
                }
            } else "NoGeo"

            // Get original filename for extension
            val originalName = contentResolver.query(
                uri,
                arrayOf(MediaStore.Images.Media.DISPLAY_NAME),
                null, null, null
            )?.use { c -> if (c.moveToFirst()) c.getString(0) else null }

            Log.d(tag, "Original Name $originalName")

            val extension = originalName?.substringAfterLast('.') ?: "jpg"

            // Get date taken
            val dateTaken = contentResolver.query(
                uri,
                arrayOf(MediaStore.Images.Media.DATE_TAKEN),
                null, null, null
            )?.use { c -> if (c.moveToFirst()) c.getLong(0) else null } ?: System.currentTimeMillis()

            val dt = Date(dateTaken)
            val fmt = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(dt)
            val year = SimpleDateFormat("yyyy", Locale.US).format(dt)
            val month = SimpleDateFormat("MM", Locale.US).format(dt)
            val day = SimpleDateFormat("dd", Locale.US).format(dt)
            val newName = "${fmt}_${place}.${extension}"

            Log.d(tag, "New Name $newName")

            val cv = ContentValues().apply {
                put(MediaStore.Images.Media.DISPLAY_NAME, newName)
                // Only set relative path on Android Q+
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    put(MediaStore.Images.Media.RELATIVE_PATH, "DCIM/Camera/$year/$month/$day")
                }
            }

            val updated = contentResolver.update(uri, cv, null, null)
            Log.d(tag, "Renamed to $newName (updated: $updated)")

        } catch (securityEx: android.app.RecoverableSecurityException) {
            Log.w(tag, "=== RecoverableSecurityException ===")
            Log.w(tag, "Exception message: ${securityEx.message}")
            Log.w(tag, "User action: ${securityEx.userAction}")
            Log.w(tag, "Action intent: ${securityEx.userAction.actionIntent}")
            Log.w(tag, "URI: $uri")

            // Create a notification that will launch the permission dialog
            showPermissionNotification(uri, securityEx)

        } catch (e: Exception) {
            Log.e(tag, "Failed to rename image", e)
        }
    }

    private fun showPermissionNotification(uri: Uri, securityEx: android.app.RecoverableSecurityException) {
        try {
            Log.d(tag, "=== Showing Permission Notification ===")
            Log.d(tag, "URI: $uri")
            Log.d(tag, "SecurityException: ${securityEx.message}")

            // Store the permission request globally
            pendingPermissionIntent = securityEx.userAction.actionIntent
            pendingUri = uri

            Log.d(tag, "Stored permission intent globally: $pendingPermissionIntent")

            // Store the pending permission request for later handling
            storePendingPermissionRequest(uri, securityEx)

            // Create an intent that will launch the permission dialog
            val permissionIntent = Intent(this, MainActivity::class.java).apply {
                action = "REQUEST_PERMISSION"
                putExtra("URI", uri.toString())
                putExtra("TIMESTAMP", System.currentTimeMillis())
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            }

            val pendingIntent = PendingIntent.getActivity(
                this,
                PERMISSION_REQUEST_NOTIFICATION_ID,
                permissionIntent,
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                } else {
                    PendingIntent.FLAG_UPDATE_CURRENT
                }
            )

            // Use the high-importance channel for permission notifications
            val channelId = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                "permission_channel"
            } else {
                "photo_organizer_channel"
            }

            val notification = NotificationCompat.Builder(this, channelId)
                .setContentTitle("📸 Photo Rename Permission")
                .setContentText("Tap to allow renaming of camera photos")
                .setSmallIcon(android.R.drawable.ic_dialog_alert)  // Use system icon for visibility
                .setContentIntent(pendingIntent)
                .setAutoCancel(true)
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setDefaults(NotificationCompat.DEFAULT_ALL)  // Sound, vibration, lights
                .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
                .setOngoing(false)  // Make sure it's not ongoing
                .setTimeoutAfter(30000)  // Auto-dismiss after 30 seconds
                .build()

            val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

            // Check if notifications are enabled
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                val areNotificationsEnabled = notificationManager.areNotificationsEnabled()
                Log.d(tag, "Notifications enabled: $areNotificationsEnabled")

                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    val channel = notificationManager.getNotificationChannel("permission_channel")
                    Log.d(tag, "Channel importance: ${channel?.importance}")
                    Log.d(tag, "Channel name: ${channel?.name}")
                    Log.d(tag, "Channel description: ${channel?.description}")
                }
            }

            // Clear any existing permission notifications first
            notificationManager.cancel(PERMISSION_REQUEST_NOTIFICATION_ID)

            // Post the new notification
            notificationManager.notify(PERMISSION_REQUEST_NOTIFICATION_ID, notification)

            Log.d(tag, "Permission notification posted with ID: $PERMISSION_REQUEST_NOTIFICATION_ID")
            Log.d(tag, "=== End Permission Notification ===")

        } catch (e: Exception) {
            Log.e(tag, "Failed to show permission notification", e)

            // Fallback: try a simple test notification
            try {
                val testNotification = NotificationCompat.Builder(this, "photo_organizer_channel")
                    .setContentTitle("Test Notification")
                    .setContentText("If you see this, notifications work")
                    .setSmallIcon(android.R.drawable.ic_dialog_info)
                    .build()

                val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
                nm.notify(9999, testNotification)
                Log.d(tag, "Test notification sent")
            } catch (testEx: Exception) {
                Log.e(tag, "Even test notification failed", testEx)
            }
        }
    }

    private fun storePendingPermissionRequest(uri: Uri, securityEx: android.app.RecoverableSecurityException) {
        // Store the permission request in shared preferences for later retrieval
        val prefs = getSharedPreferences("pending_permissions", Context.MODE_PRIVATE)
        prefs.edit()
            .putString("pending_uri", uri.toString())
            .putString("pending_action", securityEx.userAction.actionIntent.toString())
            .putLong("pending_time", System.currentTimeMillis())
            .apply()
        Log.d(tag, "Stored pending permission request for $uri")
    }

    private fun getPathFromDataColumn(uri: Uri): String? {
        return contentResolver.query(
            uri,
            arrayOf(MediaStore.Images.Media.DATA),
            null, null, null
        )?.use { cursor ->
            if (cursor.moveToFirst()) {
                val fullPath = cursor.getString(0)
                Log.d(tag, "DATA column path: $fullPath")

                // Extract relative path from full path
                when {
                    fullPath.contains("DCIM/Camera") -> {
                        val startIdx = fullPath.indexOf("DCIM/Camera")
                        fullPath.substring(startIdx)
                    }
                    fullPath.contains("DCIM") -> {
                        val startIdx = fullPath.indexOf("DCIM")
                        fullPath.substring(startIdx)
                    }
                    fullPath.contains("Camera") -> {
                        val startIdx = fullPath.indexOf("Camera")
                        "DCIM/${fullPath.substring(startIdx)}"
                    }
                    else -> {
                        Log.d(tag, "Path doesn't contain expected camera folders")
                        null
                    }
                }
            } else null
        }
    }

    override fun onBind(intent: android.content.Intent?) = null

    override fun onDestroy() {
        contentResolver.unregisterContentObserver(observer)
        Log.d(tag, "Service destroyed")
        super.onDestroy()
    }
}