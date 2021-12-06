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

import com.android.adblib.AdbChannelProvider
import com.android.adblib.AdbChannelProviderFactory
import com.android.adblib.AdbLibHost
import com.android.adblib.AdbLibSession
import com.android.ddmlib.AndroidDebugBridge
import com.android.ddmlib.DdmPreferences
import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.Service
import com.intellij.openapi.project.Project
import java.time.Duration

/**
 * Application service that provides access to the implementation of [AdbLibSession] and [AdbLibHost]
 * that integrate with the IntelliJ/Android Studio platform.
 *
 * Note: Prefer using [AdbLibService] if a [Project] instance is available, as this application
 * wide service cannot ensure the underlying ADB server is started and initialized from the [Project]
 * settings (i.e. path to ADB executable).
 */
@Service
class AdbLibApplicationService : Disposable {
  private val host = AndroidAdbLibHost()

  /**
   * An [AdbChannelProvider] that verifies DDMLIB is started before connecting
   * to the ADB server.
   */
  private val channelProvider = AdbChannelProviderFactory.createConnectAddresses(host) {
    ensureDebugBridgeActive()
    listOf(AndroidDebugBridge.getSocketAddress())
  }

  val session = AdbLibSession.create(
    host = host,
    channelProvider = channelProvider,
    connectionTimeout = Duration.ofMillis(DdmPreferences.getTimeOut().toLong())
  )

  override fun dispose() {
    session.close()
    host.close()
  }

  private fun ensureDebugBridgeActive() {
    if (AndroidDebugBridge.getBridge() == null) {
      throw IllegalStateException("ddmlib has not been initialized or has been shutdown")
    }
  }

  companion object {
    @JvmStatic
    val instance: AdbLibApplicationService
      get() = ApplicationManager.getApplication().getService(AdbLibApplicationService::class.java)
  }
}
