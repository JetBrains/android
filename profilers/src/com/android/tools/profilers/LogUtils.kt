/*
 * Copyright (C) 2023 The Android Open Source Project
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
package com.android.tools.profilers

import com.intellij.openapi.diagnostic.Logger

/**
 * A utility class that logs message with the prefix "PROFILER: ", to make it easy to programmatically
 * extract profiler related logs for various purposes including testing.
 */
class LogUtils {
  companion object {
    @JvmStatic
    fun log(clazz: Class<*>, message: String) = log(true, clazz, message)

    @JvmStatic
    fun logIfVerbose(ideServices: IdeProfilerServices, clazz: Class<*>, message: String) = log(
      ideServices.featureConfig.isVerboseLoggingEnabled, clazz, message)

    private fun log(enabled: Boolean, clazz: Class<*>, message: String) {
      if (!enabled) return
      Logger.getInstance(clazz).info("PROFILER: ".plus(message))
    }
  }
}