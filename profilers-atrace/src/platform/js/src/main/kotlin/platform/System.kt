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

@file:Suppress("NOTHING_TO_INLINE")

package platform

import org.khronos.webgl.Int8Array
import org.khronos.webgl.get
import org.khronos.webgl.set

fun arraycopy(src: IntArray, srcPos: Int, dest: IntArray, destPos: Int, length: Int) {
    (0..length).forEach {
        src[srcPos + it] = dest[destPos + it]
    }
}

fun arraycopy(src: Array<Any?>, srcPos: Int, dest: Array<Any?>, destPos: Int, length: Int) {
    (0..length).forEach {
        src[srcPos + it] = dest[destPos + it]
    }
}

/*
typealias DataBufferType = String

val DataBufferType.size get() = this.length
fun DataBufferType.copyOfRange(fromIndex: Int, toIndex: Int): DataBufferType {
    return this.substring(fromIndex, toIndex)
}
fun emptyDataBuffer(): DataBufferType = String()

fun String.toDataBuffer(): DataBufferType = this

fun DataBufferType.toString(offset: Int = 0, length: Int = this.size)
        = this.substring(offset, offset + length)

typealias DataType = Char
inline fun Char.asData() = this

inline fun DataType.asDigit() = this.toShort() - '0'.toShort()
inline fun DataType.isDigit() = this.asDigit() in 0..9

*/

typealias DataBufferType = Int8Array

fun emptyDataBuffer(): DataBufferType = Int8Array(0)
@Suppress("UnsafeCastFromDynamic")
inline operator fun DataBufferType.get(index: Int): Byte = asDynamic()[index]
inline operator fun DataBufferType.set(index: Int, value: Byte): Unit { asDynamic()[index] = value; }
inline val DataBufferType.size get() = this.length
inline fun DataBufferType.copyOfRange(fromIndex: Int, toIndex: Int): DataBufferType {
    return this.subarray(fromIndex, toIndex)
}

fun String.toDataBuffer(): DataBufferType {
    var array = Int8Array(this.length)
    this.forEachIndexed { index, c -> array[index] = c.toByte() }
    return array
}

fun DataBufferType.toString(offset: Int = 0, length: Int = this.length): String {
    var str = String()
    for (i in 0 until length) {
        str += this[offset + i].toChar()
    }
    return str
}