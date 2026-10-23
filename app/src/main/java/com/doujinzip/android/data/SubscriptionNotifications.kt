package com.doujinzip.android.data

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import com.doujinzip.android.MainActivity
import com.doujinzip.android.R
import com.doujinzip.android.security.AppLockStore
import com.doujinzip.android.security.SecureProfileStore
import com.doujinzip.android.security.SubscriptionPreferences

internal fun hideSubscriptionContent(lockEnabled:Boolean,unlocked:Boolean,hideWhenLocked:Boolean):Boolean = lockEnabled && !unlocked && hideWhenLocked

object SubscriptionNotifications {
    internal const val ENTRY_ACTIVITY="com.doujinzip.android.SubscriptionNotificationEntry"
    private const val CHANNEL="subscription-releases"
    private const val PREFIX="subscription/"
    private const val ID=202
    private var unlocked=false
    private val guard=Any()

    fun session(context:Context,isUnlocked:Boolean) = synchronized(guard) {
        unlocked=isUnlocked
        val manager=context.getSystemService(NotificationManager::class.java)
        try {
            if(!isUnlocked && AppLockStore(context).enabled && SubscriptionPreferences(context).load().hideWhenLocked) {
                val generic=androidx.core.content.ContextCompat.getContextForLanguage(context).getString(R.string.sub_generic)
                manager.activeNotifications.filter { it.tag?.startsWith(PREFIX)==true }.forEach { active ->
                    val masked=Notification.Builder(context,CHANNEL).setSmallIcon(R.drawable.ic_subscription).setContentTitle(generic)
                        .setContentIntent(active.notification.contentIntent).setGroup(CHANNEL)
                        .setGroupSummary(active.notification.flags and Notification.FLAG_GROUP_SUMMARY != 0)
                        .setOnlyAlertOnce(true).setAutoCancel(true).setVisibility(Notification.VISIBILITY_PRIVATE).build()
                    manager.notify(active.tag,active.id,masked)
                }
            } else refresh(context,allowNew=false)
        } catch(_:Exception) {
            // A failed redraw must not leave sensitive content visible after locking.
            manager.activeNotifications.filter { it.tag?.startsWith(PREFIX)==true }.forEach { manager.cancel(it.tag,it.id) }
        }
    }
    @android.annotation.SuppressLint("MissingPermission")
    fun refresh(context:Context,allowNew:Boolean=true):Unit = synchronized(guard) {
        try {
        val manager=context.getSystemService(NotificationManager::class.java)
        val strings=androidx.core.content.ContextCompat.getContextForLanguage(context).resources
        manager.createNotificationChannel(NotificationChannel(CHANNEL,strings.getString(R.string.sub_notifications),NotificationManager.IMPORTANCE_DEFAULT))
        val active=manager.activeNotifications.filter { it.tag?.startsWith(PREFIX)==true }
        val options=SubscriptionPreferences(context).load()
        if(!options.notifications || !manager.areNotificationsEnabled() || (Build.VERSION.SDK_INT>=33 && context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)!=PackageManager.PERMISSION_GRANTED)) {
            active.forEach { manager.cancel(it.tag,it.id) }
            return@synchronized
        }
        val store=SubscriptionStore.get(context)
        val scopes=listOf("")+SecureProfileStore(context).load().map { it.id }
        val unread=scopes.flatMap { scope -> store.notices(scope).filterNot { it.read }.map { scope to it } }.sortedByDescending { it.second.galleryId }
        val keys=unread.map { (scope,notice)->tag(scope,notice.galleryId) }.toSet()
        active.filter { it.tag != PREFIX+"summary" && it.tag !in keys }.forEach { manager.cancel(it.tag,it.id) }
        val visible=active.mapNotNull { it.tag }.toSet()
        val pending=if(allowNew) unread.filter { (scope,n)->store.state("notified/$scope/${n.galleryId}") == null } else emptyList()
        val pendingKeys=pending.map { tag(it.first,it.second.galleryId) }.toSet()
        val hidden=hideSubscriptionContent(AppLockStore(context).enabled,unlocked,options.hideWhenLocked)
        // Android limits active notifications; the complete history stays in the app.
        val children=unread.filter { (scope,n)->tag(scope,n.galleryId) in visible || tag(scope,n.galleryId) in pendingKeys }.take(20)
        val childKeys=children.map { tag(it.first,it.second.galleryId) }.toSet()
        active.filter { it.tag!=PREFIX+"summary" && it.tag !in childKeys }.forEach { manager.cancel(it.tag,it.id) }
        children.forEach { (scope,notice) -> manager.notify(tag(scope,notice.galleryId),ID,build(context,scope,notice,hidden)) }
        if(children.isEmpty()) manager.cancel(PREFIX+"summary",ID)
        else {
            val summary=Notification.Builder(context,CHANNEL).setSmallIcon(R.drawable.ic_subscription)
                .setContentTitle(strings.getString(R.string.sub_generic)).setGroup(CHANNEL).setGroupSummary(true)
                .setGroupAlertBehavior(Notification.GROUP_ALERT_SUMMARY).setOnlyAlertOnce(pending.isEmpty())
                .setContentIntent(target(context,"",0)).setAutoCancel(true).setVisibility(Notification.VISIBILITY_PRIVATE).build()
            manager.notify(PREFIX+"summary",ID,summary)
        }
        pending.forEach { (scope,notice)->store.setState("notified/$scope/${notice.galleryId}","1") }
        } catch(_:SecurityException) {
            val manager=context.getSystemService(NotificationManager::class.java)
            manager.activeNotifications.filter { it.tag?.startsWith(PREFIX)==true }.forEach { manager.cancel(it.tag,it.id) }
        }
    }
    private fun tag(scope:String,id:Long)="$PREFIX$scope/$id"
    internal fun build(context:Context,scope:String,notice:SubscriptionNotice,hidden:Boolean):Notification {
        val strings=androidx.core.content.ContextCompat.getContextForLanguage(context).resources
        val generic=strings.getString(R.string.sub_generic)
        val public=Notification.Builder(context,CHANNEL).setSmallIcon(R.drawable.ic_subscription).setContentTitle(generic).build()
        return Notification.Builder(context,CHANNEL).setSmallIcon(R.drawable.ic_subscription)
            .setContentTitle(if(hidden) generic else notice.title).setContentText(if(hidden) null else notice.artist)
            .setContentIntent(target(context,scope,notice.galleryId)).setAutoCancel(true).setOnlyAlertOnce(true)
            .setGroup(CHANNEL).setGroupAlertBehavior(Notification.GROUP_ALERT_SUMMARY)
            .setVisibility(Notification.VISIBILITY_PRIVATE).setPublicVersion(public).build()
    }
    internal fun target(context:Context,scope:String,id:Long):PendingIntent {
        val intent=Intent(context,MainActivity::class.java).setData(Uri.Builder().scheme("doujinzip").authority("subscription").appendPath(scope).appendPath(id.toString()).build())
            .setComponent(android.content.ComponentName(context.packageName,ENTRY_ACTIVITY))
            .putExtra("subscription_scope",scope).putExtra("subscription_gallery",id)
            .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
        return PendingIntent.getActivity(context,0,intent,PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
    }
}
