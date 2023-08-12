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
import java.util.regex.Pattern

const val FtraceLineRE = """^*(.{1,16})-(\d+) +(?:\( *(\d+)?-*\) )?\[(\d+)] (?:[dX.]...)? *([\d.]*): ?([^:]*): (.*)$"""

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

    class Parser(private val stringCache: StringCache) {
        private val NullTaskName = stringCache.stringFor("<...>".asSlice())
        private val ftraceLine = FtraceLine()
        private val _reader = BufferReader()
        private val matcher = Pattern.compile(FtraceLineRE).matcher("")

        fun parseLine_new(line: DataSlice, callback: (FtraceLine) -> Unit) =
                _reader.read(line, stringCache) {
            match(matcher) {
                ftraceLine.set(
                        string(1),
                        int(2),
                        if (matcher!!.start(3) == -1) InvalidId else int(3),
                        int(4),
                        double(5),
                        slice(6),
                        reader(7)
                )
                callback(ftraceLine)
            }
        }

        fun parseLine(line: DataSlice, callback: (FtraceLine) -> Unit) =
                _reader.read(line, stringCache) {
            var tgid: Int = InvalidId
            skipChar(' '.code.toByte())
            val taskName = stringTo {
                skipUntil { it == '-'.code.toByte() }
                skipUntil { it == '('.code.toByte() || it == '['.code.toByte() }
                rewindUntil { it == '-'.code.toByte() }
            }
            val pid = readInt()
            skipChar(' '.code.toByte())
            if (peek() == '('.code.toByte()) {
                skip()
                if (peek() != '-'.code.toByte()) {
                    tgid = readInt()
                }
                skipUntil { it == ')'.code.toByte() }
            }
            val cpu = readInt()
            skipCount(2)
            if (peek() == '.'.code.toByte() || peek() > '9'.code.toByte()) {
                skipCount(5)
            }
            skipChar(' '.code.toByte())
            val timestamp = readDouble()
            skipCount(1); skipChar(' '.code.toByte())
            val func = sliceTo(ftraceLine.function) { skipUntil { it == ':'.code.toByte() } }
            skipCount(2)
            ftraceLine.set(if (taskName === NullTaskName) null else taskName, pid, tgid, cpu,
                    timestamp, func, _reader)
            callback(ftraceLine)
        }
    }
}
