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
import trebuchet.importers.Importer
import trebuchet.importers.ImporterFactory
import trebuchet.io.DataSlice
import trebuchet.io.GenericByteBuffer
import trebuchet.io.StreamingLineReader
import trebuchet.io.StreamingReader
import trebuchet.model.fragments.ModelFragment
import trebuchet.util.contains
import java.util.regex.Pattern

class FtraceImporter(private val feedback: ImportFeedback) : Importer {
    private var foundHeader = false
    var state = FtraceImporterState(feedback)
    val parser = FtraceLine.Parser(state.stringCache)

    // Create captured lambads here to avoid extra kotlin-generated overhead
    private val lineReaderCallback: (DataSlice) -> Unit = this::handleLine
    private var ftraceParserCallback: (FtraceLine) -> Unit = state::importLine
    private val coreStartedRegex = Pattern.compile("^#+ CPU \\d buffer started #+")

    override fun import(stream: StreamingReader): ModelFragment {
        val lineReader = StreamingLineReader(1024, stream)
        foundHeader = false
        lineReader.forEachLine(lineReaderCallback)
        return state.finish()
    }

    private fun handleLine(line: DataSlice) {
        // This should never happen. However due to the dereference below we guard against it so we don't throw out of bounds exceptions.
        if (line.buffer.size < 2) {
            return
        }
        // The format of the line buffer should be either a series of comments, or a tracer line. Null and empty are handled
        // at a higher level.
        if (line[1] == '#'.code.toByte() && coreStartedRegex.matcher(line.toString()).matches()) {
            // Fix inconsistencies in traces due to circular buffering.
            //
            //  The circular buffers are kept per CPU, so it is not guaranteed that the
            //  beginning of a slice is overwritten before the end. To work around this, we
            //  throw away the prefix of the trace where not all CPUs have events yet.
            state = FtraceImporterState(feedback)
            ftraceParserCallback = state::importLine
        }
        else if (line[0] == '#'.code.toByte()) {
            foundHeader = true
        } else if (foundHeader) {
            try {
                parser.parseLine(line, ftraceParserCallback)
            } catch (ex: Exception) {
                if (line.toString().isNotBlank()) {
                    feedback.reportImportWarning("Failed to parse: '$line'")
                    feedback.reportImportException(ex)
                }
            }
        }
    }

    object Factory : ImporterFactory {
        override fun importerFor(buffer: GenericByteBuffer, feedback: ImportFeedback): Importer? {
            if (buffer.contains("# tracer: nop\n", 1000)) {
                return FtraceImporter(feedback)
            }
            return null
        }
    }
}