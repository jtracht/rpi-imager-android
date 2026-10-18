package de.jtracht.piimager.data

import android.app.*
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.IBinder
import android.os.Build
import android.os.PowerManager
import de.jtracht.piimager.MainActivity
import de.jtracht.piimager.R
import de.jtracht.piimager.domain.Imaging
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.File

data class WriteState(val busy: Boolean = false, val phase: Int = 0, val progress: Float = 0f, val error: String? = null)

class ImagingService : Service() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var job: Job? = null
    private var lastNotification = 0L

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == CANCEL) {
            job?.cancel()
            if (job == null) stopSelf()
            return START_NOT_STICKY
        }
        if (job?.isActive == true) return START_NOT_STICKY
        val notifications = getSystemService(NotificationManager::class.java)
        notifications.createNotificationChannel(NotificationChannel(CHANNEL, getString(R.string.channel), NotificationManager.IMPORTANCE_LOW))
        mutable.value = WriteState(busy = true, phase = R.string.writing)
        if (Build.VERSION.SDK_INT >= 29) startForeground(1, notification(), ServiceInfo.FOREGROUND_SERVICE_TYPE_CONNECTED_DEVICE)
        else startForeground(1, notification())
        job = scope.launch {
            val wakeLock = getSystemService(PowerManager::class.java).newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "$packageName:imaging")
            try {
                wakeLock.acquire(6 * 60 * 60 * 1000L)
                val request = requireNotNull(intent)
                val target = Storage(requireNotNull(request.getStringExtra("id")), "USB", request.getIntExtra("vendor", -1), request.getIntExtra("product", -1), lun = request.getIntExtra("lun", 0))
                val file = File(filesDir, "prepared.img")
                check(Imaging.digest(file.inputStream()) == request.getStringExtra("sha256")) { "Prepared image changed" }
                ensureActive()
                UsbStorage(this@ImagingService).open(target).use { device ->
                    check(device.blocks * device.blockSize == request.getLongExtra("capacity", -1)) { "USB capacity changed; select the device again" }
                    Imaging.flash(file, device) { verify, fraction ->
                        ensureActive()
                        mutable.value = WriteState(true, if (verify) R.string.verifying else R.string.writing, fraction)
                        val now = System.currentTimeMillis()
                        if (now - lastNotification > 500) {
                            notifications.notify(1, notification())
                            lastNotification = now
                        }
                    }
                }
                mutable.value = WriteState(phase = R.string.done, progress = 1f)
            } catch (cancelled: CancellationException) {
                mutable.value = WriteState(phase = R.string.cancelled)
            } catch (error: Exception) {
                mutable.value = WriteState(error = error.message ?: error.javaClass.simpleName)
            } finally {
                if (wakeLock.isHeld) wakeLock.release()
                stopForeground(STOP_FOREGROUND_REMOVE)
                notifications.notify(2, notification())
                stopSelf()
            }
        }
        return START_NOT_STICKY
    }

    private fun notification(): Notification {
        val state = mutable.value
        val open = PendingIntent.getActivity(this, 0, Intent(this, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        val builder = Notification.Builder(this, CHANNEL).setSmallIcon(R.drawable.ic_imager)
            .setContentTitle(getString(R.string.app_name))
            .setContentText(state.error ?: if (state.phase != 0) getString(state.phase) else "")
            .setContentIntent(open).setOnlyAlertOnce(true).setOngoing(state.busy)
        if (state.busy) {
            val cancel = PendingIntent.getService(this, 1, Intent(this, ImagingService::class.java).setAction(CANCEL), PendingIntent.FLAG_IMMUTABLE)
            builder.setProgress(100, (state.progress * 100).toInt(), false)
                .addAction(Notification.Action.Builder(null, getString(R.string.cancel), cancel).build())
        }
        return builder.build()
    }

    override fun onDestroy() { scope.cancel(); super.onDestroy() }

    companion object {
        const val CANCEL = "de.jtracht.piimager.CANCEL"
        private const val CHANNEL = "imaging"
        private val mutable = MutableStateFlow(WriteState())
        val state = mutable.asStateFlow()
    }
}
