package com.edgegesture.evilgodxu.data

import android.content.Context
import android.os.Build
import android.util.Log
import java.io.File
import java.io.PrintWriter
import java.io.StringWriter
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object CrashHandler {
    private const val TAG = "CrashHandler"
    private const val CRASH_FILE_NAME = "crash_log.txt"
    private var defaultHandler: Thread.UncaughtExceptionHandler? = null

    fun init(context: Context) {
        val appContext = context.applicationContext
        defaultHandler = Thread.getDefaultUncaughtExceptionHandler()

        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            try {
                saveCrashLog(appContext, thread, throwable)
            } catch (e: Exception) {
                Log.e(TAG, "Lỗi khi lưu crash log: ${e.message}")
            } finally {
                defaultHandler?.uncaughtException(thread, throwable)
            }
        }
    }

    private fun saveCrashLog(context: Context, thread: Thread, throwable: Throwable) {
        val sw = StringWriter()
        val pw = PrintWriter(sw)
        throwable.printStackTrace(pw)
        val stackTrace = sw.toString()

        val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
        val dateStr = dateFormat.format(Date())

        @Suppress("DEPRECATION")
        val threadId = if (Build.VERSION.SDK_INT >= 34) thread.threadId() else thread.id
        val logContent = buildString {
            append("========== CRASH DETECTED ==========\n")
            append("Thời gian: $dateStr\n")
            append("Thiết bị: ${Build.MANUFACTURER} ${Build.MODEL} (Android ${Build.VERSION.RELEASE}, API ${Build.VERSION.SDK_INT})\n")
            append("Luồng (Thread): ${thread.name} (id=$threadId)\n")
            append("Ngoại lệ: ${throwable.javaClass.name}: ${throwable.message}\n")
            append("---------------- StackTrace ----------------\n")
            append(stackTrace)
            append("=====================================\n\n")
        }

        val file = File(context.filesDir, CRASH_FILE_NAME)
        file.writeText(logContent)
        Log.e(TAG, "Đã ghi nhận crash vào: ${file.absolutePath}")
    }

    fun getLatestCrashLog(context: Context): String? {
        val file = File(context.filesDir, CRASH_FILE_NAME)
        return if (file.exists() && file.length() > 0) {
            try {
                file.readText()
            } catch (_: Exception) {
                null
            }
        } else {
            null
        }
    }

    fun clearCrashLog(context: Context): Boolean {
        val file = File(context.filesDir, CRASH_FILE_NAME)
        return if (file.exists()) {
            file.delete()
        } else {
            true
        }
    }
}
