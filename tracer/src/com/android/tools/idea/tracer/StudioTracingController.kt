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

import com.android.tools.idea.flags.StudioFlags
import com.android.tools.tracer.Tracing
import com.android.tools.tracer.TracingConfigProvider
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
}

/** Controller that manages the lifecycle and configuration of [Tracing] for Android Studio. */
class StudioTracingController : AppLifecycleListener {

  override fun appStarted() = initializeTracing()

  override fun appWillBeClosed(isRestart: Boolean) = Tracing.close()

  companion object {
    internal fun initializeTracing() {
      val log = thisLogger()
      studioTracingScope.launch(Dispatchers.IO) {
        Tracing.initialize(StudioTracingConfig)
        log.info("Tracing Driver initialized and ${if (StudioTracingConfig.isTracingEnabled()) "enabled" else "disabled"}.")
      }
    }
  }
}
