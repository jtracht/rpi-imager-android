package de.jtracht.piimager

import android.Manifest
import android.app.PendingIntent
import android.content.*
import android.hardware.usb.UsbManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.runtime.*
import androidx.core.content.ContextCompat
import de.jtracht.piimager.data.ImagingService
import de.jtracht.piimager.data.Storage
import de.jtracht.piimager.presentation.ImagerViewModel
import de.jtracht.piimager.ui.ImagerScreen
import de.jtracht.piimager.ui.ImagerTheme

class MainActivity : ComponentActivity() {
    private val model: ImagerViewModel by viewModels()
    private val usbAction get() = "$packageName.USB_PERMISSION"
    private val receiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            if (intent.action == usbAction) {
                // Resolve against the current device list and actual system permission.
                model.usb.devices().firstOrNull { it.id == intent.getStringExtra("selected_id") }?.let {
                    if (model.usb.manager.hasPermission(model.usb.device(it))) model.probe(it)
                    else model.error(getString(R.string.permission_denied))
                }
            } else if (intent.action == UsbManager.ACTION_USB_DEVICE_DETACHED || intent.action == UsbManager.ACTION_USB_DEVICE_ATTACHED) model.refresh()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        ContextCompat.registerReceiver(this, receiver, IntentFilter(usbAction).apply {
            addAction(UsbManager.ACTION_USB_DEVICE_DETACHED)
            addAction(UsbManager.ACTION_USB_DEVICE_ATTACHED)
        }, ContextCompat.RECEIVER_NOT_EXPORTED)
        setContent {
            val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri -> uri?.let(model::selectLocal) }
            val notifications = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { }
            ImagerTheme {
                ImagerScreen(model,
                    pickImage = { picker.launch(arrayOf("*/*")) },
                    selectTarget = ::selectTarget,
                    startWrite = { target, hash ->
                        if (Build.VERSION.SDK_INT >= 33) notifications.launch(Manifest.permission.POST_NOTIFICATIONS)
                        try {
                            startForegroundService(Intent(this, ImagingService::class.java)
                                .putExtra("id", target.id).putExtra("vendor", target.vendor).putExtra("product", target.product)
                                .putExtra("capacity", target.capacity).putExtra("sha256", hash))
                        } catch (e: Exception) { model.error(e.message ?: "Cannot start imaging service") }
                    },
                    cancelWrite = { startService(Intent(this, ImagingService::class.java).setAction(ImagingService.CANCEL)) }
                )
            }
        }
    }

    private fun selectTarget(storage: Storage) {
        try {
            val device = model.usb.device(storage)
            if (model.usb.manager.hasPermission(device)) model.probe(storage)
            else {
                val intent = Intent(usbAction).setPackage(packageName).putExtra("selected_id", storage.id)
                val permission = PendingIntent.getBroadcast(this, device.deviceId, intent, PendingIntent.FLAG_MUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
                model.usb.manager.requestPermission(device, permission)
            }
        } catch (e: Exception) { model.error(e.message ?: "USB unavailable") }
    }

    override fun onDestroy() { unregisterReceiver(receiver); super.onDestroy() }
}
