/*
 * Copyright (C) 2018 The Android Open Source Project
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
package com.android.tools.idea.gradle.run

import com.android.ide.common.util.getLanguages
import com.android.resources.Density
import com.android.tools.idea.run.AndroidDevice
import com.google.common.collect.Ordering
import com.google.gson.stream.JsonWriter
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.util.io.FileUtil
import java.io.File
import java.io.IOException
import java.io.StringWriter
import java.io.Writer
import java.time.Duration
import java.util.*
import java.util.concurrent.TimeUnit

data class AndroidDeviceSpec(
  val apiLevel: Int,
  val apiCodeName: String?,
  val buildDensity: Density?,
  val buildAbis: ArrayList<String>,
  val languages: SortedSet<String>
) {
  companion object {
    /**
     * Creates an [AndroidDeviceSpec] instance from a list of [devices][AndroidDevice], or `null` if the list of
     * devices is empty.
     *
     * If the [fetchLanguages] parameter is `true`, a request is made to the device to retrieve the list
     * of installed languages using the [getLanguages] method. The [timeout] and [unit]
     * parameters are used in that case to ensure each request has a timeout.
     */
    @Throws(IOException::class)
    @JvmStatic
    fun create(devices: List<AndroidDevice>, fetchLanguages: Boolean, timeout: Long, unit: TimeUnit): AndroidDeviceSpec? {
      if (devices.isEmpty()) {
        return null
      }

      val apiLevel: Int
      var apiCodeName: String? = null
      var buildDensity: Density? = null
      val buildAbis: ArrayList<String> = ArrayList()

      // Find the minimum value of the build API level and pass it to Gradle as a property
      val minVersion = devices.map { it.version }.minWith(Ordering.natural())!!
      apiLevel = minVersion.apiLevel
      if (minVersion.codename != null) {
        apiCodeName = minVersion.codename
      }

      // If we are building for only one device, pass the density and the ABI
      if (devices.size == 1) {
        val device = devices[0]
        val density = Density.getEnum(device.density)
        if (density != null) {
          buildDensity = density
        }

        // Note: the abis are returned in their preferred order which should be maintained while passing it on to Gradle.
        val abis = device.abis.map { it.toString() }
        if (!abis.isEmpty()) {
          buildAbis.addAll(abis)
        }
      }

      val languages = if (fetchLanguages) {
        combineDeviceLanguages(devices, timeout, unit)
      }
      else {
        Collections.emptySortedSet()
      }
      return AndroidDeviceSpec(apiLevel, apiCodeName, buildDensity, buildAbis, languages)
    }

    /**
     * Retrieve the list of installed languages on each device and merge them into a set.
     *
     * Note: We should be able to cache the result of this call to improve performance,
     * i.e. to avoid issuing an ADB call every time, but we first need to define a
     * reasonable cache validation policy, which is not trivial. See b/78452155.
     */
    @Throws(IOException::class)
    private fun combineDeviceLanguages(devices: List<AndroidDevice>,
                                       timeout: Long, unit: TimeUnit): SortedSet<String> {
      return try {
        // Note: the "getLanguages" method below uses Duration.Zero as an infinite timeout value.
        // So we must ensure to never use 0 "nanos" if the caller wants the minimal timeout.
        val nanos = Math.max(1, unit.toNanos(timeout))
        val duration = Duration.ofNanos(nanos)
        val languageSets = devices.map { it.launchedDevice.get(timeout, unit).getLanguages(duration) }
        // Note: If we get an empty list from any device, we want to return an empty list instead of the
        // union of all languages, to ensure we don't have missing language splits on that device.
        if (languageSets.any { it.isEmpty() }) {
          Collections.emptySortedSet()
        }
        else {
          languageSets.flatMap { it }.toSortedSet()
        }
      }
      catch (e: Exception) {
        throw IOException("Error retrieving list of installed languages on device", e)
      }
    }
  }

  /**
   * message DeviceSpec {
   *   // Supported ABI architectures in the order of preference.
   *   // The values should be the string as reported by the platform, e.g.
   *   // "armeabi-v7a" or "x86_64".
   *   repeated string supported_abis = 1;
   *
   *   // All installed locales represented as BCP-47 strings.
   *   repeated string supported_locales = 2;
   *
   *   // List of device features returned by the package manager utility.
   *   repeated string device_features = 3;
   *
   *   // List of OpenGL extension strings supported by the device.
   *   repeated string gl_extensions = 4;
   *
   *   // Screen dpi.
   *   uint32 screen_density = 5;
   *
   *   uint32 sdk_version = 6;
   * }
   */
  @Throws(IOException::class)
  private fun writeJson(out: Writer) {
    JsonWriter(out).use { writer ->
      writer.beginObject()
      writer.name("sdk_version").value(apiLevel.toLong())
      buildDensity?.let {
        if (it.dpiValue > 0) {
          writer.name("screen_density").value(it.dpiValue.toLong())
        }
      }
      if (!buildAbis.isEmpty()) {
        writer.name("supported_abis")
        writer.beginArray()
        buildAbis.forEach {
          writer.value(it)
        }
        writer.endArray()
      }
      if (!languages.isEmpty()) {
        writer.name("supported_locales")
        writer.beginArray()
        languages.forEach {
          writer.value(it)
        }
        writer.endArray()
      }
      writer.endObject()
    }
  }

  private val log: Logger
    get() = Logger.getInstance(AndroidDeviceSpec::class.java)

  @Throws(IOException::class)
  fun writeToJsonTempFile(): File {
    val jsonString = StringWriter().use {
      writeJson(it)
      it.flush()
      it.toString()
    }
    log.info("Device spec file generated: $jsonString")

    // TODO: It'd be nice to clean this up sooner than at exit.
    val tempFile = FileUtil.createTempFile("device-spec", ".json", true)
    FileUtil.writeToFile(tempFile, jsonString)
    return tempFile
  }
}
