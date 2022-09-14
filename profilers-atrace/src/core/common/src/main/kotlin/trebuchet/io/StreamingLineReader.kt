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

package trebuchet.io

class StreamingLineReader(private val maxLineLength: Int, val stream: StreamingReader) {
    init {
        if (maxLineLength > stream.keepLoadedSize) {
            throw IllegalArgumentException("Cannot have a maxLineLength ($maxLineLength) that's bigger than "
                    + "the StreamingReader's window size (${stream.keepLoadedSize}")
        }
    }

    private val tmpBuffer = ByteArray(maxLineLength)
    private val tmpBufferSlice = tmpBuffer.asSlice()
    private val tmpSlice = DataSlice()

    fun forEachLine(lineCallback: (DataSlice) -> Unit) {
        var lineStartIndex = stream.startIndex
        while (true) {
            var index = lineStartIndex
            var foundAt = -1
            while (true) {
                if (index > stream.endIndex) {
                    if (!stream.loadIndex(index)) break
                }
                val window = stream.windowFor(index)
                foundAt = findNewlineInWindow(window, index)
                if (foundAt != -1) break
                index = window.globalEndIndex + 1
            }
            // Reached EOF with no data, return
            if (lineStartIndex > stream.endIndex) return

            // Reached EOF, consume remaining as a line
            if (foundAt == -1) foundAt = stream.endIndex + 1

            val nextStart = foundAt + 1

            // Handle CLRF
            if (stream[foundAt - 1] == '\r'.code.toByte()) foundAt -= 1

            val lineEndIndexInclusive = foundAt - 1
            if (lineEndIndexInclusive - lineStartIndex < maxLineLength) {
                val window = stream.windowFor(lineStartIndex)
                if (window === stream.windowFor(lineEndIndexInclusive)) {
                    // slice endIndex is exclusive
                    lineCallback(window.slice.slice(lineStartIndex - window.globalStartIndex,
                            lineEndIndexInclusive - window.globalStartIndex + 1, tmpSlice))
                } else {
                    stream.copyTo(tmpBuffer, lineStartIndex, lineEndIndexInclusive)
                    tmpBufferSlice.set(tmpBuffer, 0, lineEndIndexInclusive - lineStartIndex + 1)
                    lineCallback(tmpBufferSlice)
                }
            }
            lineStartIndex = nextStart
        }
    }

    private fun findNewlineInWindow(window: StreamingReader.Window, startIndex: Int): Int {
        for (i in startIndex..window.globalEndIndex) {
            if (window[i] == '\n'.code.toByte()) return i
        }
        return -1
    }
}