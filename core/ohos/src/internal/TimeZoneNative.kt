/*
 * Copyright 2019-2023 JetBrains s.r.o.
 * Use of this source code is governed by the Apache 2.0 License that can be found in the LICENSE.txt file.
 */

package kotlinx.datetime.internal

import kotlinx.cinterop.ByteVar
import kotlinx.cinterop.CPointer
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.allocArray
import kotlinx.cinterop.memScoped
import kotlinx.cinterop.toKString
import kotlinx.datetime.IllegalTimeZoneException
import platform.framework.OH_TimeService_GetTimeZone

internal object TimezoneOffset {
    private val TZ_TO_HOURS: Map<String, Int> = mapOf(
        "Antarctica/McMurdo" to 12,
        "America/Argentina/Buenos_Aires" to -3,
        "Australia/Sydney" to 10,
        "America/Noronha" to -2,
        "America/St_Johns" to -3,
        "Africa/Kinshasa" to 1,
        "America/Santiago" to -3,
        "Asia/Shanghai" to 8,
        "Asia/Nicosia" to 3,
        "Europe/Berlin" to 2,
        "America/Guayaquil" to -5,
        "Europe/Madrid" to 2,
        "Pacific/Pohnpei" to 11,
        "America/Godthab" to -2,
        "Asia/Jakarta" to 7,
        "Pacific/Tarawa" to 12,
        "Asia/Almaty" to 6,
        "Pacific/Majuro" to 12,
        "Asia/Ulaanbaatar" to 8,
        "America/Mexico_City" to -5,
        "Asia/Kuala_Lumpur" to 8,
        "Pacific/Auckland" to 12,
        "Pacific/Tahiti" to -10,
        "Pacific/Port_Moresby" to 10,
        "Asia/Gaza" to 3,
        "Europe/Lisbon" to 1,
        "Europe/Moscow" to 3,
        "Europe/Kiev" to 3,
        "Pacific/Wake" to 12,
        "America/New_York" to -4,
        "Asia/Tashkent" to 5,
    )

    val availableTimeZoneIds = TZ_TO_HOURS.keys

    /** 返回 "UTC+8" / "UTC-3"；若不在表中，返回 null */
    fun label(tz: String): String? {
        val hours = TZ_TO_HOURS[tz] ?: return null
        return buildString {
            append("UTC")
            if (hours >= 0) append('+')
            append(hours)
        }
    }
}

internal actual val systemTzdb: TimeZoneDatabase = TzdbOnData()

internal class TzdbOnData: TimeZoneDatabase {
    override fun rulesForId(id: String): TimeZoneRules {
        TODO()
    }

    override fun availableTimeZoneIds(): Set<String> = TimezoneOffset.availableTimeZoneIds
}

internal actual fun currentSystemDefaultZone(): Pair<String, TimeZoneRules?> {
    // according to https://www.man7.org/linux/man-pages/man5/localtime.5.html, when there is no symlink, UTC is used
//    val zonePath = currentSystemTimeZonePath ?: return "Z" to null
//    val zoneId = zonePath.splitTimeZonePath()?.second?.toString()
//        ?: throw IllegalTimeZoneException("Could not determine the timezone ID that `$zonePath` corresponds to")
//    return zoneId to null
    val tz = getCurrentTimeZone()
    val zoneId = TimezoneOffset.label(tz)
        ?: throw IllegalTimeZoneException("Could not determine the timezone ID that `$tz` corresponds to")
    return zoneId to null
}

@OptIn(ExperimentalForeignApi::class)
internal fun getCurrentTimeZone(bufLen: Int = 256): String {
    return memScoped {
        val buf: CPointer<ByteVar> = allocArray(bufLen)
        OH_TimeService_GetTimeZone(buf, bufLen.toUInt())
        val tz = buf.toKString()
        tz
    }
}
