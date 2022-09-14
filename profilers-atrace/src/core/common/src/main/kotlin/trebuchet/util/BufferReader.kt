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

import trebuchet.io.DataSlice
import java.util.regex.Matcher

inline fun Byte.isDigit() = this >= '0'.code.toByte() && this <= '9'.code.toByte()

open class BufferReaderState(var buffer: ByteArray, var index: Int, var endIndexExclusive: Int) {
    constructor() : this(DataSlice.EmptyBuffer, 0, 0)

    inline fun peek() = buffer[index]

    inline fun isDigit() = buffer[index].isDigit()

    inline fun skip() {
        index++
    }

    inline fun skipCount(count: Int) {
        index += count
    }

    inline fun skipChar(char: Byte) {
        while (index < endIndexExclusive && buffer[index] == char) { index++ }
    }

    inline fun skipSingle(char: Byte) {
        if (peek() == char) skip()
    }

    inline fun skipUntil(cb: (Byte) -> Boolean) {
        while (index < endIndexExclusive && !cb(peek())) { index++ }
    }

    inline fun end() { index = endIndexExclusive }

    inline fun skipTo(search: StringSearch) {
        val foundAt = search.find(buffer, index, endIndexExclusive)
        index = if (foundAt != -1) foundAt else endIndexExclusive
    }

    inline fun readByte() = buffer[index++]
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

class MatchResult(private val reader: BufferReader) {
    var matcher: Matcher? = null
    var startIndex: Int = 0

    fun int(group: Int): Int {
        reader.index = startIndex + matcher!!.start(group)
        return reader.readInt()
    }

    fun double(group: Int): Double {
        reader.index = startIndex + matcher!!.start(group)
        return reader.readDouble()
    }

    fun long(group: Int): Long {
        reader.index = startIndex + matcher!!.start(group)
        return reader.readLong()
    }

    fun string(group: Int): String {
        reader.index = startIndex + matcher!!.start(group)
        val endAt = startIndex + matcher!!.end(group)
        return reader.stringTo { index = endAt }
    }

    fun slice(group: Int): DataSlice {
        reader.index = startIndex + matcher!!.start(group)
        val endAt = startIndex + matcher!!.end(group)
        return reader.sliceTo { index = endAt }
    }

    fun reader(group: Int): BufferReader {
        reader.index = startIndex + matcher!!.start(group)
        return reader
    }

    fun <T> read(group: Int, cb: PreviewReader.() -> T): T {
        val tempPreview = reader.tempPreview
        tempPreview.buffer = reader.buffer
        tempPreview.index = startIndex + matcher!!.start(group)
        tempPreview.endIndexExclusive = startIndex + matcher!!.end(group)
        return cb.invoke(tempPreview)
    }
}

class BufferReader : BufferReaderState() {
    companion object {
        val PowerOf10s = intArrayOf(1, 10, 100, 1000, 10_000, 100_000, 1_000_000)
    }

    var stringCache: StringCache? = null
    val tempSlice = DataSlice()
    val tempPreview = PreviewReader()
    private val tempMatchResult = MatchResult(this)

    private val charWrapper = object : CharSequence {
        override val length: Int
            get() = endIndexExclusive - index

        override fun get(index: Int): Char {
            return buffer[this@BufferReader.index + index].toChar()
        }

        override fun subSequence(startIndex: Int, endIndex: Int): CharSequence {
            //noinspection StopShip
            TODO("not implemented")
        }

    }

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
        return readLong().toInt()
    }

    fun readLong(): Long {
        var value: Long = 0
        var foundDigit = false
        val startIndex = index
        while (index < endIndexExclusive) {
            val c = buffer[index] - '0'.code.toByte()
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
                "${String(buffer, startIndex, index - startIndex)} is not an int")
    }

    fun readDouble(): Double {
        skipUntil { it == '.'.code.toByte() || isDigit() }
        var result = readInt().toDouble()
        if (peek() == '.'.code.toByte()) {
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

    fun match(matcher: Matcher, result: MatchResult.() -> Unit) {
        if (!tryMatch(matcher, result)) {
            println("RE failed on '${stringTo { end() }}'")
        }
    }
    fun tryMatch(matcher: Matcher, result: MatchResult.() -> Unit): Boolean {
        matcher.reset(charWrapper)
        if (matcher.matches()) {
            tempMatchResult.matcher = matcher
            tempMatchResult.startIndex = index
            result.invoke(tempMatchResult)
            tempMatchResult.matcher = null
            return true
        }
        return false
    }
}

