package com.example.photorenameservice

import android.content.Intent
import android.os.Build
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import com.example.photorenameservice.databinding.ActivityMainBinding
import android.Manifest
import android.content.pm.PackageManager
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import android.widget.Toast

class MainActivity : AppCompatActivity() {

    companion object {
        private const val REQUEST_PERMS = 1001
    }

    private lateinit var binding: ActivityMainBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // Request permissions if needed
        if (!hasAllPermissions()) {
            ActivityCompat.requestPermissions(
                this,
                getRequiredPermissions(),
                REQUEST_PERMS
            )
        }

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

//    companion object {
//        private const val REQUEST_PERMS = 1
//    }
//
//    /** Build required permissions based on API level */
//    private fun getRequiredPermissions(): Array<String> {
//        val perms = mutableListOf(
//            Manifest.permission.READ_EXTERNAL_STORAGE
//        )
//        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
//            perms.add(Manifest.permission.WRITE_EXTERNAL_STORAGE)
//        }
//        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
//            perms.add(Manifest.permission.READ_MEDIA_IMAGES)
//        }
//        return perms.toTypedArray()
//    }
//
//    private lateinit var binding: ActivityMainBinding
//
//    override fun onCreate(savedInstanceState: Bundle?) {
//        super.onCreate(savedInstanceState)
//        binding = ActivityMainBinding.inflate(layoutInflater)
//        setContentView(binding.root)
//
//        // Request permissions if needed
//        if (!hasAllPermissions()) {
//            ActivityCompat.requestPermissions(this, getRequiredPermissions(), REQUEST_PERMS)
//        }
//
//        binding.btnStart.setOnClickListener {
//            if (hasAllPermissions()) {
//                controlService(true)
//            } else Toast.makeText(this, "Storage permission required", Toast.LENGTH_SHORT).show()
//        }
//        binding.btnStop.setOnClickListener { controlService(false) }
//    }
//
//    private fun hasAllPermissions(): Boolean {
//        // Check each required permission
//        return getRequiredPermissions().all { perm ->
//            ContextCompat.checkSelfPermission(this, perm) == PackageManager.PERMISSION_GRANTED
//        }
//    }
//    override fun onRequestPermissionsResult(
//        requestCode: Int, permissions: Array<out String>, grantResults: IntArray
//    ) {
//        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
//        if (requestCode == REQUEST_PERMS) {
//            val denied = grantResults.any { it != PackageManager.PERMISSION_GRANTED }
//            binding.tvStatus.text = if (denied) "Permissions denied" else "Permissions granted"
//        }
//    }
//
//    private fun controlService(start: Boolean) {
//        val intent = Intent(this, PhotoRenameService::class.java)
//        if (start) {
//            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) startForegroundService(intent) else startService(intent)
//            binding.tvStatus.text = "Service running"
//        } else {
//            stopService(intent)
//            binding.tvStatus.text = "Service stopped"
//        }
//    }

//    companion object {
//        private const val REQUEST_PERMS = 1001
//        private val PERMISSIONS = arrayOf(
//            Manifest.permission.READ_EXTERNAL_STORAGE,
//            Manifest.permission.WRITE_EXTERNAL_STORAGE,      // For API < 29
//            Manifest.permission.READ_MEDIA_IMAGES            // For API >= 33
//        )
//    }
//
//    private lateinit var binding: ActivityMainBinding
//
//    override fun onCreate(savedInstanceState: Bundle?) {
//        super.onCreate(savedInstanceState)
//        binding = ActivityMainBinding.inflate(layoutInflater)
//        setContentView(binding.root)
//
//        // Request permissions if needed
//        if (!hasAllPermissions()) {
//            ActivityCompat.requestPermissions(this, PERMISSIONS, REQUEST_PERMS)
//        }
//
//        binding.btnStart.setOnClickListener {
//            if (hasAllPermissions()) {
//                controlService(true)
//            } else Toast.makeText(this, "Storage permission required", Toast.LENGTH_SHORT).show()
//        }
//        binding.btnStop.setOnClickListener { controlService(false) }
//    }
//
//    private fun hasAllPermissions(): Boolean {
//        return PERMISSIONS.all { perm ->
//            ContextCompat.checkSelfPermission(this, perm) == PackageManager.PERMISSION_GRANTED
//        }
//    }
//
//    override fun onRequestPermissionsResult(
//        requestCode: Int, permissions: Array<out String>, grantResults: IntArray
//    ) {
//        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
//        if (requestCode == REQUEST_PERMS) {
//            val denied = grantResults.any { it != PackageManager.PERMISSION_GRANTED }
//            binding.tvStatus.text = if (denied) "Permissions denied" else "Permissions granted"
//        }
//    }
//
//    private fun controlService(start: Boolean) {
//        val intent = Intent(this, PhotoRenameService::class.java)
//        if (start) {
//            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) startForegroundService(intent) else startService(intent)
//            binding.tvStatus.text = "Service running"
//        } else {
//            stopService(intent)
//            binding.tvStatus.text = "Service stopped"
//        }
//    }
//    companion object {
//        private const val REQUEST_PERMS = 1001
//        private val PERMS = arrayOf(
//            Manifest.permission.READ_EXTERNAL_STORAGE,
//            Manifest.permission.WRITE_EXTERNAL_STORAGE,           // for API<29
//            Manifest.permission.READ_MEDIA_IMAGES                 // for API 33+
//        )
//    }
//
//    private lateinit var binding: ActivityMainBinding
//
//    override fun onCreate(savedInstanceState: Bundle?) {
//        super.onCreate(savedInstanceState)
//        binding = ActivityMainBinding.inflate(layoutInflater)
//        setContentView(binding.root)
//
//        binding.btnStart.setOnClickListener { controlService(true) }
//        binding.btnStop.setOnClickListener { controlService(false) }
//    }
//
//    private fun controlService(start: Boolean) {
//        val intent = Intent(this, PhotoRenameService::class.java)
//        if (start) {
//            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) startForegroundService(intent)
//            else startService(intent)
//            binding.tvStatus.text = "Service running"
//        } else {
//            stopService(intent)
//            binding.tvStatus.text = "Service stopped"
//        }
//    }
}