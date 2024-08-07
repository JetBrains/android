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

import java.util.function.Predicate

class SpanFilter internal constructor(
  @JvmField internal val filter: (SpanElement) -> Boolean,
  @JvmField internal val rawFilter: Predicate<SpanData>,
) {
  companion object {
    fun nameEquals(name: String): SpanFilter {
      return SpanFilter(filter = { spanData -> spanData.name == name }, rawFilter = { it.operationName == name })
    }

    fun none(): SpanFilter {
      return SpanFilter(filter = { false }, rawFilter = { false })
    }
  }
}