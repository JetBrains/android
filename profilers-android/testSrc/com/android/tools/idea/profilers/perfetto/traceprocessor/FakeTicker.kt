/*
 * Copyright (C) 2020 The Android Open Source Project
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
package com.android.tools.idea.profilers.perfetto.traceprocessor

import com.google.common.base.Ticker
import java.util.concurrent.TimeUnit

/**
 * FakeTicker with auto increments on each {@link read()} call.
 */
class FakeTicker(val step: Long, val unit: TimeUnit): Ticker() {
  private var internalTimerNanos = 0L

  override fun read(): Long {
    internalTimerNanos += unit.toNanos(step)
    return internalTimerNanos
  }
}