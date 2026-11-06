package com.adblock.browser

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.net.VpnService
import android.os.Build
import android.os.ParcelFileDescriptor
import android.system.ErrnoException
import android.system.Os
import android.system.OsConstants
import android.system.StructPollfd
import android.util.Log
import java.io.FileDescriptor
import java.io.FileInputStream
import java.io.FileOutputStream
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

/**
 * Chặn quảng cáo cho TOÀN BỘ điện thoại (Chrome, Firefox, app khác…) bằng cách lọc DNS.
 *
 * Tạo một VPN cục bộ (không gửi dữ liệu đi đâu cả) mà CHỈ định tuyến các truy vấn DNS
 * vào ứng dụng. Truy vấn tới tên miền quảng cáo được trả lời "không tồn tại" (NXDOMAIN),
 * các truy vấn khác được chuyển tiếp tới máy chủ DNS thật. Lưu lượng web bình thường
 * không đi qua VPN nên không làm chậm mạng.
 */
class AdBlockVpnService : VpnService() {

    private var tun: ParcelFileDescriptor? = null
    private var worker: Thread? = null
    private var stopPipe: Array<FileDescriptor>? = null
    private var pool: ExecutorService? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            stopVpn()
            return START_NOT_STICKY
        }
        startForegroundNotification()
        if (tun == null) startVpn()
        return START_STICKY
    }

    private fun startVpn() {
        AdBlocker.ensureLoaded(applicationContext)
        val builder = Builder()
            .setSession(getString(R.string.app_name))
            .addAddress(VPN_ADDRESS, 24)
            .addDnsServer(VPN_DNS)
            .addRoute(VPN_DNS, 32)
            .setBlocking(true)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) builder.setMetered(false)
        builder.setConfigureIntent(
            PendingIntent.getActivity(
                this, 0, Intent(this, MainActivity::class.java),
                PendingIntent.FLAG_IMMUTABLE
            )
        )

        val fd = try {
            builder.establish()
        } catch (e: Exception) {
            Log.e(TAG, "establish failed", e)
            null
        }
        if (fd == null) {
            // Chưa được cấp quyền VPN (hoặc bị VPN khác chiếm)
            stopVpn()
            return
        }
        tun = fd
        val pipe = Os.pipe()
        stopPipe = pipe
        pool = Executors.newFixedThreadPool(8)
        running = true
        getSharedPreferences("settings", MODE_PRIVATE).edit().putBoolean("vpn_enabled", true).apply()

        worker = Thread({ runLoop(fd, pipe[0]) }, "dns-vpn").also { it.start() }

        // Lần đầu: tự tải danh sách chặn đầy đủ (~100.000 tên miền)
        if (!AdBlocker.hasRemoteList(this)) {
            pool?.execute {
                try {
                    AdBlocker.updateFromInternet(applicationContext)
                } catch (e: Exception) {
                    Log.w(TAG, "auto update failed", e)
                }
            }
        }
    }

    private fun runLoop(pfd: ParcelFileDescriptor, stopFd: FileDescriptor) {
        val input = FileInputStream(pfd.fileDescriptor)
        val output = FileOutputStream(pfd.fileDescriptor)
        val buf = ByteArray(32767)
        val tunPoll = StructPollfd().apply {
            this.fd = pfd.fileDescriptor
            events = OsConstants.POLLIN.toShort()
        }
        val stopPoll = StructPollfd().apply {
            this.fd = stopFd
            events = OsConstants.POLLIN.toShort()
        }
        try {
            while (true) {
                tunPoll.revents = 0
                stopPoll.revents = 0
                try {
                    Os.poll(arrayOf(tunPoll, stopPoll), -1)
                } catch (e: ErrnoException) {
                    if (e.errno == OsConstants.EINTR) continue
                    throw e
                }
                if (stopPoll.revents.toInt() != 0) break
                if (tunPoll.revents.toInt() and OsConstants.POLLIN == 0) continue
                val len = input.read(buf)
                if (len <= 0) continue
                handlePacket(buf.copyOf(len), output)
            }
        } catch (e: Exception) {
            Log.w(TAG, "loop ended", e)
        }
    }

    private fun handlePacket(packet: ByteArray, output: FileOutputStream) {
        val q = Packets.parseDnsQuery(packet) ?: return
        val domain = q.domain
        if (domain != null && AdBlocker.isDomainBlocked(domain)) {
            AdBlocker.recordBlocked(domain)
            val resp = Packets.blockedResponse(q.dnsPayload) ?: return
            write(output, Packets.buildUdpReply(q, resp))
            return
        }
        // Chuyển tiếp tới DNS thật (trên thread khác để không chặn vòng lặp)
        pool?.execute {
            val resp = forward(q.dnsPayload) ?: return@execute
            write(output, Packets.buildUdpReply(q, resp))
        }
    }

    private fun forward(query: ByteArray): ByteArray? {
        for (server in UPSTREAM_DNS) {
            try {
                DatagramSocket().use { socket ->
                    protect(socket) // không cho gói này quay lại VPN
                    socket.soTimeout = 5000
                    val addr = InetAddress.getByName(server)
                    socket.send(DatagramPacket(query, query.size, addr, 53))
                    val rbuf = ByteArray(4096)
                    val rp = DatagramPacket(rbuf, rbuf.size)
                    socket.receive(rp)
                    return rbuf.copyOf(rp.length)
                }
            } catch (e: Exception) {
                Log.d(TAG, "upstream $server failed: ${e.message}")
            }
        }
        return null
    }

    private fun write(output: FileOutputStream, packet: ByteArray) {
        try {
            synchronized(output) { output.write(packet) }
        } catch (e: Exception) {
            Log.d(TAG, "write failed: ${e.message}")
        }
    }

    private fun stopVpn() {
        running = false
        getSharedPreferences("settings", MODE_PRIVATE).edit().putBoolean("vpn_enabled", false).apply()
        stopPipe?.let { p ->
            try { Os.write(p[1], byteArrayOf(1), 0, 1) } catch (_: Exception) {}
        }
        worker?.join(1000)
        worker = null
        pool?.shutdownNow()
        pool = null
        stopPipe?.forEach { try { Os.close(it) } catch (_: Exception) {} }
        stopPipe = null
        try { tun?.close() } catch (_: Exception) {}
        tun = null
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            stopForeground(STOP_FOREGROUND_REMOVE)
        } else {
            @Suppress("DEPRECATION") stopForeground(true)
        }
        stopSelf()
    }

    override fun onRevoke() {
        // Người dùng tắt VPN trong Cài đặt hoặc bật VPN khác
        stopVpn()
    }

    override fun onDestroy() {
        if (tun != null) stopVpn()
        super.onDestroy()
    }

    private fun startForegroundNotification() {
        val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val builder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            nm.createNotificationChannel(
                NotificationChannel(CHANNEL_ID, getString(R.string.channel_name), NotificationManager.IMPORTANCE_LOW)
            )
            Notification.Builder(this, CHANNEL_ID)
        } else {
            @Suppress("DEPRECATION") Notification.Builder(this)
        }
        val openApp = PendingIntent.getActivity(
            this, 0, Intent(this, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE
        )
        val stop = PendingIntent.getService(
            this, 1, Intent(this, AdBlockVpnService::class.java).setAction(ACTION_STOP),
            PendingIntent.FLAG_IMMUTABLE
        )
        val notification = builder
            .setContentTitle(getString(R.string.notif_title))
            .setContentText(getString(R.string.notif_text))
            .setSmallIcon(R.drawable.ic_shield)
            .setContentIntent(openApp)
            .setOngoing(true)
            .addAction(Notification.Action.Builder(null, getString(R.string.turn_off), stop).build())
            .build()
        if (Build.VERSION.SDK_INT >= 34) {
            startForeground(NOTIF_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
        } else {
            startForeground(NOTIF_ID, notification)
        }
    }

    companion object {
        private const val TAG = "AdBlockVpn"
        const val ACTION_STOP = "com.adblock.browser.STOP"
        private const val CHANNEL_ID = "vpn"
        private const val NOTIF_ID = 1
        private const val VPN_ADDRESS = "10.111.222.1"
        const val VPN_DNS = "10.111.222.2"
        private val UPSTREAM_DNS = listOf("1.1.1.1", "8.8.8.8", "9.9.9.9")

        @Volatile
        var running = false
            private set

        fun start(context: Context) {
            val i = Intent(context, AdBlockVpnService::class.java)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) context.startForegroundService(i)
            else context.startService(i)
        }

        fun stop(context: Context) {
            context.startService(Intent(context, AdBlockVpnService::class.java).setAction(ACTION_STOP))
        }
    }
}
