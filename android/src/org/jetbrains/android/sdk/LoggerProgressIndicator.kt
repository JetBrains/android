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
package org.jetbrains.android.sdk

import com.android.repository.api.ProgressIndicatorAdapter
import com.intellij.openapi.diagnostic.Logger

/** Copy of [StudioLoggerProgressIndicator]. */
internal class LoggerProgressIndicator(c: Class<*>) : ProgressIndicatorAdapter() {
  private val myLogger: Logger = Logger.getInstance(c)

  override fun logWarning(s: String) {
    myLogger.warn(s)
  }

  override fun logWarning(s: String, e: Throwable?) {
    myLogger.warn(s, e)
  }

  override fun logError(s: String) {
    myLogger.error(s)
  }

  override fun logError(s: String, e: Throwable?) {
    myLogger.error(s, e)
  }

  override fun logInfo(s: String) {
    myLogger.info(s)
  }

  override fun logVerbose(s: String) {
    myLogger.debug(s)
  }
}