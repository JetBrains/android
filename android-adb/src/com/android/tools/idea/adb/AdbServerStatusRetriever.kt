/*
 * Copyright (C) 2025 The Android Open Source Project
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
package com.android.tools.idea.adb

import com.android.adblib.AdbFeatures
import com.android.adblib.ServerStatus
import com.android.adblib.connectionStatusTracker
import com.android.tools.idea.adblib.AdbLibService
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.diagnostic.thisLogger
import com.intellij.openapi.project.Project
import com.intellij.openapi.startup.ProjectActivity
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

@Service(Service.Level.PROJECT)
class AdbServerStatusRetriever(project: Project) {

  private val _serverStatus = MutableStateFlow<ServerStatus?>(null)
  val serverStatus: StateFlow<ServerStatus?> = _serverStatus.asStateFlow()

  private val logger = thisLogger()

  init {
    val adbSession = AdbLibService.getInstance(project).session
    adbSession.scope.launch {
      adbSession.connectionStatusTracker.connectionStatus.collect { connectionStatus ->
        if (!connectionStatus.isConnected) {
          return@collect
        }
        runCatching {
            val hostServices = adbSession.hostServices
            if (hostServices.hostFeatures().contains(AdbFeatures.SERVER_STATUS)) {
              hostServices.serverStatus().let { serverStatus ->
                _serverStatus.value = serverStatus
                logger.info("ADB server logs can be found at: ${serverStatus.absoluteLogPath}")
              }
            }
          }
          .onFailure { e ->
            if (e !is CancellationException) {
              logger.warn("Cannot retrieve `AdbServerStatus` due to a problem with adb server", e)
            }
          }
      }
    }
  }

  companion object {
    @JvmStatic fun getInstance(project: Project): AdbServerStatusRetriever = project.service()
  }
}

/**
 * This activity ensures that AdbServerStatusRetriever service is eagerly initialized to have
 * serverStatus field populated and ready to use.
 */
class AdbServerStatusRetrieverInitializerActivity : ProjectActivity {
  override suspend fun execute(project: Project) {
    AdbServerStatusRetriever.getInstance(project)
  }
}
