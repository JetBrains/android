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
package com.android.tools.rendering.security

import com.intellij.openapi.diagnostic.DelegatingLogger
import com.intellij.openapi.diagnostic.Logger

/**
 * A [Logger] that is used for testing sandboxing. This will simulate a property access during error
 * logging that does not happen in the unit test logging but does happen in production.
 */
class TestLoggerWithPropertyAccess(delegate: Logger) : DelegatingLogger<Logger>(delegate) {
  override fun error(message: String?, t: Throwable?, vararg details: String?) {
    // Simulate a properties access as the IdeaLogger does.
    System.getProperties()
    super.error(message, t, *details)
  }
}
