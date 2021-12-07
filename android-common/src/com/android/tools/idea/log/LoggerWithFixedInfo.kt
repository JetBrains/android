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
package com.android.tools.idea.log

import com.intellij.openapi.diagnostic.DelegatingLogger
import com.intellij.openapi.diagnostic.Logger

/**
 * A logger that supports adding additional info as a prefix. This allows to, for example, temporarily log a request id next to every
 * line of the log as:
 *
 * "[requestId=anId] The actual log line".
 */
class LoggerWithFixedInfo(delegate: Logger, information: Map<String, String>): DelegatingLogger<Logger>(delegate) {
  private val prefix = if (information.isNotEmpty())
    "[" + information.entries.joinToString(" ") { "${it.key}=${it.value}" } + "] "
  else ""

  override fun debug(message: String) {
    super.debug("$prefix$message")
  }

  override fun debug(t: Throwable?) {
    debug("", t)
  }

  override fun debug(message: String, t: Throwable?) {
    super.debug("$prefix$message", t)
  }

  override fun info(message: String) {
    super.info("$prefix$message")
  }

  override fun info(message: String, t: Throwable?) {
    super.info("$prefix$message", t)
  }

  override fun warn(message: String, t: Throwable?) {
    super.warn("$prefix$message", t)
  }

  override fun error(message: String, t: Throwable?, vararg details: String?) {
    super.error("$prefix$message", t, *details)
  }
}