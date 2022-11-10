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

import com.android.adblib.AdbChannelProviderFactory
import com.android.adblib.AdbSession
import com.android.ddmlib.AndroidDebugBridge
import com.android.ddmlib.DdmPreferences
import com.android.tools.idea.adb.AdbFileProvider
import com.android.tools.idea.adb.AdbService
import com.intellij.openapi.project.Project
import kotlinx.coroutines.guava.await
import kotlinx.coroutines.withContext
import java.net.InetSocketAddress
import java.time.Duration

/**
 * The production implementation of [AdbLibService]
 */
internal class AdbLibServiceImpl(val project: Project) : AdbLibService {
  private val host
    get() = AdbLibApplicationService.instance.session.host // re-use host from application service

  private val channelProvider = AdbChannelProviderFactory.createConnectAddresses(host) {
    listOf(getAdbSocketAddress())
  }

  override val session: AdbSession = AdbSession.create(
    host = host,
    channelProvider = channelProvider,
    connectionTimeout = Duration.ofMillis(DdmPreferences.getTimeOut().toLong())
  )

  override fun dispose() {
    session.close()
  }

  private suspend fun getAdbSocketAddress(): InetSocketAddress {
    return withContext(host.ioDispatcher) {
      val needToConnect = AndroidDebugBridge.getBridge()?.let { !it.isConnected } ?: true
      if (needToConnect) {
        // Ensure ddmlib is initialized with ADB server path from project context
        val adbFile = AdbFileProvider.fromProject(project).get() ?: throw IllegalStateException(
          "ADB has not been initialized for this project")
        AdbService.getInstance().getDebugBridge(adbFile).await()
      }
      AndroidDebugBridge.getSocketAddress()
    }
  }
}
