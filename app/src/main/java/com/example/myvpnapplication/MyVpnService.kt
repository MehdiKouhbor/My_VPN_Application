package com.example.myvpnapplication

import android.app.*
import android.content.Intent
import android.net.VpnService
import android.os.IBinder
import android.os.ParcelFileDescriptor
import androidx.core.app.NotificationCompat
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import kotlinx.coroutines.*

class MyVpnService : VpnService() {

    private var vpnInterface: ParcelFileDescriptor? = null
    private var isVpnConnected = false
    private var job: Job? = null

    companion object {
        const val CHANNEL_ID = "VPN_CHANNEL_ID"
        const val NOTIFICATION_ID = 1
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        intent?.let {
            when (it.action) {
                "START_VPN" -> startVpn()
                "STOP_VPN" -> stopVpn()
            }
        }
        return START_STICKY
    }

    private fun startVpn() {
        val builder = Builder()

        // تنظیمات تونل VPN و DNS ها
        builder.addDnsServer("10.202.10.202")
        builder.addDnsServer("10.202.10.102")
        builder.setSession("My DNS VPN")
        builder.setMtu(1500)
        builder.addAddress("192.168.1.2", 24)

        // ساخت تونل VPN
        vpnInterface = builder.establish()
        isVpnConnected = true // اتصال VPN برقرار است

        // نمایش نوتیفیکیشن VPN
        startForeground(
            NOTIFICATION_ID,
            createNotification("اتصال VPN برقرار است")
        )

        // بروزرسانی وضعیت VPN
        updateVpnStatus()

        // بررسی وضعیت اتصال به صورت دوره‌ای
        checkVpnConnection()
    }

    // ساخت نوتیفیکیشن
    private fun createNotification(status: String): Notification {
        val notificationIntent = Intent(this, MainActivity::class.java)
        val pendingIntent =
            PendingIntent.getActivity(this, 0, notificationIntent, PendingIntent.FLAG_IMMUTABLE)

        // دکمه قطع ارتباط
        val stopIntent = Intent(this, MyVpnService::class.java).apply {
            action = "STOP_VPN"
        }
        val stopPendingIntent = PendingIntent.getService(this, 0, stopIntent, PendingIntent.FLAG_IMMUTABLE)

        // ساخت نوتیفیکیشن
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("وضعیت VPN")
            .setContentText(status)
            .setSmallIcon(R.drawable.ic_vpn)
            .setContentIntent(pendingIntent)
            .addAction(R.drawable.ic_stop, "قطع ارتباط", stopPendingIntent) // دکمه قطع ارتباط
            .setOngoing(true)
            .setSound(null) // غیرفعال کردن صدا
            .build()
    }

    // متد بروزرسانی وضعیت VPN
    private fun updateVpnStatus() {
        val intent = Intent("VPN_STATUS_ACTION").apply {
            putExtra("VPN_ACTIVE", isVpnConnected)
        }
        LocalBroadcastManager.getInstance(this).sendBroadcast(intent)
    }

    // متد بررسی اتصال VPN
    private fun checkVpnConnection() {
        job = CoroutineScope(Dispatchers.IO).launch {
            while (isVpnConnected) {
                if (vpnInterface == null || !isVpnStillConnected()) {
                    enableKillSwitch()
                    break
                }
                delay(1000) // بررسی هر ۱ ثانیه یک بار
            }
        }
    }

    // بررسی اینکه آیا VPN هنوز متصل است یا خیر
    private fun isVpnStillConnected(): Boolean {
        return true // فرض کنید VPN متصل است (در کد واقعی باید به صورت دقیق بررسی شود)
    }

    // فعال‌سازی کیل سوییچ
    private fun enableKillSwitch() {
        isVpnConnected = false
        // بستن تونل VPN
        vpnInterface?.close()
        vpnInterface = null

        // بروزرسانی وضعیت VPN
        updateVpnStatus()

        // بستن سرویس
        stopSelf()
    }

    // قطع ارتباط VPN
    private fun stopVpn() {
        isVpnConnected = false
        vpnInterface?.close()
        vpnInterface = null
        stopForeground(true) // توقف نوتیفیکیشن
        updateVpnStatus() // بروزرسانی وضعیت VPN
        job?.cancel() // متوقف کردن کارهای Coroutines
        stopSelf() // توقف سرویس
    }

    override fun onDestroy() {
        super.onDestroy()
        vpnInterface?.close()
        vpnInterface = null
        job?.cancel() // متوقف کردن کارهای Coroutines
    }

    override fun onBind(intent: Intent?): IBinder? {
        return null
    }
}
