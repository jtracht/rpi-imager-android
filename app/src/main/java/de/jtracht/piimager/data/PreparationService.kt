package de.jtracht.piimager.data

import android.app.*
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.net.Uri
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import de.jtracht.piimager.MainActivity
import de.jtracht.piimager.R
import de.jtracht.piimager.domain.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.UUID

/** Session-only request. Secrets are never placed in an Intent, Bundle or persistent job. */
data class PreparationRequest(val entry: OsEntry?, val uri: Uri?, val settings: Customization,
                              val format: InitFormat, val model: PiModel?) {
    fun validate() {
        require(entry != null || uri != null) { "Choose an image" }
        settings.validate(entry?.let { InitFormat.fromId(it.initFormat) } ?: format)
        entry?.validateCustomization(settings)
        require(entry == null || CatalogSelection.supports(entry, model)) { "Image does not match the selected model" }
    }
}

data class PreparationState(val busy: Boolean = false, val bytes: Long = 0,
                            val image: PreparedImage? = null, val error: String? = null)

class PreparationService : Service() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var job: Job? = null
    @Volatile private var timedOut = false
    private var lastNotification = 0L
    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == CANCEL) {
            job?.cancel()
            if (job == null) { synchronized(lock) { pending = null; mutable.value = PreparationState() }; stopSelf() }
            return START_NOT_STICKY
        }
        if (job?.isActive == true) return START_NOT_STICKY
        val request = synchronized(lock) {
            pending?.takeIf { it.first == intent?.getStringExtra("request") }?.second.also { pending = null }
        }
        if (request == null) {
            if (mutable.value.busy) mutable.value = PreparationState(error = getString(R.string.preparation_interrupted))
            stopSelf()
            return START_NOT_STICKY
        }
        val notifications = getSystemService(NotificationManager::class.java)
        notifications.createNotificationChannel(NotificationChannel(CHANNEL, getString(R.string.preparation_channel), NotificationManager.IMPORTANCE_LOW))
        try {
            if (Build.VERSION.SDK_INT >= 29) startForeground(10, notification(), ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
            else startForeground(10, notification())
        } catch (e: Exception) {
            mutable.value = PreparationState(error = e.message ?: getString(R.string.preparation_interrupted))
            stopSelf()
            return START_NOT_STICKY
        }
        job = scope.launch {
            val wakeLock = getSystemService(PowerManager::class.java).newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "$packageName:preparation")
            var result = PreparationState()
            try {
                wakeLock.acquire(6 * 60 * 60 * 1000L)
                val image = ImageRepository(this@PreparationService).prepare(request.entry, request.uri, request.settings, request.format, request.model) { bytes ->
                    ensureActive()
                    val now = android.os.SystemClock.elapsedRealtime()
                    if (now - lastNotification >= 500) {
                        mutable.value = PreparationState(busy = true, bytes = bytes)
                        notifications.notify(10, notification())
                        lastNotification = now
                    }
                }
                ensureActive()
                result = PreparationState(bytes = image.file.length(), image = image)
            } catch (_: CancellationException) {
                result = PreparationState(error = if (timedOut) getString(R.string.preparation_timeout) else null)
            } catch (e: Exception) {
                result = PreparationState(error = e.message ?: getString(R.string.preparation_interrupted))
            } finally {
                withContext(NonCancellable + Dispatchers.Main.immediate) {
                    if (wakeLock.isHeld) wakeLock.release()
                    stopForeground(STOP_FOREGROUND_REMOVE)
                    // CANCEL is a newer start command; stop the single operation's service too.
                    stopSelf()
                    job = null
                    mutable.value = result
                    notifications.notify(11, notification(result))
                }
            }
        }
        return START_NOT_STICKY
    }

    override fun onTimeout(startId: Int, fgsType: Int) {
        timedOut = true
        job?.cancel()
        // Android requires the service to stop promptly, even if a blocked read is unwinding.
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf(startId)
    }

    private fun notification(progress: PreparationState = mutable.value): Notification {
        val open = PendingIntent.getActivity(this, 0, Intent(this, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        val text = when {
            progress.error != null -> progress.error
            progress.busy -> getString(R.string.preparation_notification, android.text.format.Formatter.formatFileSize(this, progress.bytes))
            progress.image != null -> getString(R.string.preparation_ready)
            else -> getString(R.string.preparation_cancelled)
        }
        val builder = Notification.Builder(this, CHANNEL).setSmallIcon(R.drawable.ic_imager)
            .setContentTitle(getString(R.string.app_name)).setContentText(text).setContentIntent(open)
            .setOnlyAlertOnce(true).setOngoing(progress.busy)
        if (progress.busy) {
            val cancel = PendingIntent.getService(this, 10, Intent(this, PreparationService::class.java).setAction(CANCEL), PendingIntent.FLAG_IMMUTABLE)
            builder.setProgress(0, 0, true).addAction(Notification.Action.Builder(null, getString(R.string.cancel), cancel).build())
        }
        return builder.build()
    }

    override fun onDestroy() { scope.cancel(); super.onDestroy() }

    companion object {
        const val CANCEL = "de.jtracht.piimager.CANCEL_PREPARATION"
        private const val CHANNEL = "preparation"
        private val lock = Any()
        private var pending: Pair<String, PreparationRequest>? = null
        private val mutable = MutableStateFlow(PreparationState())
        val state = mutable.asStateFlow()

        fun begin(context: Context, request: PreparationRequest) = synchronized(lock) {
            check(!mutable.value.busy && !ImagingService.state.value.busy) { "Another image operation is running" }
            request.validate()
            val id = UUID.randomUUID().toString()
            pending = id to request
            mutable.value = PreparationState(busy = true)
            val start = Intent(context, PreparationService::class.java).putExtra("request", id)
            // Extend the selected document's temporary read grant to the service lifetime.
            // No persistable grant is taken; account/network credentials stay in RAM.
            if (request.uri != null) start.setData(request.uri).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            try { context.startForegroundService(start) }
            catch (e: Exception) { pending = null; mutable.value = PreparationState(); throw e }
        }
        fun cancel(context: Context) { if (mutable.value.busy) context.startService(Intent(context, PreparationService::class.java).setAction(CANCEL)) }
        fun clearResult() = synchronized(lock) { check(!mutable.value.busy); mutable.value = PreparationState() }
        fun dismissError() = synchronized(lock) { mutable.value = mutable.value.copy(error = null) }
    }
}
