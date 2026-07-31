/*
 * Copyright (C) 2026 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.android.tools.profilers.memory.adapters

import com.google.common.truth.Truth.assertThat
import java.nio.ByteBuffer
import java.nio.ByteOrder
import org.junit.Test

class TraceProcessorMemoryUtilTest {

  @Test
  fun testReadPrimitiveValueFromBuffer() {
    verifyPrimitiveValueReading(ByteOrder.BIG_ENDIAN)
    verifyPrimitiveValueReading(ByteOrder.LITTLE_ENDIAN)
  }

  private fun verifyPrimitiveValueReading(order: ByteOrder) {
    val buffer = ByteBuffer.allocate(36).order(order)
    buffer.put(1.toByte()) // boolean true
    buffer.put(0.toByte()) // boolean false
    buffer.put((-128).toByte()) // byte -128
    buffer.putChar('A') // char 'A'
    buffer.putShort((-32768).toShort()) // short -32768
    buffer.putInt(123456789) // int 123456789
    buffer.putLong(9876543210L) // long 9876543210
    buffer.putFloat(3.14f) // float 3.14
    buffer.putDouble(2.718281828) // double 2.718281828
    buffer.putInt(9999) // unknown type fallback (4 bytes)
    buffer.flip()

    assertThat(TraceProcessorMemoryUtil.readPrimitiveValueFromBuffer("boolean", buffer)).isEqualTo("true")
    assertThat(TraceProcessorMemoryUtil.readPrimitiveValueFromBuffer("boolean", buffer)).isEqualTo("false")
    assertThat(TraceProcessorMemoryUtil.readPrimitiveValueFromBuffer("byte", buffer)).isEqualTo("-128")
    assertThat(TraceProcessorMemoryUtil.readPrimitiveValueFromBuffer("char", buffer)).isEqualTo("A")
    assertThat(TraceProcessorMemoryUtil.readPrimitiveValueFromBuffer("short", buffer)).isEqualTo("-32768")
    assertThat(TraceProcessorMemoryUtil.readPrimitiveValueFromBuffer("int", buffer)).isEqualTo("123456789")
    assertThat(TraceProcessorMemoryUtil.readPrimitiveValueFromBuffer("long", buffer)).isEqualTo("9876543210")
    assertThat(TraceProcessorMemoryUtil.readPrimitiveValueFromBuffer("float", buffer)).isEqualTo(3.14f.toString())
    assertThat(TraceProcessorMemoryUtil.readPrimitiveValueFromBuffer("double", buffer)).isEqualTo(2.718281828.toString())
    assertThat(TraceProcessorMemoryUtil.readPrimitiveValueFromBuffer("unknown_type", buffer)).isEqualTo("0")
    assertThat(buffer.hasRemaining()).isFalse()
  }

  @Test
  fun testParseRawValueEdgeCases() {
    // Boolean parsing
    assertThat(TraceProcessorMemoryUtil.parseRawValue("boolean", "1")).isEqualTo(true)
    assertThat(TraceProcessorMemoryUtil.parseRawValue("boolean", "true")).isEqualTo(true)
    assertThat(TraceProcessorMemoryUtil.parseRawValue("boolean", "0")).isEqualTo(false)
    assertThat(TraceProcessorMemoryUtil.parseRawValue("boolean", "garbage")).isEqualTo(false)

    // Byte / Short / Int / Long parsing including fallback defaults and overflow bounds
    assertThat(TraceProcessorMemoryUtil.parseRawValue("byte", "127")).isEqualTo(127.toByte())
    assertThat(TraceProcessorMemoryUtil.parseRawValue("byte", "128")).isEqualTo(0.toByte()) // overflow fallback
    assertThat(TraceProcessorMemoryUtil.parseRawValue("byte", "invalid")).isEqualTo(0.toByte())

    assertThat(TraceProcessorMemoryUtil.parseRawValue("short", "-32768")).isEqualTo((-32768).toShort())
    assertThat(TraceProcessorMemoryUtil.parseRawValue("short", "-32769")).isEqualTo(0.toShort()) // underflow fallback
    assertThat(TraceProcessorMemoryUtil.parseRawValue("short", "invalid")).isEqualTo(0.toShort())

    assertThat(TraceProcessorMemoryUtil.parseRawValue("int", "2147483647")).isEqualTo(2147483647)
    assertThat(TraceProcessorMemoryUtil.parseRawValue("int", "invalid")).isEqualTo(0)

    assertThat(TraceProcessorMemoryUtil.parseRawValue("long", "-9223372036854775808")).isEqualTo(Long.MIN_VALUE)
    assertThat(TraceProcessorMemoryUtil.parseRawValue("long", "invalid")).isEqualTo(0L)

    // Floating point parsing
    assertThat(TraceProcessorMemoryUtil.parseRawValue("float", "3.14")).isEqualTo(3.14f)
    assertThat(TraceProcessorMemoryUtil.parseRawValue("float", "not_a_float")).isEqualTo(0.0f)

    assertThat(TraceProcessorMemoryUtil.parseRawValue("double", "2.718281828459045")).isEqualTo(2.718281828459045)
    assertThat(TraceProcessorMemoryUtil.parseRawValue("double", "not_a_double")).isEqualTo(0.0)

    // Char parsing
    assertThat(TraceProcessorMemoryUtil.parseRawValue("char", "X")).isEqualTo('X')
    assertThat(TraceProcessorMemoryUtil.parseRawValue("char", "")).isEqualTo('\u0000')
    assertThat(TraceProcessorMemoryUtil.parseRawValue("char", "ABC")).isEqualTo('A')

    // Unknown types
    assertThat(TraceProcessorMemoryUtil.parseRawValue("unknown_type", "123")).isNull()
  }
}
