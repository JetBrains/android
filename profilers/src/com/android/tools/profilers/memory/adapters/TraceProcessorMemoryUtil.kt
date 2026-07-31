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

import java.nio.ByteBuffer

/** Helper utility methods for primitive field type conversions, sizes, and binary parsing from Trace Processor results. */
object TraceProcessorMemoryUtil {
  fun isPrimitiveArray(className: String): Boolean {
    return className in setOf("byte[]", "char[]", "int[]", "long[]", "float[]", "double[]", "boolean[]", "short[]")
  }

  fun getValueType(typeName: String): ValueObject.ValueType =
    when (typeName) {
      "boolean" -> ValueObject.ValueType.BOOLEAN
      "byte" -> ValueObject.ValueType.BYTE
      "char" -> ValueObject.ValueType.CHAR
      "short" -> ValueObject.ValueType.SHORT
      "int" -> ValueObject.ValueType.INT
      "long" -> ValueObject.ValueType.LONG
      "float" -> ValueObject.ValueType.FLOAT
      "double" -> ValueObject.ValueType.DOUBLE
      else -> ValueObject.ValueType.NULL
    }

  fun getSize(typeName: String): Int =
    when (typeName) {
      "boolean",
      "byte" -> 1
      "char",
      "short" -> 2
      "int",
      "float" -> 4
      "long",
      "double" -> 8
      else -> 4
    }

  fun getDefaultValue(typeName: String): String =
    when (typeName) {
      "boolean" -> "false"
      "char" -> "\u0000"
      "float" -> "0.0"
      "double" -> "0.0"
      "byte",
      "short",
      "int",
      "long" -> "0"
      else -> "null"
    }

  fun getPrimitiveFieldText(typeName: String, value: String): String {
    return if (typeName == "boolean") {
      if (value == "1" || value == "true") "true" else "false"
    } else {
      value
    }
  }

  fun parseRawValue(typeName: String, value: String): Any? =
    when (typeName) {
      "boolean" -> value == "1" || value == "true"
      "byte" -> value.toByteOrNull() ?: 0.toByte()
      "char" -> if (value.isNotEmpty()) value[0] else '\u0000'
      "short" -> value.toShortOrNull() ?: 0.toShort()
      "int" -> value.toIntOrNull() ?: 0
      "long" -> value.toLongOrNull() ?: 0L
      "float" -> value.toFloatOrNull() ?: 0.0f
      "double" -> value.toDoubleOrNull() ?: 0.0
      else -> null
    }

  fun readPrimitiveValueFromBuffer(typeName: String, buffer: ByteBuffer): String =
    when (typeName) {
      "boolean" -> if (buffer.get().toInt() != 0) "true" else "false"
      "byte" -> buffer.get().toString()
      "char" -> buffer.getChar().toString()
      "short" -> buffer.getShort().toString()
      "int" -> buffer.getInt().toString()
      "long" -> buffer.getLong().toString()
      "float" -> buffer.getFloat().toString()
      "double" -> buffer.getDouble().toString()
      else -> {
        buffer.getInt()
        "0"
      }
    }
}

fun String?.standardHeapName(): String =
  when (this) {
    "HEAP_TYPE_APP",
    "app" -> "app"
    "HEAP_TYPE_IMAGE",
    "HEAP_TYPE_BOOT_IMAGE",
    "image" -> "image"
    "HEAP_TYPE_ZYGOTE",
    "zygote" -> "zygote"
    else -> "default"
  }

fun String?.standardHeapId(): Int = if (standardHeapName() == "default") 0 else standardHeapName().hashCode()
