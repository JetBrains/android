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

import trebuchet.importers.ImportFeedback
import trebuchet.util.BufferReader

data class ImportData(val importer: FtraceImporterState, val feedback: ImportFeedback) {
    private var _line: FtraceLine? = null

    fun wrap(line: FtraceLine): ImportData {
        _line = line
        return this
    }

    val line: FtraceLine get() = _line!!
    val thread get() = importer.threadFor(line)

    inline fun <T> readDetails(init: BufferReader.() -> T): T {
        return line.functionDetailsReader.init()
    }
}