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
package com.android.tools.idea.gradle.project.sync

import com.intellij.openapi.externalSystem.model.settings.ExternalSystemExecutionSettings.REMOTE_PROCESS_IDLE_TTL_IN_MS_KEY
import org.junit.rules.ExternalResource
import kotlin.time.Duration

class DaemonIdleTimeoutRule(private val timeout: Duration): ExternalResource() {
  override fun before() {
    // If this is not set, tests won't respect the actual value below.
    System.setProperty(USE_EXTERNAL_SYSTEM_REMOTE_PROCESS_IDLE_TTL_FOR_TESTS_KEY, true.toString())
    System.setProperty(REMOTE_PROCESS_IDLE_TTL_IN_MS_KEY, timeout.inWholeMilliseconds.toString())
  }

  companion object {
    // This is from [GradleConnectorService]
    const val USE_EXTERNAL_SYSTEM_REMOTE_PROCESS_IDLE_TTL_FOR_TESTS_KEY = "gradle.connector.useExternalSystemRemoteProcessIdleTtlForTests"
  }
}