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

import com.android.resources.Density
import com.android.tools.idea.run.AndroidDevice
import com.google.common.collect.Ordering
import com.google.gson.stream.JsonWriter
import com.intellij.openapi.util.io.FileUtil
import java.io.*
import java.util.*

data class AndroidDeviceSpec(
  val apiLevel: Int = 0,
  val apiCodeName: String? = null,
  val buildDensity: Density? = null,
  val buildAbis: ArrayList<String> = ArrayList()
) {
  companion object {
    @JvmStatic
    fun create(devices: List<AndroidDevice>): AndroidDeviceSpec? {
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

      return AndroidDeviceSpec(apiLevel, apiCodeName, buildDensity, buildAbis)
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
      writer.endObject()
    }
  }

  @Throws(IOException::class)
  fun writeToJsonTempFile(): File {
    // TODO: It'd be nice to clean this up sooner than at exit.
    val tempFile = FileUtil.createTempFile("device-spec", ".json", true)
    FileOutputStream(tempFile).use {
      OutputStreamWriter(it).use {
        BufferedWriter(it).use {
          writeJson(it)
        }
      }
    }
    return tempFile
  }
}
