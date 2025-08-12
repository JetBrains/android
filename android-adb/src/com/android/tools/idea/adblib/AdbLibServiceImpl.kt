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
package com.android.tools.idea.adblib

import com.android.adblib.AdbServerChannelProvider
import com.android.adblib.AdbSession
import com.android.adblib.AdbSessionHost
import com.android.adblib.tools.debugging.impl.JdwpProcessSessionFinder
import com.android.adblib.tools.debugging.impl.addJdwpProcessSessionFinder
import com.android.ddmlib.AndroidDebugBridge
import com.android.ddmlib.DdmPreferences
import com.android.tools.idea.adb.AdbFileProvider
import com.android.tools.idea.adb.AdbService
import com.intellij.openapi.Disposable
import com.intellij.openapi.project.Project
import kotlinx.coroutines.guava.await
import kotlinx.coroutines.withContext
import java.net.InetSocketAddress
import java.time.Duration

/** The production implementation of [AdbLibService] */
internal class AdbLibServiceImpl(val project: Project) : AdbLibService, Disposable {

  init {
    AdbLibApplicationService.instance.registerProject(project)
  }

  override val session by lazy {
    createProjectSession(project)
  }

  override fun dispose() {
    session.close()
  }

  companion object {
    private fun createProjectSession(project: Project): AdbSession {
      // re-use host from application service
      val host = AdbLibApplicationService.instance.session.host

      // Configure a channel provider to look for ADB from project settings
      val channelProvider =
        AdbLibApplicationService.instance.adbServerController?.let {
          // TODO: use this project's adb file location in the channel provider
          AdbLibApplicationService.instance.channelProvider
        }
          ?: AdbServerChannelProvider.createConnectAddresses(host) {
            listOf(getAdbSocketAddress(project, host))
          }

      return AdbSession.createChildSession(
          parentSession = AdbLibApplicationService.instance.session,
          host = host,
          channelProvider = channelProvider,
          // Double the preferred timeout for remote devices that need more time to execute
          // commands.
          // TODO (b/390732614) Set higher timeout only for remote devices.
          connectionTimeout = Duration.ofMillis(DdmPreferences.getTimeOut().toLong() * 2),
        )
        .also { projectSession ->
          // Ensure all JDWP connections are delegated to the application session
          projectSession.addJdwpProcessSessionFinder(ProjectSessionFinder(projectSession))
        }
    }

    private suspend fun getAdbSocketAddress(
      project: Project,
      host: AdbSessionHost,
    ): InetSocketAddress {
      return withContext(host.ioDispatcher) {
        val needToConnect = AndroidDebugBridge.getBridge()?.let { !it.isConnected } ?: true
        if (needToConnect) {
          // Ensure ddmlib is initialized with ADB server path from project context
          val adbFile =
            AdbFileProvider.fromProject(project).get()
              ?: throw IllegalStateException("ADB has not been initialized for this project")
          AdbService.getInstance().getDebugBridge(adbFile).await()
        }
        AndroidDebugBridge.getSocketAddress()
      }
    }

    class ProjectSessionFinder(private val projectSession: AdbSession) : JdwpProcessSessionFinder {
      override fun findDelegateSession(forSession: AdbSession): AdbSession {
        return if (projectSession === forSession) {
          // Until we properly support one Android SDK per project, delegate to the
          // application level AdbSession for JDWP processes and connections.
          AdbLibApplicationService.instance.session
        } else {
          forSession
        }
      }
    }
  }
}
