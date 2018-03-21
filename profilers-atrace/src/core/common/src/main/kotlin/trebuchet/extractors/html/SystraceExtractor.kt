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

package trebuchet.extractors.html

import trebuchet.extractors.Extractor
import trebuchet.extractors.ExtractorFactory
import trebuchet.importers.ImportFeedback
import trebuchet.io.*
import trebuchet.util.contains
import trebuchet.util.searchFor

class SystraceExtractor : Extractor {
    private var startIndex: Int = 0
    private var endIndex: Int = Int.MAX_VALUE

    override fun extract(stream: StreamingReader, processSubStream: (BufferProducer) -> Unit) {
        while (true) {
            stream.onWindowReleased = null
            startIndex = START.find(stream, startIndex)
            if (startIndex == -1) return
            startIndex += START.length
            if (!stream.loadIndex(startIndex)) return
            if (stream[startIndex] == '\n'.toByte()) startIndex++
            endIndex = Int.MAX_VALUE

            val pipe = Pipe<DataSlice>()
            val thread = Thread {
                stream.onWindowReleased = { window -> processWindow(window, pipe) }
                endIndex = END.find(stream, startIndex)
                if (endIndex == -1) {
                    endIndex = stream.endIndex
                }
                stream.onWindowReleased = null
                stream.windows.forEach { processWindow(it, pipe) }
                pipe.close()
            }
            thread.start()

            processSubStream(SubStream(pipe))

            thread.join()

            startIndex = endIndex
        }
    }

    private class SubStream(val pipe: Pipe<DataSlice>) : BufferProducer {
        override fun next(): DataSlice? = pipe.next()
    }

    private fun processWindow(window: StreamingReader.Window, pipe: Pipe<DataSlice>) {
        if (window.globalEndIndex >= startIndex && window.globalStartIndex < endIndex) {
            if (window.globalStartIndex >= startIndex && window.globalEndIndex <= endIndex) {
                pipe.add(window.slice)
            } else {
                val sliceStart = maxOf(startIndex - window.globalStartIndex, 0)
                val sliceEnd = minOf(endIndex, window.globalEndIndex) - window.globalStartIndex
                pipe.add(window.slice.slice(sliceStart, sliceEnd))
            }
        }
    }

    private companion object {
        val START = searchFor("""<script class="trace-data" type="application/text">""")
        val END = searchFor("""</script>""")
    }

    object Factory : ExtractorFactory {
        override fun extractorFor(buffer: GenericByteBuffer, feedback: ImportFeedback): Extractor? {
            if (buffer.contains("<html>", 1000)) {
                return SystraceExtractor()
            }
            return null
        }
    }
}