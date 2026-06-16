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

import androidx.compose.runtime.InternalComposeTracingApi
import androidx.tracing.AbstractTraceSink
import androidx.tracing.AtomicBoolean
import androidx.tracing.DelicateTracingApi
import androidx.tracing.PooledTracePacketArray
import androidx.tracing.wire.ExperimentalRingBufferApi
import androidx.tracing.wire.TraceDriver
import androidx.tracing.wire.TraceSink
import androidx.tracing.wire.createPerfettoFile
import com.android.tools.idea.flags.StudioFlags
import com.intellij.ide.util.PropertiesComponent
import com.intellij.openapi.application.ApplicationInfo
import com.intellij.openapi.application.PathManager
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.diagnostic.thisLogger
import java.nio.file.Path
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.util.concurrent.atomic.AtomicReference
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import okio.appendingSink
import okio.buffer

fun isTracingEnabled(): Boolean =
  StudioFlags.STUDIO_TRACE_LIBRARY_ENABLED.get() && PropertiesComponent.getInstance().getBoolean(TRACING_ENABLED_KEY, false)

@OptIn(ExperimentalRingBufferApi::class)
@Service(Service.Level.APP)
class AndroidxTracerService(internal val serviceScope: CoroutineScope) {
  // This is set just once for this service
  private val serviceInitialized = AtomicBoolean(false)

  private val disabledDriver = TraceDriver(EmptyTraceSink(), false)

  val perfettoFile = PathManager.getTempDir().toFile().createPerfettoFile()
  val driver: AtomicReference<TraceDriver> = AtomicReference(disabledDriver)
  private val compositionTracer = StudioCompositionTracer { driver.get() }
  private val jvmMetricsTracer = JvmMetricsTracer(serviceScope) { driver.get().tracer }

  fun initializeService() {
    if (serviceInitialized.getAndSet(true)) {
      return
    }

    serviceScope.launch { initializeTracing() }
  }

  fun initializeTracing() {
    val enableTracing = isTracingEnabled()
    val currentDriver =
      if (enableTracing) {
        val sink = TraceSink(sequenceId = 1, bufferedSink = perfettoFile.appendingSink().buffer(), coroutineContext = Dispatchers.IO)
        TraceDriver(sink, true) {
          addAttribute("application_version", ApplicationInfo.getInstance().strictVersion)
          addAttribute("trace_created_at", ZonedDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss z")))
        }
      } else {
        disabledDriver
      }

    driver.set(currentDriver)
    thisLogger().info("Tracing Driver initialized and ${if (isTracingEnabled()) "enabled" else "disabled"}.")

    initializeCompositionTracing()
    jvmMetricsTracer.setTracingEnabled(enableTracing)
  }

  @OptIn(InternalComposeTracingApi::class)
  internal fun initializeCompositionTracing() {
    val enabled = PropertiesComponent.getInstance().getBoolean(COMPOSITION_TRACING_ENABLED_KEY, false)
    compositionTracer.setTracingEnabled(enabled)
  }

  fun flush(): Path {
    driver.get().flush()
    return perfettoFile.toPath()
  }

  companion object {
    fun getInstance(): AndroidxTracerService = service()
  }
}

private class EmptyTraceSink : AbstractTraceSink() {
  @OptIn(DelicateTracingApi::class)
  override fun enqueue(pooledPacketArray: PooledTracePacketArray) {
    pooledPacketArray.recycle()
  }

  override fun onDroppedTraceEvent() = Unit

  override fun flush() = Unit

  override fun close() = Unit
}
