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

import platform.*

class StreamingReader(val source: BufferProducer, val keepLoadedSize: Int = 8096) : GenericByteBuffer {
    val windows = mutableListOf<Window>()

    var onWindowReleased: ((Window) -> Unit)? = null
    var startIndex: Int = 0
        get private set
    var endIndex: Int = -1
        get private set
    var reachedEof: Boolean = false
        get private set

    override operator fun get(index: Int): Byte = windowFor(index)[index]
    override val length: Int
        get() = endIndex - startIndex + 1

    fun windowFor(i: Int): Window {
        for (wi in 0..windows.size-1) {
            val window = windows[wi]
            if (window.globalStartIndex <= i && window.globalEndIndex >= i) {
                return window
            }
        }
        throw IndexOutOfBoundsException("$i not in range $startIndex..$endIndex")
    }

    fun loadIndex(index: Int): Boolean {
        while (endIndex < index && !reachedEof) {
            val nextBuffer = source.next()
            if (nextBuffer == null) {
                reachedEof = true
                return false
            }
            addBuffer(nextBuffer)
        }
        return index <= endIndex
    }

    private fun addBuffer(buffer: DataSlice) {
        windows.add(Window(buffer, endIndex + 1, endIndex + buffer.length))
        endIndex += buffer.length
        if (windows.size > 2 && endIndex - windows[1].globalStartIndex > keepLoadedSize) {
            val temp = windows[0]
            windows.removeAt(0)
            startIndex = windows[0].globalStartIndex
            if (onWindowReleased != null) {
                onWindowReleased!!.invoke(temp)
            }
        }
    }

    class Window(val slice: DataSlice, val globalStartIndex: Int, val globalEndIndex: Int) {
        @Suppress("NOTHING_TO_INLINE")
        inline operator fun get(i: Int): Byte = slice[i - globalStartIndex]
    }

    fun copyTo(tmpBuffer: DataBufferType, lineStartIndex: Int, lineEndIndex: Int) {
        var srcIndex = lineStartIndex
        var dstIndex = 0
        while (srcIndex <= lineEndIndex && dstIndex < tmpBuffer.size) {
            val window = windowFor(srcIndex)
            while (srcIndex <= window.globalEndIndex && dstIndex < tmpBuffer.size) {
                tmpBuffer[dstIndex++] = window[srcIndex++]
            }
        }
    }
}