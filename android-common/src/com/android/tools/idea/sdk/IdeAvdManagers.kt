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
package com.android.tools.idea.sdk

import com.android.prefs.AndroidLocationsException
import com.android.prefs.AndroidLocationsSingleton
import com.android.sdklib.internal.avd.AvdManager
import com.android.sdklib.repository.AndroidSdkHandler
import com.android.tools.idea.log.LogWrapper
import com.android.utils.ILogger
import java.nio.file.Path

/**
 * A cache / factory of AvdManager instances, keyed by their constructor parameters.
 */
interface AvdManagerCache {
  @Throws(AndroidLocationsException::class)
  fun getAvdManager(sdkHandler: AndroidSdkHandler, avdHomeDir: Path): AvdManager?

  @Throws(AndroidLocationsException::class)
  fun getAvdManager(sdkHandler: AndroidSdkHandler): AvdManager? =
    getAvdManager(sdkHandler, AndroidLocationsSingleton.avdLocation)
}

internal class AvdManagerCacheImpl(
  private val logger: ILogger,
  private val deviceManagerCache: DeviceManagerCache
) : AvdManagerCache {
  private data class AvdManagerCacheKey(val sdkHandler: AndroidSdkHandler, val avdHomeDir: Path)

  private val avdManagers = mutableMapOf<AvdManagerCacheKey, AvdManager>()

  @Throws(AndroidLocationsException::class)
  override fun getAvdManager(sdkHandler: AndroidSdkHandler, avdHomeDir: Path): AvdManager? =
    synchronized(avdManagers) {
      val key = AvdManagerCacheKey(sdkHandler, avdHomeDir)
      var avdManager = avdManagers[key]
      if (avdManager == null) {
        try {
          avdManager =
            AvdManager.createInstance(
              key.sdkHandler,
              key.avdHomeDir,
              deviceManagerCache.getDeviceManager(key.sdkHandler),
              logger
            )
          avdManagers.put(key, avdManager)
        } catch (e: AndroidLocationsException) {
          throw e
        } catch (e: Exception) {
          // This is somewhat suspect
          logger.error(e, "Exception during AvdManager initialization")
          return null
        }
      }
      return avdManager
    }
}

/**
 * The [AvdManagerCache] instance used within Studio.
 */
object IdeAvdManagers : AvdManagerCache {
  private val logger = LogWrapper(AvdManager::class.java)
  private val impl = AvdManagerCacheImpl(logger, IdeDeviceManagers.cache)

  // We don't use "by AvdManagerCacheImpl" because Kotlin doesn't preserve @Throws on the resulting
  // delegate methods.
  @Throws(AndroidLocationsException::class)
  override fun getAvdManager(sdkHandler: AndroidSdkHandler, avdHomeDir: Path): AvdManager? =
    impl.getAvdManager(sdkHandler, avdHomeDir)
}
