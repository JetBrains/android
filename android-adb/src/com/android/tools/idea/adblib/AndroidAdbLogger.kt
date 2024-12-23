/*
 * Copyright (C) 2021 The Android Open Source Project
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
package com.android.tools.idea.adblib

import com.android.adblib.AdbLogger
import com.intellij.openapi.diagnostic.Logger

/**
 * Implementation of [AdbLogger] that integrates with the IntelliJ/Android Studio platform [Logger].
 *
 * See also [AndroidAdbLoggerFactory].
 */
internal class AndroidAdbLogger(private val logger: Logger) : AdbLogger() {
  override val minLevel: Level
    get() =
      if (logger.isDebugEnabled) {
        if (logger.isTraceEnabled) Level.VERBOSE else Level.DEBUG
      } else {
        Level.INFO
      }

  override fun log(level: Level, message: String) {
    when (level) {
      Level.VERBOSE -> logger.trace(message)
      Level.DEBUG -> logger.debug(message)
      Level.INFO -> logger.info(message)
      Level.WARN -> logger.warn(message)
      Level.ERROR -> logger.error(message)
    }
  }

  override fun log(level: Level, exception: Throwable?, message: String) {
    when (level) {
      Level.VERBOSE -> {
        // At the time of this writing, the IJ logger does not have an overload
        // for `trace` that takes both a message and an exception.
        logger.trace(message)
        exception?.let { logger.trace(exception) }
      }
      Level.DEBUG -> logger.debug(message, exception)
      Level.INFO -> logger.info(message, exception)
      Level.WARN -> logger.warn(message, exception)
      Level.ERROR -> logger.error(message, exception)
    }
  }
}
