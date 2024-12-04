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
package com.android.tools.idea.adblib

import com.android.adblib.AdbChannel
import com.android.adblib.AdbServerChannelProvider
import com.android.annotations.concurrency.AnyThread
import com.android.ddmlib.AndroidDebugBridge
import com.android.tools.idea.adb.AdbService
import com.intellij.openapi.project.Project
import kotlinx.coroutines.guava.await
import kotlinx.coroutines.withContext
import java.net.InetSocketAddress
import java.util.concurrent.TimeUnit

/**
 * An [AdbServerChannelProvider] that keeps track of [Project] instances to retrieve the path
 * to `adb` on a "best effort" basis. If it "best effort", because Android Studio currently
 * does not support multiple `adb` paths and/or versions.
 *
 * This class is thread-safe.
 */
@AnyThread
internal class AndroidAdbServerChannelProvider(private val host: AndroidAdbSessionHost,
                                               private val adbFileTracker: AdbFileLocationTracker) : AdbServerChannelProvider {
  /**
   * The [AdbServerChannelProvider] we delegate to
   */
  private val connectProvider = AdbServerChannelProvider.createConnectAddresses(host) {
    listOf(getAdbSocketAddress())
  }

  /**
   * [AdbServerChannelProvider] implementation: delegate to [connectProvider]
   */
  override suspend fun createChannel(timeout: Long, unit: TimeUnit): AdbChannel {
    return connectProvider.createChannel(timeout, unit)
  }

  private suspend fun getAdbSocketAddress(): InetSocketAddress {
    return withContext(host.ioDispatcher) {
      val needToConnect = AndroidDebugBridge.getBridge()?.let { !it.isConnected } ?: true
      if (needToConnect) {
        // Look for best candidate of `adb` location, then start ddmlib
        AdbService.getInstance().getDebugBridge(adbFileTracker.get()).await()
      }

      // Deprecate: We need this until ddmlib is completely phased out
      @Suppress("DEPRECATION")
      AndroidDebugBridge.getSocketAddress()
    }
  }
}
