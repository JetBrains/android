/*
 * Copyright 2017 Google Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package trebuchet.importers.ftrace

import trebuchet.util.BufferReader

object TracingMarkerWrite : FunctionHandlerRegistry() {
    const val Begin = 'B'.toByte()
    const val End = 'E'.toByte()
    const val Counter = 'C'.toByte()

    init {
        "tracing_mark_write" handleWith this::handle
    }

    fun handle(data: ImportData) = data.readDetails {
        when (peek()) {
            Begin -> handleBegin(data)
            End -> handleEnd(data)
            Counter -> handleCounter(data)
            else -> handleClockSyncMarker(data)
        }
    }


    // tracing_mark_write: trace_event_clock_sync: parent_ts=23816.083984
    val parentTsMatcher = matcher(
            "trace_event_clock_sync: parent_ts=(.*)")


    // tracing_mark_write: trace_event_clock_sync: realtime_ts=1491850748338
    val realtimeTsMatcher = matcher(
            "trace_event_clock_sync: realtime_ts=(.*)")


    fun BufferReader.handleClockSyncMarker(data: ImportData) {
        // First check if the line we are importing is the parent timestamp line.
        tryMatch(parentTsMatcher) {
            val timestamp = double(1)
            data.importer.modelFragment.parentTimestamp = timestamp
        }

        // Test if the line we are testing has the realtime timestamp.
        tryMatch(realtimeTsMatcher) {
            val timestamp = long(1)
            data.importer.modelFragment.realtimeTimestamp = timestamp
        }
    }

    fun BufferReader.handleBegin(data: ImportData) {
        // Begin format: B|<tgid>|<title>
        skipCount(2)
        data.line.tgid = readInt()
        skip()
        val thread = data.importer.threadFor(data.line)
        val name = stringTo { end() }
        thread.slicesBuilder.beginSlice {
            it.startTime = data.line.timestamp
            it.name = name
        }
    }

    fun handleEnd(data: ImportData) {
        // End format: E
        val slices = data.thread.slicesBuilder
        slices.endSlice { it.endTime = data.line.timestamp }
    }

    fun BufferReader.handleCounter(data: ImportData) {
        // Counter format: C|<tgid>|<name>|<value>
        skipCount(2)
        val tgid = readInt()
        skip()
        val name = stringTo { skipUntil { it == '|'.toByte() } }
        skip()
        val value = readInt()
        data.line.tgid = tgid
        data.importer.threadFor(data.line).process.addCounterSample(name, data.line.timestamp, value)
    }
}