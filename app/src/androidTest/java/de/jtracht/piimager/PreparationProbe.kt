package de.jtracht.piimager

import android.app.Activity
import android.app.Instrumentation
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import de.jtracht.piimager.data.*
import de.jtracht.piimager.domain.*
import java.io.File
import java.security.MessageDigest

/** Explicit emulator integration probe. No account, network or USB writes. */
class PreparationProbe : Instrumentation() {
    override fun onCreate(arguments: Bundle?) { super.onCreate(arguments); start() }
    override fun onStart() {
        val report = Bundle()
        try {
            val request = PreparationRequest(null, Uri.parse("content://de.jtracht.piimager.test.fixture/image"), Customization(), InitFormat.NONE, null)
            var activity = open()
            runOnMainSync { PreparationService.begin(targetContext, request) }
            await("preparation started") { PreparationService.state.value.bytes > 0 }
            runOnMainSync { activity.finish() }
            await("preparation after Activity destruction", 45000) { !PreparationService.state.value.busy }
            await("completed service stopped") { serviceStopped() }
            val result = requireNotNull(PreparationService.state.value.image) { PreparationService.state.value.error ?: "Missing prepared image" }
            check(result.file.length() == 64L * 1024 * 1024)
            val hash = MessageDigest.getInstance("SHA-256")
            val chunk = ByteArray(512 * 1024) { (it % 251).toByte() }
            repeat(128) { hash.update(chunk) }
            check(result.sha256 == hash.digest().joinToString("") { "%02x".format(it) })
            activity = open()
            val label = targetContext.getString(R.string.ready, android.text.format.Formatter.formatFileSize(targetContext, result.file.length()))
            waitForIdleSync()
            var visible = false
            val display = targetContext.resources.displayMetrics
            for (attempt in 0..4) {
                if (hasText(uiAutomation.rootInActiveWindow, label)) { visible = true; break }
                uiAutomation.executeShellCommand("input swipe ${display.widthPixels / 2} ${display.heightPixels * 4 / 5} ${display.widthPixels / 2} ${display.heightPixels / 3} 350").use { fd ->
                    android.os.ParcelFileDescriptor.AutoCloseInputStream(fd).bufferedReader().use { it.readText() }
                }
                Thread.sleep(400)
            }
            if (!visible) {
                val screenshot = uiAutomation.takeScreenshot()
                File(targetContext.getExternalFilesDir(null), "probe-failure.png").outputStream().use { screenshot?.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it) }
            }
            check(visible) { "New Activity did not show '$label'; root=${uiAutomation.rootInActiveWindow}; state=${PreparationService.state.value}" }
            runOnMainSync { PreparationService.begin(targetContext, request) }
            await("second preparation started") { PreparationService.state.value.bytes > 0 }
            runOnMainSync { PreparationService.cancel(targetContext) }
            await("cancellation") { !PreparationService.state.value.busy }
            await("cancelled service stopped") { serviceStopped() }
            check(PreparationService.state.value.image == null)
            check(!File(targetContext.filesDir, "preparing.tmp").exists())
            check(!File(targetContext.filesDir, "prepared.img").exists())
            report.putString("stream", "PASS: background preparation, SHA-256, Activity recreation, cancellation and temporary-file cleanup\n")
            runOnMainSync { activity.finish() }
            finish(Activity.RESULT_OK, report)
        } catch (failure: Throwable) {
            report.putString("stream", failure.stackTraceToString())
            finish(Activity.RESULT_CANCELED, report)
        }
    }
    private fun open(): Activity = startActivitySync(Intent(targetContext, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    @Suppress("DEPRECATION")
    private fun serviceStopped() = targetContext.getSystemService(android.app.ActivityManager::class.java)
        .getRunningServices(100).none { it.service.className == PreparationService::class.java.name }
    private fun hasText(node: android.view.accessibility.AccessibilityNodeInfo?, text: String, depth: Int = 0): Boolean {
        if (node == null || depth > 32) return false
        if (node.text?.toString() == text) return true
        return (0 until node.childCount).any { hasText(node.getChild(it), text, depth + 1) }
    }
    private fun await(name: String, timeout: Long = 15000, condition: () -> Boolean) {
        val until = android.os.SystemClock.elapsedRealtime() + timeout
        while (!condition()) { check(android.os.SystemClock.elapsedRealtime() < until) { "Timed out: $name; ${PreparationService.state.value}" }; Thread.sleep(100) }
    }
}
