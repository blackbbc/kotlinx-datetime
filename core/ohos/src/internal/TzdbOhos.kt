/*
 * Copyright 2019-2024 JetBrains s.r.o. and contributors.
 * Use of this source code is governed by the Apache 2.0 License that can be found in the LICENSE.txt file.
 */
/*
 * Based on the bionic project.
 * Copyright (C) 2017 The Android Open Source Project
 */

package kotlinx.datetime.internal

private class TzdbOhos(private val rules: Map<String, Entry>) : TimeZoneDatabase {
    override fun rulesForId(id: String): TimeZoneRules =
        rules[id]?.readRules() ?: throw IllegalStateException("Unknown time zone $id")

    override fun availableTimeZoneIds(): Set<String> = rules.keys

    class Entry(val file: ByteArray, val offset: Int, val length: Int) {
        fun readRules(): TimeZoneRules = readTzFile(file.copyOfRange(offset, offset + length)).toTimeZoneRules()
    }
}

/**
 * The system time zone database, read from the tzdata container HarmonyOS ships at [TZDATA_PATH].
 *
 * It is the same container bionic uses — see
 * https://android.googlesource.com/platform/bionic/+/master/libc/tzcode/bionic.cpp — with one
 * difference: an index entry is [INDEX_ENTRY_SIZE] bytes rather than Android's 52, because the
 * trailing unused field is absent. Everything past the index is a plain TZif payload, so
 * [readTzFile] applies unchanged.
 *
 * Verified on HarmonyOS 5 (tzdata2026b): 473 entries, every one TZif-framed, the last ending exactly
 * at the header's `final_offset`. The file is world-readable and reachable from inside an
 * application sandbox, not only from a shell.
 */
internal fun TzdbOhos(): TimeZoneDatabase = TzdbOhos(readSystemTzdata())

private const val TZDATA_PATH = "/system/etc/zoneinfo/tzdata"
private const val INDEX_ENTRY_SIZE = 48
private const val INDEX_NAME_SIZE = 40
private const val HEADER_VERSION_SIZE = 12

private fun readSystemTzdata(): Map<String, TzdbOhos.Entry> = buildMap {
    // Read the file once and hand every entry a reference to that same ByteArray, as bionic does:
    // re-reading per zone would mean a 285 KB read on each lookup.
    val content = Path.fromString(TZDATA_PATH).readBytes() ?: return@buildMap
    val header = BinaryDataReader(content)
    val version = header.readNullTerminatedUtf8String(HEADER_VERSION_SIZE)
    check(version.startsWith("tzdata") && version.length < HEADER_VERSION_SIZE) {
        "Unknown tzdata version: $version"
    }
    val indexOffset = header.readInt()
    val dataOffset = header.readInt()
    check(indexOffset <= dataOffset) { "Invalid data and index offsets: $dataOffset and $indexOffset" }
    val indexSize = dataOffset - indexOffset
    check(indexSize % INDEX_ENTRY_SIZE == 0) {
        "Invalid index size: $indexSize (must be a multiple of $INDEX_ENTRY_SIZE)"
    }
    val index = BinaryDataReader(content, indexOffset)
    repeat(indexSize / INDEX_ENTRY_SIZE) {
        val name = index.readNullTerminatedUtf8String(INDEX_NAME_SIZE)
        val start = index.readInt()
        val length = index.readInt()
        put(name, TzdbOhos.Entry(content, dataOffset + start, length))
    }
}
