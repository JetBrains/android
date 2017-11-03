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

fun arraycopy(src: IntArray, srcPos: Int, dest: IntArray, destPos: Int, length: Int) {
    System.arraycopy(src, srcPos, dest, destPos, length)
}

fun arraycopy(src: Array<Any?>, srcPos: Int, dest: Array<Any?>, destPos: Int, length: Int) {
    System.arraycopy(src, srcPos, dest, destPos, length)
}

typealias DataBufferType = ByteArray
inline fun emptyDataBuffer(): DataBufferType = ByteArray(0)
inline fun String.toDataBuffer(): DataBufferType = this.toByteArray()
inline fun DataBufferType.toString(offset: Int = 0, length: Int = this.size) = String(this, offset, length)