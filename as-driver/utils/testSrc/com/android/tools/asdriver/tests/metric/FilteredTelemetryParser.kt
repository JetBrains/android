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

internal class FilteredTelemetryParser(
  spanFilter: SpanFilter,
  private val childFilter: SpanFilter,
) : TelemetryParser(spanFilter) {
  override fun processChild(result: MutableSet<SpanElement>, parent: SpanElement, index: Map<String, Collection<SpanElement>>) {
    index[parent.spanId]?.forEach {
      if (parent.isWarmup) {
        it.isWarmup = true
      }
      if (!childFilter.filter(it)) {
        return@forEach
      }
      result.add(it)
      processChild(result, it, index)
    }
  }
}