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
package com.android.tools.idea.run.deployment.selector

import com.intellij.execution.RunManager
import com.intellij.execution.RunManagerListener
import com.intellij.execution.RunnerAndConfigurationSettings
import com.intellij.openapi.project.Project
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.channels.trySendBlocking
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow

internal fun runConfigurationFlow(project: Project): Flow<RunnerAndConfigurationSettings?> =
  callbackFlow {
    val connection = project.messageBus.connect()
    connection.subscribe(
      RunManagerListener.TOPIC,
      object : RunManagerListener {
        override fun runConfigurationSelected(settings: RunnerAndConfigurationSettings?) {
          trySendBlocking(settings)
        }

        override fun runConfigurationChanged(
          settings: RunnerAndConfigurationSettings,
          existingId: String?,
        ) {
          trySendBlocking(settings)
        }

        override fun runConfigurationRemoved(settings: RunnerAndConfigurationSettings) {}

        override fun runConfigurationAdded(settings: RunnerAndConfigurationSettings) {}
      },
    )
    send(RunManager.getInstance(project).selectedConfiguration)
    awaitClose { connection.disconnect() }
  }
