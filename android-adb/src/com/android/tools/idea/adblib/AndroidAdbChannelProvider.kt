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
import com.android.adblib.AdbChannelProvider
import com.android.adblib.AdbChannelProviderFactory
import com.android.annotations.concurrency.AnyThread
import com.android.annotations.concurrency.GuardedBy
import com.android.ddmlib.AndroidDebugBridge
import com.android.tools.idea.adb.AdbFileProvider
import com.android.tools.idea.adb.AdbService
import com.intellij.openapi.diagnostic.thisLogger
import com.intellij.openapi.project.Project
import kotlinx.coroutines.guava.await
import kotlinx.coroutines.withContext
import java.io.File
import java.net.InetSocketAddress
import java.util.concurrent.TimeUnit

/**
 * An [AdbChannelProvider] that keeps track of [Project] instances to retrieve the path
 * to `adb` on a "best effort" basis. If it "best effort", because Android Studio currently
 * does not support multiple `adb` paths and/or versions.
 *
 * This class is thread-safe.
 */
@AnyThread
internal class AndroidAdbChannelProvider(private val host: AndroidAdbSessionHost) : AdbChannelProvider {
  private val logger = thisLogger()

  /**
   * The [AdbChannelProvider] we delegate to
   */
  private val connectProvider = AdbChannelProviderFactory.createConnectAddresses(host) {
    listOf(getAdbSocketAddress())
  }

  /**
   * The application [AdbFileProvider], always available
   */
  private val applicationProvider by lazy(LazyThreadSafetyMode.PUBLICATION) {
    AdbFileProvider.fromApplication()
  }

  /**
   * One [AdbFileProvider] per project (we use a LinkedHashMap to keep enumeration ordering consistent).
   */
  @GuardedBy("projectProviders")
  private val projectProviders = LinkedHashMap<Project, AdbFileProvider>()

  /**
   * [AdbChannelProvider] implementation: delegate to [connectProvider]
   */
  override suspend fun createChannel(timeout: Long, unit: TimeUnit): AdbChannel {
    return connectProvider.createChannel(timeout, unit)
  }

  /**
   * Registers a [Project] as a possible source of `adb` path location. The same [Project]
   * instance can be registered multiple times (for convenience).
   */
  fun registerProject(project: Project): Boolean {
    synchronized(projectProviders) {
      return if (!projectProviders.contains(project)) {
        logger.info("Registering project to adblib channel provider: $project")
        projectProviders[project] = AdbFileProvider.fromProject(project)
        true
      } else {
        false
      }
    }
  }

  /**
   * Unregisters a [Project] as a possible source of `adb` path location.
   */
  fun unregisterProject(project: Project): Boolean {
    return synchronized(projectProviders) {
      logger.info("Unregistering project from adblib channel provider: $project")
      projectProviders.remove(project) != null
    }
  }

  private suspend fun getAdbSocketAddress(): InetSocketAddress {
    return withContext(host.ioDispatcher) {
      val needToConnect = AndroidDebugBridge.getBridge()?.let { !it.isConnected } ?: true
      if (needToConnect) {
        // Look for best candidate of `adb` location, then start ddmlib
        AdbService.getInstance().getDebugBridge(getAdbFile()).await()
      }

      // Deprecate: We need this until ddmlib is completely phased out
      @Suppress("DEPRECATION")
      AndroidDebugBridge.getSocketAddress()
    }
  }

  private fun getAdbFile(): File {
    // Go through projects first
    val file = synchronized(projectProviders) {
      projectProviders.values.firstNotNullOfOrNull { it.get() }
    }
    // Then application if nothing found
    return file ?: applicationProvider.get() ?: throw IllegalStateException("ADB location has not been initialized")
  }
}
