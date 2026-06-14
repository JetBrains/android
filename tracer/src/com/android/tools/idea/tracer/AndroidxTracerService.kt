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

  @Volatile private var currentSession: TraceSession = TraceSession.Disabled
  private val compositionTracer = StudioCompositionTracer { currentSession.driver }
  private val jvmMetricsTracer = JvmMetricsTracer(serviceScope) { currentSession.driver.tracer }

  fun initializeService() {
    if (serviceInitialized.getAndSet(true)) {
      return
    }

    serviceScope.launch { initializeTracing() }
  }

  @Synchronized
  fun initializeTracing() {
    val tracingEnabled = isTracingEnabled()
    currentSession = if (tracingEnabled) createTraceSession() else TraceSession.Disabled
    thisLogger().info("Tracing Driver initialized and ${if (tracingEnabled) "enabled" else "disabled"}.")

    val compositionTracingEnabled = PropertiesComponent.getInstance().getBoolean(COMPOSITION_TRACING_ENABLED_KEY, false)
    setCompositionTracingEnabled(compositionTracingEnabled && tracingEnabled)
    jvmMetricsTracer.setTracingEnabled(tracingEnabled)
  }

  internal fun setCompositionTracingEnabled(enabled: Boolean) {
    compositionTracer.setTracingEnabled(enabled)
  }

  @Synchronized
  fun flush(reset: Boolean = true): Path =
    when (val current = currentSession) {
      is TraceSession.Enabled -> {
        current.driver.flush()
        if (reset) {
          // Publish the new session before closing the old driver so other threads don't
          // observe currentSession pointing at a closed driver.
          currentSession = createTraceSession()
          current.driver.close()
        }
        current.traceFile
      }
      is TraceSession.Disabled -> error("Tracing is disabled")
    }

  private fun createTraceSession(): TraceSession.Enabled {
    val traceFile = PathManager.getTempDir().toFile().createPerfettoFile().toPath()
    return TraceSession.Enabled(traceFile = traceFile, driver = createTraceDriver(traceFile))
  }

  private fun createTraceDriver(traceFile: Path): TraceDriver {
    val sink = TraceSink(sequenceId = 1, bufferedSink = traceFile.toFile().appendingSink().buffer(), coroutineContext = Dispatchers.IO)
    return TraceDriver(sink, true) {
      addAttribute("application_version", ApplicationInfo.getInstance().strictVersion)
      addAttribute("trace_created_at", ZonedDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss z")))
    }
  }

  companion object {
    fun getInstance(): AndroidxTracerService = service()
  }
}

private sealed class TraceSession(open val driver: TraceDriver) {
  data class Enabled(val traceFile: Path, override val driver: TraceDriver) : TraceSession(driver)

  data object Disabled : TraceSession(TraceDriver(EmptyTraceSink(), false))
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
