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
package com.android.tools.sdk

import com.android.prefs.AndroidLocationsSingleton
import com.android.sdklib.BuildToolInfo
import com.android.sdklib.IAndroidTarget
import com.android.sdklib.devices.DeviceManager
import com.android.sdklib.repository.AndroidSdkHandler
import com.android.tools.sdk.DeviceManagers.getDeviceManager
import java.io.File
import java.io.IOException
import java.lang.ref.SoftReference
import java.nio.file.Path
import java.util.concurrent.ConcurrentHashMap

class AndroidSdkData private constructor(localSdk: File) {
  val sdkHandler: AndroidSdkHandler = AndroidSdkHandler.getInstance(AndroidLocationsSingleton, localSdk.toPath())
  val deviceManager: DeviceManager = getDeviceManager(sdkHandler)

  val location: Path
    get() = checkNotNull(sdkHandler.location)

  val locationFile: File
    get() = location.toFile()

  @get:Deprecated("")
  val path: String
    get() = location.toString()

  fun getLatestBuildTool(allowPreview: Boolean): BuildToolInfo? =
    sdkHandler.getLatestBuildTool(LoggerProgressIndicator(javaClass), allowPreview)

  val targets: Array<IAndroidTarget>
    get() = targetCollection.toTypedArray()

  private val targetCollection: Collection<IAndroidTarget>
    get() {
      val progress = LoggerProgressIndicator(javaClass)
      return sdkHandler.getAndroidTargetManager(progress).getTargets(progress)
    }

  fun getTargets(includeAddOns: Boolean): Array<IAndroidTarget> {
    val targets = targetCollection
    return if (includeAddOns) {
      targets.toTypedArray()
    } else {
      targets.filter { it.isPlatform }.toTypedArray()
    }
  }

  fun findTargetByApiLevel(apiLevel: String): IAndroidTarget? = targets.find { targetHasId(it, apiLevel) }

  fun findTargetByHashString(hashString: String): IAndroidTarget? {
    val progress = LoggerProgressIndicator(javaClass)
    return sdkHandler.getAndroidTargetManager(progress).getTargetFromHashString(hashString, progress)
  }

  override fun equals(other: Any?): Boolean {
    if (this === other) return true
    if (other !is AndroidSdkData) return false
    return location.normalize().toAbsolutePath() == other.location.normalize().toAbsolutePath()
  }

  override fun hashCode(): Int = location.normalize().toAbsolutePath().hashCode()

  companion object {
    private val ourCache = ConcurrentHashMap<String, SoftReference<AndroidSdkData>>()

    @JvmStatic
    @JvmOverloads
    fun getSdkData(sdkLocation: File, forceReparse: Boolean = false): AndroidSdkData? {
      return getSdkData(sdkLocation, forceReparse, checkValidity = true)
    }

    // Used by standalone-render
    @JvmStatic
    fun getSdkDataWithoutValidityCheck(sdkLocation: File): AndroidSdkData =
      getSdkData(sdkLocation, forceReparse = false, checkValidity = false)!!

    private fun getSdkData(sdkLocation: File, forceReparse: Boolean, checkValidity: Boolean): AndroidSdkData? {
      val canonicalPath =
        try {
          sdkLocation.canonicalPath
        } catch (ignore: IOException) {
          if (checkValidity) return null
          // We do not care about whether sdk exists or not, we are using the path as a key
          sdkLocation.path
        }

      // Try to use cached data.
      if (!forceReparse) {
        val cachedRef = ourCache[canonicalPath]
        if (cachedRef != null) {
          val cachedData = cachedRef.get()
          if (cachedData == null) {
            ourCache.remove(canonicalPath, cachedRef)
          } else {
            return cachedData
          }
        }
      }

      val canonicalLocation = File(canonicalPath)
      if (checkValidity && !isValid(canonicalLocation)) {
        return null
      }

      val sdkData = AndroidSdkData(canonicalLocation)
      ourCache[canonicalPath] = SoftReference(sdkData)
      return sdkData
    }

    @JvmStatic fun getSdkData(sdkPath: String): AndroidSdkData? = getSdkData(File(sdkPath))

    private fun targetHasId(target: IAndroidTarget, id: String): Boolean {
      return id == target.version.apiString || id == target.versionName
    }
  }
}
