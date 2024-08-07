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
package com.android.tools.asdriver.tests.metric

import com.google.common.collect.Maps
import com.google.common.collect.Sets
import com.google.gson.TypeAdapter
import com.google.gson.stream.JsonReader
import com.google.gson.stream.JsonWriter
import java.nio.file.Files
import java.nio.file.Path
import java.util.function.Predicate

open class TelemetryParser(private val spanFilter: SpanFilter) {

  private fun getParentToSpanMap(spans: List<SpanData>): Map<String, MutableSet<SpanElement>> {
    val indexParentToChild = Maps.newHashMap<String, MutableSet<SpanElement>>()
    for (span in spans) {
      val parentSpanId = span.getParentSpanId()
      if (parentSpanId != null) {
        indexParentToChild.getOrPut(parentSpanId) { Sets.newHashSet() }.add(toSpanElement(span))
      }
    }
    return indexParentToChild
  }

  private fun getSpans(file: Path): List<SpanData> {
    val root = Files.newBufferedReader(file).use {
      OpenTelemetryJsonTypeAdapter().read(JsonReader(it))
    }
    val spanData = root.data
    check(spanData.isNotEmpty()) {
      "No 'data' node in json at path $file"
    }
    requireNotNull(spanData.firstOrNull()) {
      "First data element is absent in json file $file"
    }

    val allSpans = spanData.firstOrNull()?.spans
    check(!allSpans.isNullOrEmpty()) {
      "No spans was found"
    }
    return allSpans
  }

  fun getSpanElements(file: Path, spanElementFilter: Predicate<SpanElement> = Predicate { true }): Set<SpanElement> {
    val rawSpans = getSpans(file)
    val index = getParentToSpanMap(rawSpans)
    val result = Sets.newHashSet<SpanElement>()
    for (span in rawSpans.asSequence().filter(spanFilter.rawFilter::test).map { toSpanElement(it) }.filter { spanElementFilter.test(it) }) {
      result.add(span)
      processChild(result, span, index)
    }
    return result
  }

  protected open fun processChild(result: MutableSet<SpanElement>, parent: SpanElement, index: Map<String, Collection<SpanElement>>) {
    index[parent.spanId]?.forEach {
      if (parent.isWarmup) {
        it.isWarmup = true
      }
      result.add(it)
      processChild(result = result, parent = it, index = index)
    }
  }
}

private data class OpenTelemetryJson(
  @JvmField val data: List<OpenTelemetryJsonData> = emptyList(),
)

private data class OpenTelemetryJsonData(
  @JvmField val traceID: String? = null,
  @JvmField val spans: List<SpanData> = mutableListOf(),
)

private class OpenTelemetryJsonTypeAdapter : TypeAdapter<OpenTelemetryJson>() {
  override fun write(out: JsonWriter?, value: OpenTelemetryJson?) {}

  override fun read(reader: JsonReader?): OpenTelemetryJson {
    val data = mutableListOf<OpenTelemetryJsonData>()
    if (!reader!!.hasNext()) {
      return OpenTelemetryJson(data)
    }
    reader.beginObject()
    while (reader.hasNext()) {
      when (reader.nextName()) {
        "data" -> {
          reader.beginArray()
          while (reader.hasNext()) {
            data.add(OpenTelemetryJsonDataTypeAdapter().read(reader))
          }
          reader.endArray()
        }

        else -> reader.skipValue()
      }
    }
    reader.endObject()
    return OpenTelemetryJson(data)
  }
}

private class OpenTelemetryJsonDataTypeAdapter : TypeAdapter<OpenTelemetryJsonData>() {
  override fun write(out: JsonWriter?, value: OpenTelemetryJsonData?) {}

  override fun read(reader: JsonReader?): OpenTelemetryJsonData {
    if (!reader!!.hasNext()) {
      return OpenTelemetryJsonData()
    }
    var traceID = ""
    val spans = mutableListOf<SpanData>()
    reader.beginObject()
    while (reader.hasNext()) {
      when (reader.nextName()) {
        "traceID" -> traceID = reader.nextString()
        "spans" -> {
          reader.beginArray()
          while (reader.hasNext()) {
            spans.add(SpanDataTypeAdapter().read(reader))
          }
          reader.endArray()
        }

        else -> reader.skipValue()
      }
    }
    reader.endObject()
    return OpenTelemetryJsonData(traceID, spans)
  }
}
