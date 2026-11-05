package com.wordpop.app

import android.app.AppOpsManager
import android.app.usage.UsageEvents
import android.app.usage.UsageStatsManager
import android.content.Context
import android.content.pm.ApplicationInfo
import android.os.Process

/** Detects what the user is doing so words are not shown over games, videos or calls. */
object Focus {

    private val videoApps = setOf(
        "com.google.android.youtube",
        "com.netflix.mediaclient",
        "com.zhiliaoapp.musically",
        "com.ss.android.ugc.trill",
        "tv.twitch.android.app",
        "org.videolan.vlc",
        "com.mxtech.videoplayer.ad",
        "com.amazon.avod.thirdpartyclient",
        "com.disney.disneyplus"
    )

    /** True when the user allowed "usage access" for this app in system settings. */
    fun hasUsageAccess(ctx: Context): Boolean = try {
        val ops = ctx.getSystemService(Context.APP_OPS_SERVICE) as AppOpsManager
        @Suppress("DEPRECATION")
        val mode = ops.checkOpNoThrow(AppOpsManager.OPSTR_GET_USAGE_STATS, Process.myUid(), ctx.packageName)
        mode == AppOpsManager.MODE_ALLOWED
    } catch (e: Exception) {
        false
    }

    @Suppress("DEPRECATION")
    private fun foregroundPackage(ctx: Context): String? {
        val usm = ctx.getSystemService(Context.USAGE_STATS_SERVICE) as UsageStatsManager
        val end = System.currentTimeMillis()
        val events = usm.queryEvents(end - 4 * 3_600_000L, end)
        val e = UsageEvents.Event()
        var pkg: String? = null
        while (events.hasNextEvent()) {
            events.getNextEvent(e)
            when (e.eventType) {
                UsageEvents.Event.MOVE_TO_FOREGROUND -> pkg = e.packageName
                UsageEvents.Event.MOVE_TO_BACKGROUND -> if (e.packageName == pkg) pkg = null
            }
        }
        return pkg
    }

    @Suppress("DEPRECATION")
    fun gameOrVideoInForeground(ctx: Context): Boolean {
        if (!hasUsageAccess(ctx)) return false
        return try {
            val pkg = foregroundPackage(ctx) ?: return false
            if (pkg == ctx.packageName) return false
            if (pkg in videoApps) return true
            val info = ctx.packageManager.getApplicationInfo(pkg, 0)
            info.category == ApplicationInfo.CATEGORY_GAME ||
                info.category == ApplicationInfo.CATEGORY_VIDEO ||
                (info.flags and ApplicationInfo.FLAG_IS_GAME) != 0
        } catch (e: Exception) {
            false
        }
    }
}
