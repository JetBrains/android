/*
 * Copyright (C) 2024 The Android Open Source Project
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

@file:Suppress("ReplaceGetOrSet")

package com.android.tools.asdriver.tests.metric

import com.google.gson.TypeAdapter
import com.google.gson.stream.JsonReader
import com.google.gson.stream.JsonWriter
import kotlin.math.roundToLong

/**
 * Represents a span element that defines a unit of work or a segment of time.
 *
 * @property isWarmup Indicates whether this span element is a warm-up span.
 * @property name The name of the span element.
 * @property duration The duration of the span element in milliseconds.
 * @property startTimestamp The timestamp when the span element started, in milliseconds.
 * @property spanId The unique identifier for the span element.
 * @property parentSpanId The unique identifier of the parent span element, if any.
 * @property tags The list of key-value pairs representing the tags associated with the span element.
 */
data class SpanElement(
  @JvmField var isWarmup: Boolean,
  @JvmField val name: String,
  @JvmField val duration: Long,
  @JvmField val startTimestamp: Long,
  @JvmField val spanId: String,
  @JvmField val parentSpanId: String?,
  @JvmField val tags: List<Pair<String, String>>,
)

internal fun toSpanElement(span: SpanData): SpanElement {
  val tags = getTags(span)
  return SpanElement(
    isWarmup = isWarmup(tags),
    name = span.operationName,
    duration = (span.duration / 1000.0).roundToLong(),
    startTimestamp = (span.startTime / 1000.0).roundToLong(),
    spanId = span.spanID,
    parentSpanId = span.getParentSpanId(),
    tags = tags,
  )
}

data class SpanData(
  @JvmField val spanID: String,
  @JvmField val operationName: String,
  @JvmField val duration: Long,
  @JvmField val startTime: Long,
  @JvmField val references: List<SpanRef> = emptyList(),
  @JvmField val tags: List<SpanTag> = emptyList(),
)

class SpanDataTypeAdapter : TypeAdapter<SpanData>() {
  override fun write(out: JsonWriter?, value: SpanData?) {}

  override fun read(reader: JsonReader?): SpanData {
    reader!!.beginObject()
    var spanID = ""
    var operationName = ""
    var duration = 0L
    var startTime = 0L
    val references = mutableListOf<SpanRef>()
    val tags = mutableListOf<SpanTag>()

    while(reader.hasNext()) {
      when (reader.nextName()) {
        "spanID" -> spanID = reader.nextString()
        "operationName" -> operationName = reader.nextString()
        "duration" -> duration = reader.nextLong()
        "startTime" -> startTime = reader.nextLong()
        "references" -> {
          reader.beginArray()
          while(reader.hasNext()) {
            references.add(SpanRefTypeAdapter().read(reader))
          }
          reader.endArray()
        }
        "tags" -> {
          reader.beginArray()
          while(reader.hasNext()) {
            tags.add(SpanTagTypeAdapter().read(reader))
          }
          reader.endArray()
        }
        else -> reader.skipValue()
      }
    }
    reader.endObject()
    return SpanData(spanID, operationName, duration, startTime, references, tags)
  }

}

data class SpanRef(
  @JvmField val refType: String? = null,
  @JvmField val traceID: String? = null,
  @JvmField val spanID: String? = null,
)

private class SpanRefTypeAdapter : TypeAdapter<SpanRef>() {
  override fun write(out: JsonWriter?, value: SpanRef?) {}

  override fun read(reader: JsonReader?): SpanRef {
    reader!!.beginObject()
    var refType : String? = null
    var traceID : String? = null
    var spanID : String? = null

    while(reader.hasNext()) {
      when (reader.nextName()) {
        "refType" -> refType = reader.nextString()
        "traceID" -> traceID = reader.nextString()
        "spanID" -> spanID = reader.nextString()
        else -> reader.skipValue()
      }
    }
    reader.endObject()
    return SpanRef(refType, traceID, spanID)
  }

}

data class SpanTag(
  @JvmField val key: String? = null,
  @JvmField val type: String? = null,
  @JvmField val value: String? = null,
)

private class SpanTagTypeAdapter : TypeAdapter<SpanTag>() {
  override fun write(out: JsonWriter?, value: SpanTag?) {}

  override fun read(reader: JsonReader?): SpanTag {
    reader!!.beginObject()
    var key : String? = null
    var type : String? = null
    var value : String? = null

    while(reader.hasNext()) {
      when (reader.nextName()) {
        "key" -> key = reader.nextString()
        "type" -> type = reader.nextString()
        "value" -> value = reader.nextString()
        else -> reader.skipValue()
      }
    }
    reader.endObject()
    return SpanTag(key, type, value)
  }

}

internal fun SpanData.getParentSpanId(): String? {
  return references.firstOrNull { it.refType == "CHILD_OF" }?.spanID
}

private fun getTags(span: SpanData): List<Pair<String, String>> {
  val tags = ArrayList<Pair<String, String>>(span.tags.size)
  for (tag in span.tags) {
    val attributeName = tag.key!!
    val textValue = tag.value!!
    tags.add(Pair(attributeName, textValue))
  }
  return tags
}

private fun isWarmup(tags: List<Pair<String, String>>): Boolean {
  return tags.find { it.first == "warmup" && it.second == "true" } != null
}