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

package trebuchet.util

import trebuchet.io.GenericByteBuffer
import trebuchet.io.StreamingReader

/**
 * Partial implementation of the Boyer-Moore string search algorithm.
 * Requires that the bytearray to look for is <= 127 in length due to the use of
 * signed bytes for the jump table.
 */
class StringSearch(val lookFor: String) {
    val skipLut = ByteArray(256) { lookFor.length.toByte() }
    val suffixSkip = ByteArray(lookFor.length)

    private companion object {
        fun isPrefix(str: CharSequence, pos: Int): Boolean {
            return (0 until (str.length - pos)).none { str[it] != str[pos + it] }
        }

        fun longestCommonSuffix(word: CharSequence, pos: Int): Int {
            var i: Int = 0
            while (word[pos - i] == word[word.length - 1 - i] && i < pos) {
                i++
            }
            return i
        }
    }

    init {
        val last = lookFor.length - 1
        for (i in 0..last - 1) {
            skipLut[lookFor[i].code and 0xFF] = (last - i).toByte()
        }

        var lastPrefix = last
        for (i in last downTo 0) {
            if (isPrefix(lookFor, i + 1)) {
                lastPrefix = i + 1
            }
            suffixSkip[i] = (lastPrefix + last - i).toByte()
        }
        for (i in 0 until last) {
            val suffixLength = longestCommonSuffix(lookFor, i)
            if(lookFor[i - suffixLength] != lookFor[last - suffixLength]) {
                suffixSkip[last - suffixLength] = (suffixLength + last - i).toByte()
            }
        }
    }

    val length get() = lookFor.length

    fun find(reader: StreamingReader, startIndex: Int = 0, inEndIndex: Int = Int.MAX_VALUE): Int {
        var index = startIndex + lookFor.length - 1
        var endIndex = inEndIndex
        while (index <= endIndex) {
            if (index > reader.endIndex) {
                if (!reader.loadIndex(index)) return -1
                if (reader.reachedEof) {
                    endIndex = reader.endIndex
                }
            }
            // Search the overlapping region slowly
            while (reader.windowFor(index) !== reader.windowFor(index - lookFor.length + 1)) {
                var lookForIndex = lookFor.length - 1
                while (lookForIndex >= 0 && reader[index] == lookFor[lookForIndex].code.toByte()) {
                    index--
                    lookForIndex--
                }
                if (lookForIndex < 0) {
                    return index + 1
                }
                index += maxOf(skipLut[reader[index].toInt() and 0xFF], suffixSkip[lookForIndex])

                if (index > reader.endIndex) {
                    if (!reader.loadIndex(index)) return -1
                    if (reader.reachedEof) {
                        endIndex = reader.endIndex
                    }
                }
            }
            // Now search the non-overlapping quickly
            val window = reader.windowFor(index)
            index = findInWindow(window, index, endIndex)
            if (index <= window.globalEndIndex) {
                // Found a match
                return index
            }
        }
        return -1
    }

    fun findInLoadedRegion(reader: StreamingReader, endIndex: Int = reader.endIndex): Int {
        var index = reader.startIndex + lookFor.length - 1
        while (index <= endIndex) {
            // Search the overlapping region slowly
            while (reader.windowFor(index) !== reader.windowFor(index - lookFor.length + 1)) {
                var lookForIndex = lookFor.length - 1
                while (lookForIndex >= 0 && reader[index] == lookFor[lookForIndex].code.toByte()) {
                    index--
                    lookForIndex--
                }
                if (lookForIndex < 0) {
                    return index + 1
                }
                index += maxOf(skipLut[reader[index].toInt() and 0xFF], suffixSkip[lookForIndex])
            }
            // Now search the non-overlapping quickly
            val window = reader.windowFor(index)
            index = findInWindow(window, index, endIndex)
            if (index < window.globalEndIndex && index < endIndex) {
                // Found a match
                return index
            }
        }
        return -1
    }

    fun find(buffer: GenericByteBuffer, startIndex: Int = 0, endIndex: Int = buffer.length): Int {
        var index = startIndex + lookFor.length - 1
        while (index < endIndex) {
            var lookForIndex = lookFor.length - 1
            while (lookForIndex >= 0 && buffer[index] == lookFor[lookForIndex].code.toByte()) {
                index--
                lookForIndex--
            }
            if (lookForIndex < 0) {
                index += 1
                break
            }
            index += maxOf(skipLut[buffer[index].toInt() and 0xFF], suffixSkip[lookForIndex])
        }
        return index
    }

    fun find(buffer: ByteArray, startIndex: Int = 0, endIndex: Int = buffer.size): Int {
        var index = startIndex + lookFor.length - 1
        while (index < endIndex) {
            var lookForIndex = lookFor.length - 1
            while (lookForIndex >= 0 && buffer[index] == lookFor[lookForIndex].code.toByte()) {
                index--
                lookForIndex--
            }
            if (lookForIndex < 0) {
                index += 1
                break
            }
            index += maxOf(skipLut[buffer[index].toInt() and 0xFF], suffixSkip[lookForIndex])
        }
        return index
    }

    private fun findInWindow(window: StreamingReader.Window, globalStartIndex: Int, globalEndIndex: Int): Int {
        var index = globalStartIndex - window.globalStartIndex
        val buffer = window.slice
        val endIndex = minOf(window.globalEndIndex, globalEndIndex) - window.globalStartIndex
        while (index <= endIndex) {
            var lookForIndex = lookFor.length - 1
            while (lookForIndex >= 0 && buffer[index] == lookFor[lookForIndex].code.toByte()) {
                index--
                lookForIndex--
            }
            if (lookForIndex < 0) {
                index += 1
                break
            }
            index += maxOf(skipLut[buffer[index].toInt() and 0xFF], suffixSkip[lookForIndex])
        }
        return index + window.globalStartIndex
    }
}

fun searchFor(str: String) = StringSearch(str)

fun GenericByteBuffer.contains(str: String, endIndex: Int = this.length): Boolean {
    val stopAt = minOf(endIndex, this.length - 1)
    if (this is StreamingReader) {
        return StringSearch(str).findInLoadedRegion(this, stopAt) != -1
    }
    return StringSearch(str).find(this, 0, stopAt) != -1
}