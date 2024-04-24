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

import com.intellij.openapi.project.DumbService
import com.intellij.openapi.project.Project
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.channels.trySendBlocking
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.conflate

internal enum class DumbModeStatus {
  DUMB_MODE,
  SMART_MODE,
}

internal fun dumbModeFlow(project: Project): Flow<DumbModeStatus> =
  callbackFlow {
      val connection = project.messageBus.connect()
      connection.subscribe(
        DumbService.DUMB_MODE,
        object : DumbService.DumbModeListener {
          override fun enteredDumbMode() {
            trySendBlocking(DumbModeStatus.DUMB_MODE)
          }

          override fun exitDumbMode() {
            trySendBlocking(DumbModeStatus.SMART_MODE)
          }
        },
      )
      trySendBlocking(
        if (DumbService.isDumb(project)) DumbModeStatus.DUMB_MODE else DumbModeStatus.SMART_MODE
      )
      awaitClose { connection.disconnect() }
    }
    .conflate()
