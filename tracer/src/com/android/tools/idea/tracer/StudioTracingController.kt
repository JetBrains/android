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
import com.android.tools.idea.flags.StudioFlags
import com.android.tools.tracer.Tracing
import com.android.tools.tracer.TracingConfigProvider
import com.android.tools.tracer.beginSectionWithMetadata
import com.android.tools.tracer.endSection
import com.android.tools.tracer.isTracingEnabled
import com.intellij.ide.AppLifecycleListener
import com.intellij.ide.util.PropertiesComponent
import com.intellij.openapi.application.PathManager
import com.intellij.openapi.diagnostic.thisLogger
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

private object StudioTracingConfig : TracingConfigProvider {
  override fun isTracingEnabled(): Boolean =
    StudioFlags.STUDIO_TRACE_LIBRARY_ENABLED.get() && PropertiesComponent.getInstance().getBoolean(TRACING_ENABLED_KEY, false)

  override fun getTraceDirectory(): File = PathManager.getTempDir().toFile()

  override fun getRingBufferCapacity(): Long = 20_000_000 // 20 MB trace file
}

/**
 * Controller that manages the lifecycle and configuration of [Tracing] for Android Studio. It also enables Compose Composition tracing to
 * write to the tracer.
 */
class StudioTracingController : AppLifecycleListener {

  override fun appStarted() {
    initializeTracing()
    enableComposeCompositionTracing()
  }

  // Enable Compose Composition tracing in the Studio tracer.
  // See https://developer.android.com/develop/ui/compose/tooling/tracing for more details
  @OptIn(InternalComposeTracingApi::class)
  private fun enableComposeCompositionTracing() {
    Composer.setTracer(
      object : CompositionTracer {
        override fun traceEventStart(key: Int, dirty1: Int, dirty2: Int, info: String) {
          beginSectionWithMetadata("compose", info)
        }

        override fun traceEventEnd() {
          endSection()
        }

        override fun isTraceInProgress(): Boolean {
          return isTracingEnabled()
        }
      }
    )
  }

  override fun appWillBeClosed(isRestart: Boolean) {
    // Gracefully close tracing on a normal IDE exit.
    // We only save the trace file when the shutdown hook (crash/force-kill) catches it.
    Tracing.close(false)
    try {
      Runtime.getRuntime().removeShutdownHook(hook)
    } catch (_: IllegalStateException) {
      // Ignored: JVM is already shutting down
    }
  }

  companion object {
    private val hook = Thread { Tracing.close(true) }
    private var hookRegistered = false

    internal fun initializeTracing() {
      val log = thisLogger()
      studioTracingScope.launch(Dispatchers.IO) {
        Tracing.initialize(StudioTracingConfig)
        log.info("Tracing Driver initialized and ${if (StudioTracingConfig.isTracingEnabled()) "enabled" else "disabled"}.")
      }

      // Add a shutdown hook to ensure trace is flushed on process crash or termination.
      if (!hookRegistered) {
        try {
          Runtime.getRuntime().addShutdownHook(hook)
          hookRegistered = true
        } catch (_: IllegalArgumentException) {
          // Hook already registered
        } catch (_: IllegalStateException) {
          // JVM is shutting down
        }
      }
    }
  }
}
