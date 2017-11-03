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

import trebuchet.io.DataSlice
import trebuchet.io.asSlice
import trebuchet.model.InvalidId
import trebuchet.util.BufferReader
import trebuchet.util.StringCache

class FtraceLine private constructor() {
    private var _task: String? = null
    private var _pid: Int = 0
    private var _tgid: Int = 0
    private var _cpu: Int = 0
    private var _timestamp: Double = 0.0
    private var _function: DataSlice = DataSlice()
    private var _functionDetails: BufferReader? = null

    val hasTgid: Boolean get() = _tgid != InvalidId
    var tgid: Int
        get() = _tgid
        set(value) {
            if (hasTgid && _tgid != value) {
                throw IllegalStateException("tgid fight, currently $_tgid but trying to set $value")
            }
            _tgid = value
        }
    val task get() = _task
    val pid get() = _pid
    val cpu get() = _cpu
    val timestamp get() = _timestamp
    val function get() = _function
    val functionDetailsReader get() = _functionDetails!!

    private fun set(taskName: String?, pid: Int, tgid: Int, cpu: Int, timestamp: Double,
                    func: DataSlice, funcDetails: BufferReader) {
        _task = taskName
        _pid = pid
        _tgid = tgid
        _cpu = cpu
        _timestamp = timestamp
        _function = func
        _functionDetails = funcDetails
    }

    class Parser(val stringCache: StringCache) {
        private val NullTaskName = stringCache.stringFor("<...>".asSlice())
        private val ftraceLine = FtraceLine()
        private val _reader = BufferReader()

        fun parseLine(line: DataSlice, callback: (FtraceLine) -> Unit) =
                _reader.read(line, stringCache) {
            var tgid: Int = InvalidId
            skip(' ')
            val taskName = stringTo {
                skipUntil { it == '-'.toByte() }
                skipUntil { it == '('.toByte() || it == '['.toByte() }
                rewindUntil { it == '-'.toByte() }
            }
            val pid = readInt()
            skip(' ')
            if (peek() == '('.toByte()) {
                skip()
                if (peek() != '-'.toByte()) {
                    tgid = readInt()
                }
                skipUntil { it == ')'.toByte() }
            }
            val cpu = readInt()
            skip(2)
            if (peek() == '.'.toByte() || peek() > '9'.toByte()) {
                skip(5)
            }
            skip(' ')
            val timestamp = readDouble()
            skip(1); skip(' ')
            val func = sliceTo(ftraceLine.function) { skipUntil { it == ':'.toByte() } }
            skip(2)
            ftraceLine.set(if (taskName === NullTaskName) null else taskName, pid, tgid, cpu,
                    timestamp, func, _reader)
            callback(ftraceLine)
        }
    }
}
