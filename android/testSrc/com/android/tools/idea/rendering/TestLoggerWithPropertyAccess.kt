/*
 * Copyright (C) 2022 The Android Open Source Project
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
package com.android.tools.idea.rendering

import com.intellij.openapi.diagnostic.Logger
import org.apache.log4j.Level

/**
 * A [Logger] that is used for testing sandboxing. This will simulate a property access during error logging that does not happen in the
 * unit test logging but does happen in production.
 */
class TestLoggerWithPropertyAccess(private val delegate: Logger): Logger() {
  override fun isDebugEnabled(): Boolean = delegate.isDebugEnabled
  override fun debug(message: String?) = delegate.debug(message)
  override fun debug(t: Throwable?) = delegate.debug(t)
  override fun debug(message: String?, t: Throwable?) = delegate.debug(message, t)
  override fun info(message: String?) = delegate.info(message)
  override fun info(message: String?, t: Throwable?) = delegate.info(message, t)
  override fun warn(message: String?, t: Throwable?) = delegate.warn(message, t)
  override fun error(message: String?, t: Throwable?, vararg details: String?) {
    // Simulate a properties access as the IdeaLogger does.
    System.getProperties()
    delegate.error(message, t, *details)
  }
  @Suppress("UnstableApiUsage")
  override fun setLevel(level: Level) = delegate.setLevel(level)
}