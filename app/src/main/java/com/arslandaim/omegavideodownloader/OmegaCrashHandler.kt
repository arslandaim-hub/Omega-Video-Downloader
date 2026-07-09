/*
 * Omega Video Downloader Project Original (2026)
 * arslandaim-hub (GitHub.com/arslandaim-hub)
 * Licenced Under GPL-3.0+
*/
package com.arslandaim.omegavideodownloader

import android.content.Context
import android.os.Build
import java.io.File
import java.io.PrintWriter
import java.io.StringWriter
import java.util.Date
import kotlin.system.exitProcess

class OmegaCrashHandler(private val context: Context) : Thread.UncaughtExceptionHandler {
    
    private val defaultHandler = Thread.getDefaultUncaughtExceptionHandler()

    companion object {
        const val CRASH_FILE_NAME = "crash_report.txt"
        
        fun initialize(context: Context) {
            Thread.setDefaultUncaughtExceptionHandler(OmegaCrashHandler(context))
        }

        fun getCrashReport(context: Context): String? {
            val file = File(context.cacheDir, CRASH_FILE_NAME)
            return if (file.exists()) file.readText() else null
        }

        fun clearCrashReport(context: Context) {
            File(context.cacheDir, CRASH_FILE_NAME).delete()
        }
    }

    override fun uncaughtException(thread: Thread, throwable: Throwable) {
        try {
            val report = buildString {
                appendLine("--- OMEGA CRASH REPORT ---")
                appendLine("Date: ${Date()}")
                appendLine("Device: ${Build.MANUFACTURER} ${Build.MODEL}")
                appendLine("Android Version: ${Build.VERSION.RELEASE}")
                appendLine("API Level: ${Build.VERSION.SDK_INT}")
                appendLine("--- STACK TRACE ---")
                
                val sw = StringWriter()
                throwable.printStackTrace(PrintWriter(sw))
                appendLine(sw.toString())
            }

            File(context.cacheDir, CRASH_FILE_NAME).writeText(report)
        } catch (e: Exception) {
            // Fallback to avoid infinite crash loop during error handling
        } finally {
            // Pass to default handler or force exit
            if (defaultHandler != null) {
                defaultHandler.uncaughtException(thread, throwable)
            } else {
                exitProcess(1)
            }
        }
    }
}
