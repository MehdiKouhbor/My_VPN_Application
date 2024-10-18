package com.example.myvpnapplication

import android.Manifest
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.net.VpnService
import android.os.Build
import android.os.Bundle
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import com.example.myvpnapplication.databinding.ActivityMainBinding
import kotlinx.coroutines.launch

class MainActivity : AppCompatActivity() {
    private lateinit var binding: ActivityMainBinding
    private lateinit var vpnLauncher: ActivityResultLauncher<Intent>
    private lateinit var permissionLauncher: ActivityResultLauncher<Array<String>>

    // متغیر برای بررسی وضعیت VPN
    private var isVpnActive = false

    // Receiver برای دریافت وضعیت VPN
    private val vpnStatusReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            // دریافت وضعیت VPN و بروزرسانی وضعیت دکمه
            isVpnActive = intent?.getBooleanExtra("VPN_ACTIVE", false) ?: false
            updateButtonState()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        enableEdgeToEdge()
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        // لانچر برای درخواست دسترسی VPN
        vpnLauncher =
            registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
                if (result.resultCode == RESULT_OK) {
                    startVpnService() // شروع سرویس VPN
                }
            }

        // لانچر برای درخواست مجوزها
        permissionLauncher = registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { permissions ->
            val allPermissionsGranted = permissions.values.all { it }
            if (allPermissionsGranted) {
                // مجوزها داده شد
                startVpnService() // شروع سرویس VPN در صورت نیاز
            } else {
                // مجوزها داده نشدند
                // مدیریت عدم اعطای مجوز
            }
        }

        // درخواست مجوزها
        requestPermissions()

        // دکمه شروع یا قطع VPN
        binding.startVpnButton.setOnClickListener {
            if (!isVpnActive) {
                val intent: Intent? = VpnService.prepare(this)
                if (intent != null) {
                    vpnLauncher.launch(intent) // اگر intent خالی نیست، آن را به لانچر ارسال کن
                } else {
                    startVpnService() // اگر نیازی به درخواست نیست، مستقیم سرویس VPN را شروع کن
                }
            } else {
                stopVpnService() // اگر VPN فعال است، سرویس را متوقف کن
            }
        }
    }

    override fun onResume() {
        super.onResume()
        // ثبت Receiver برای دریافت وضعیت VPN
        LocalBroadcastManager.getInstance(this)
            .registerReceiver(vpnStatusReceiver, IntentFilter("VPN_STATUS_ACTION"))
    }

    override fun onPause() {
        super.onPause()
        // لغو ثبت Receiver
        LocalBroadcastManager.getInstance(this)
            .unregisterReceiver(vpnStatusReceiver)
    }

    // متد درخواست مجوزها
    private fun requestPermissions() {
        val permissionsToRequest = mutableListOf<String>()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            permissionsToRequest.add(Manifest.permission.POST_NOTIFICATIONS)
        }

        // اگر مجوز VPN وجود ندارد، آن را به لیست اضافه کن
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.BIND_VPN_SERVICE) != PackageManager.PERMISSION_GRANTED) {
            permissionsToRequest.add(Manifest.permission.BIND_VPN_SERVICE)
        }

        // اگر لیست خالی نیست، مجوزها را درخواست کن
        if (permissionsToRequest.isNotEmpty()) {
            permissionLauncher.launch(permissionsToRequest.toTypedArray())
        }
    }

    // متد شروع سرویس VPN
    private fun startVpnService() {
        val intent = Intent(this, MyVpnService::class.java).apply {
            action = "START_VPN" // اکشن شروع VPN
        }
        startService(intent)
        isVpnActive = true
        updateButtonState()
    }

    // متد قطع سرویس VPN
    private fun stopVpnService() {
        val intent = Intent(this, MyVpnService::class.java).apply {
            action = "STOP_VPN" // اکشن قطع VPN
        }
        startService(intent)
        isVpnActive = false
        updateButtonState()
    }

    // به‌روزرسانی متن دکمه با توجه به وضعیت VPN
    private fun updateButtonState() {
        binding.startVpnButton.text = if (isVpnActive) "قطع اتصال" else "اتصال"
    }
}
