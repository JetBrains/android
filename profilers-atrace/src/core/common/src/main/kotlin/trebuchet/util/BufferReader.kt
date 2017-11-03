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

@file:Suppress("ConvertTwoComparisonsToRangeCheck", "NOTHING_TO_INLINE")

package trebuchet.util

import platform.*
import trebuchet.io.DataSlice

inline fun Byte.isDigit() = this >= '0'.toByte() && this <= '9'.toByte()

open class BufferReaderState(var buffer: DataBufferType, var index: Int, var endIndexExclusive: Int) {
    constructor() : this(DataSlice.EmptyBuffer, 0, 0)

    inline fun peek(): Byte {
        if (index >= endIndexExclusive) {
            throw IndexOutOfBoundsException()
        }
        return buffer[index]
    }

    inline fun isDigit() = buffer[index].isDigit()

    inline fun skip() {
        index++
    }

    inline fun skip(count: Int) {
        index += count
    }

    inline fun skip(char: Char) {
        while (index < endIndexExclusive && buffer[index] == char.toByte()) { index++ }
    }

    inline fun skipUntil(cb: (Byte) -> Boolean) {
        while (index < endIndexExclusive && !cb(peek())) { index++ }
    }

    inline fun end() { index = endIndexExclusive }

    inline fun skipTo(search: StringSearch) {
        val foundAt = search.find(buffer, index, endIndexExclusive)
        index = if (foundAt != -1) foundAt else endIndexExclusive
    }
}

class PreviewReader : BufferReaderState() {
    val startIndex = index
    inline fun rewind() {
        index--
        if (index < startIndex) { throw IndexOutOfBoundsException() }
    }
    inline fun rewindUntil(cb: (Byte) -> Boolean) {
        while (!cb(peek())) { rewind() }
    }
}

class BufferReader : BufferReaderState() {
    companion object {
        val PowerOf10s = intArrayOf(1, 10, 100, 1000, 10_000, 100_000, 1_000_000)
    }

    var stringCache: StringCache? = null
    val tempSlice = DataSlice()
    val tempPreview = PreviewReader()

    fun reset(slice: DataSlice, stringCache: StringCache?) {
        this.buffer = slice.buffer
        this.index = slice.startIndex
        this.endIndexExclusive = slice.endIndex
        this.stringCache = stringCache
    }

    inline fun <T> read(slice: DataSlice, stringCache: StringCache? = null, init: BufferReader.() -> T): T {
        this.reset(slice, stringCache)
        val ret = this.init()
        this.stringCache = null
        return ret
    }

    fun readInt(): Int {
        var value: Int = 0
        var foundDigit = false
        val startIndex = index
        while (index < endIndexExclusive) {
            val c = buffer[index] - '0'.toByte()
            if (c >= 0 && c <= 9) {
                foundDigit = true
                value *= 10
                value += c
            } else if (foundDigit) {
                return value
            }
            index++
        }
        if (foundDigit) {
            return value
        }
        throw NumberFormatException(
                "${buffer.toString(startIndex, index - startIndex)} is not an int")
    }

    fun readDouble(): Double {
        skipUntil { it == '.'.toByte() || isDigit() }
        var result = readInt().toDouble()
        if (peek() == '.'.toByte()) {
            skip()
            val startI = index
            val second = readInt().toDouble()
            val magnitude = index - startI
            result += (second / PowerOf10s[magnitude])
        }
        return result
    }

    inline fun stringTo(init: PreviewReader.() -> Unit): String {
        val slice = sliceTo(tempSlice, init)
        return stringCache?.stringFor(slice) ?: slice.toString()
    }

    inline fun sliceTo(slice: DataSlice = DataSlice(), init: PreviewReader.() -> Unit): DataSlice {
        tempPreview.buffer = buffer
        tempPreview.index = index
        tempPreview.endIndexExclusive = endIndexExclusive
        tempPreview.init()
        slice.set(buffer, index, tempPreview.index)
        index = tempPreview.index
        return slice
    }
}

