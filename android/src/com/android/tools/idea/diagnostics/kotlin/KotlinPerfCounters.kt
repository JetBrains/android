/*
 * Copyright (C) 2019 The Android Open Source Project
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
package com.android.tools.idea.diagnostics.kotlin

import org.jetbrains.kotlin.util.PerformanceCounter

interface KotlinPerfCountersMXBean {
  val report: Array<String>
  var enabled: Boolean
  fun resetAllCounters()
}

class KotlinPerfCounters : KotlinPerfCountersMXBean {
  override var enabled: Boolean = false
    set(enable) {
      PerformanceCounter.setTimeCounterEnabled(enable)
      field = enable
    }

  override fun resetAllCounters() {
    PerformanceCounter.resetAllCounters()
  }

  override val report: Array<String>
    get() {
      val values = mutableListOf<String>()
      PerformanceCounter.report { s -> values.add(s) }
      return values.toTypedArray()
    }
}
