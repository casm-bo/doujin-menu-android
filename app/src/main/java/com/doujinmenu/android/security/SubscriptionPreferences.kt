package com.doujinmenu.android.security

import android.content.Context

data class SubscriptionOptions(val intervalMinutes: Int = 60, val notifications: Boolean = true, val hideWhenLocked: Boolean = true)
class SubscriptionPreferences(context: Context) {
    private val preferences = context.getSharedPreferences("subscription_options",Context.MODE_PRIVATE)
    fun load() = SubscriptionOptions(preferences.getInt("interval",60).takeIf { it in intervals } ?: 60,
        preferences.getBoolean("notifications",true),preferences.getBoolean("hide_locked",true))
    fun save(value: SubscriptionOptions) {
        require(value.intervalMinutes in intervals)
        check(preferences.edit().putInt("interval",value.intervalMinutes).putBoolean("notifications",value.notifications).putBoolean("hide_locked",value.hideWhenLocked).commit())
    }
    companion object { val intervals = listOf(15,30,60,180,360,720,1440) }
}
