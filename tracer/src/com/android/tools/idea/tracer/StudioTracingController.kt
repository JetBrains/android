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
import com.intellij.openapi.application.PathManager
import com.intellij.openapi.diagnostic.thisLogger
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/** Controller that manages the lifecycle and configuration of [Tracing] for Android Studio. */
class StudioTracingController : AppLifecycleListener, TracingConfigProvider {

  override fun appStarted() {
    val log = thisLogger()
    studioTracingScope.launch(Dispatchers.IO) {
      Tracing.initialize(this@StudioTracingController)
      log.info("Tracing Driver initialized and ${if (isTracingEnabled()) "enabled" else "disabled"}.")
    }
  }

  override fun appWillBeClosed(isRestart: Boolean) = Tracing.close()

  // TODO(b/467364934): Use the feature flag to control the feature, not enablement.
  override fun isTracingEnabled(): Boolean = StudioFlags.STUDIO_TRACE_LIBRARY_ENABLED.get()

  override fun getTraceDirectory(): File = PathManager.getTempDir().toFile()
}
