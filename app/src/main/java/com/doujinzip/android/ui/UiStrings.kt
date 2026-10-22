package com.doujinzip.android.ui

import android.content.res.Resources
import androidx.annotation.StringRes
import com.doujinzip.android.R
import com.doujinzip.android.model.HistoryAction
import com.doujinzip.android.model.HistorySource
import com.doujinzip.android.security.LockMethod

// Capture the Activity's configured resources in composition; callbacks use the same locale.
internal operator fun Resources.get(@StringRes id: Int, vararg arguments: Any?): String =
    if (arguments.isEmpty()) getString(id) else getString(id, *arguments)

internal val LockMethod.labelResource: Int get() = when (this) {
    LockMethod.PIN -> R.string.lock_pin
    LockMethod.PASSWORD -> R.string.lock_password
    LockMethod.PATTERN -> R.string.lock_pattern
}

internal val HistorySource.labelResource: Int get() = when (this) {
    HistorySource.SEARCH -> R.string.history_search
    HistorySource.DEVICE -> R.string.ui_device_d79c45
    HistorySource.PC -> R.string.history_pc
}

internal val HistoryAction.labelResource: Int get() = when (this) {
    HistoryAction.DETAIL -> R.string.history_detail
    HistoryAction.READING -> R.string.history_reading
    HistoryAction.COMPLETED -> R.string.ui_read
}
