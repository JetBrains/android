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
package com.android.tools.idea.tracer

import androidx.compose.runtime.Composer
import androidx.compose.runtime.CompositionTracer
import androidx.compose.runtime.InternalComposeTracingApi
import androidx.tracing.DelicateTracingApi
import androidx.tracing.wire.TraceDriver
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Recomposition tracer.
 *
 * Reference: https://developer.android.com/develop/ui/compose/tooling/tracing
 */
@OptIn(InternalComposeTracingApi::class)
internal class StudioCompositionTracer(val traceDriver: () -> TraceDriver) : CompositionTracer {
  private val enabled = AtomicBoolean(false)

  fun setTracingEnabled(en: Boolean) {
    enabled.set(en)
    Composer.setTracer(if (en) this else null)
  }

  @OptIn(DelicateTracingApi::class)
  override fun traceEventStart(key: Int, dirty1: Int, dirty2: Int, info: String) {
    val tracer = traceDriver().tracer
    val metadataCloseable = tracer.beginSectionWithMetadata(category = "compose", name = info, token = null, isRoot = false)
    metadataCloseable.metadata.dispatchToTraceSink()
  }

  override fun traceEventEnd() {
    val context = traceDriver().context
    context.process.currentThreadTrack().endSection()
  }

  override fun isTraceInProgress(): Boolean = enabled.get()
}
