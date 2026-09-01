/*
 * Copyright 2019-2023 JetBrains s.r.o.
 * Use of this source code is governed by the Apache 2.0 License that can be found in the LICENSE.txt file.
 */

@file:OptIn(ExperimentalForeignApi::class)

package kotlinx.datetime.internal

import kotlinx.cinterop.ByteVar
import kotlinx.cinterop.CPointer
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.alloc
import kotlinx.cinterop.allocArray
import kotlinx.cinterop.memScoped
import kotlinx.cinterop.ptr
import kotlinx.cinterop.set
import kotlinx.cinterop.toKString
import kotlinx.cinterop.value
import platform.framework.OH_TimeService_GetTimeZone
import platform.posix.localtime_r
import platform.posix.time
import platform.posix.time_tVar
import platform.posix.tm

internal actual val systemTzdb: TimeZoneDatabase get() = tzdb.getOrThrow()

private val tzdb = runCatching { TzdbOhos() }

internal actual fun currentSystemDefaultZone(): Pair<String, TimeZoneRules?> {
    val id = getCurrentTimeZone()
    if (id.isNotEmpty()) {
        val rules = tzdb.getOrNull()?.let { db -> runCatching { db.rulesForId(id) }.getOrNull() }
        if (rules != null) return id to rules
    }
    /* Either the OS named no zone, or the system tzdb could not be read or does not carry this id.
    Reporting the offset the C library resolves for *now* keeps this from throwing, at the cost of
    freezing DST at the moment of the call. That is the same trade the hard-coded 31-entry lookup
    table this replaced made, except the offset is the real one and no zone is left out: throwing
    here is what turned `TimeZone.currentSystemDefault()` into a crash on every device whose zone
    the table had not been taught. */
    return currentUtcOffsetId() to null
}

internal fun getCurrentTimeZone(bufLen: Int = 256): String = memScoped {
    val buf: CPointer<ByteVar> = allocArray(bufLen)
    // On failure the call leaves the buffer alone, so the empty result has to be pre-arranged here
    // rather than read off the return code, whose cinterop type is not stable across SDK versions.
    buf[0] = 0
    OH_TimeService_GetTimeZone(buf, bufLen.toUInt())
    buf.toKString()
}

/** The largest offset `UtcOffset` accepts; anything beyond it cannot be rendered as a zone id. */
private const val MAX_UTC_OFFSET_SECONDS = 18L * 60 * 60

/** The current system UTC offset, in the `+HH:MM` form that `TimeZone.of` accepts. */
private fun currentUtcOffsetId(): String = memScoped {
    val now = alloc<time_tVar>()
    now.value = time(null)
    val broken = alloc<tm>()
    if (localtime_r(now.ptr, broken.ptr) == null) return@memScoped "Z"
    formatUtcOffset(broken.tm_gmtoff)
}

/**
 * Takes the C `long` unnarrowed, and degrades to `"Z"` for anything `UtcOffset` would reject rather
 * than emitting, say, `+19:00` for `TimeZone.of` to throw on. This function is the branch that
 * exists so the caller cannot throw, so it must not hand back a string that makes it throw.
 */
private fun formatUtcOffset(totalSeconds: Long): String {
    if (totalSeconds == 0L || totalSeconds !in -MAX_UTC_OFFSET_SECONDS..MAX_UTC_OFFSET_SECONDS) return "Z"
    val absSeconds = (if (totalSeconds < 0) -totalSeconds else totalSeconds).toInt()
    return buildString {
        append(if (totalSeconds < 0) '-' else '+')
        appendTwoDigits(absSeconds / 3600)
        append(':')
        appendTwoDigits(absSeconds / 60 % 60)
        val seconds = absSeconds % 60
        if (seconds != 0) {
            append(':')
            appendTwoDigits(seconds)
        }
    }
}

private fun StringBuilder.appendTwoDigits(value: Int) {
    if (value < 10) append('0')
    append(value)
}
