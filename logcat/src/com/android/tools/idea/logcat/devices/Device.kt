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
package com.android.tools.idea.logcat.devices

import com.android.sdklib.AndroidApiLevel
import com.android.sdklib.AndroidVersion
import com.android.sdklib.deviceprovisioner.DeviceType
import com.android.tools.idea.ui.screenshot.ScreenshotParameters
import com.google.gson.Gson
import com.google.gson.JsonDeserializationContext
import com.google.gson.JsonDeserializer
import com.google.gson.JsonElement
import com.google.gson.JsonObject
import com.google.gson.JsonSerializationContext
import com.google.gson.JsonSerializer
import java.lang.reflect.Type
import java.nio.file.Path

/** A representation of a device used by [DeviceComboBox]. */
sealed class Device() {
  abstract val deviceId: String
  abstract val name: String
  abstract val serialNumber: String
  abstract val isOnline: Boolean
  abstract val release: String
  abstract val apiLevel: AndroidApiLevel
  abstract val featureLevel: Int
  abstract val type: DeviceType?

  val sdk: Int
    get() = apiLevel.majorVersion

  abstract val isEmulator: Boolean

  abstract fun getScreenshotParameters(): ScreenshotParameters

  abstract fun copy(
    isOnline: Boolean = this.isOnline,
    apiLevel: AndroidApiLevel = this.apiLevel,
  ): Device

  data class PhysicalDevice(
    override val serialNumber: String,
    override val isOnline: Boolean,
    override val release: String,
    override val apiLevel: AndroidApiLevel,
    override val featureLevel: Int,
    val manufacturer: String,
    val model: String,
    override val type: DeviceType,
  ) : Device() {
    override val deviceId: String
      get() = serialNumber

    override val name: String
      get() = if (model.startsWith(manufacturer)) model else "$manufacturer $model"

    override val isEmulator
      get() = false

    override fun getScreenshotParameters() = ScreenshotParameters(serialNumber, type, model)

    override fun copy(isOnline: Boolean, apiLevel: AndroidApiLevel) =
      PhysicalDevice(
        serialNumber,
        isOnline,
        release,
        apiLevel,
        featureLevel,
        manufacturer,
        model,
        type,
      )
  }

  data class EmulatorDevice(
    override val serialNumber: String,
    override val isOnline: Boolean,
    override val release: String,
    override val apiLevel: AndroidApiLevel,
    override val featureLevel: Int,
    val avdName: String,
    val avdPath: String,
    override val type: DeviceType,
  ) : Device() {
    override val isEmulator
      get() = true

    override val deviceId: String
      get() = avdPath

    override val name: String
      get() = avdName

    override fun getScreenshotParameters() =
      ScreenshotParameters(serialNumber, type, Path.of(avdPath))

    override fun copy(isOnline: Boolean, apiLevel: AndroidApiLevel) =
      EmulatorDevice(
        serialNumber,
        isOnline,
        release,
        apiLevel,
        featureLevel,
        avdName,
        avdPath,
        type,
      )
  }

  companion object {
    private const val PROPERTY_PHYSICAL_DEVICE = "physicalDevice"
    private const val PROPERTY_EMULATOR_DEVICE = "emulatorDevice"

    fun createPhysical(
      serialNumber: String,
      isOnline: Boolean,
      release: String,
      androidVersion: AndroidVersion,
      manufacturer: String,
      model: String,
      type: DeviceType? = null,
    ): Device {
      return PhysicalDevice(
        serialNumber,
        isOnline,
        release.normalizeVersion(),
        androidVersion.androidApiLevel,
        androidVersion.featureLevel,
        manufacturer,
        model,
        type ?: DeviceType.HANDHELD,
      )
    }

    fun createEmulator(
      serialNumber: String,
      isOnline: Boolean,
      release: String,
      androidVersion: AndroidVersion,
      avdName: String,
      avdPath: String,
      type: DeviceType? = null,
    ): Device {
      return EmulatorDevice(
        serialNumber,
        isOnline,
        release.normalizeVersion(),
        androidVersion.androidApiLevel,
        androidVersion.featureLevel,
        avdName,
        avdPath,
        type ?: DeviceType.HANDHELD,
      )
    }
  }

  // This is required since Gson can't deal with the sealed base class.
  internal class DeviceSerializer : JsonSerializer<Device?>, JsonDeserializer<Device?> {
    override fun serialize(
      src: Device?,
      type: Type,
      context: JsonSerializationContext,
    ): JsonElement {
      val obj = JsonObject()
      val jsonTree = Gson().toJsonTree(src)
      when (src) {
        is PhysicalDevice -> obj.add(PROPERTY_PHYSICAL_DEVICE, jsonTree)
        is EmulatorDevice -> obj.add(PROPERTY_EMULATOR_DEVICE, jsonTree)
        null -> {}
      }
      return obj
    }

    override fun deserialize(
      element: JsonElement,
      type: Type,
      context: JsonDeserializationContext,
    ): Device? {
      val obj = element.asJsonObject
      val gson = Gson()
      return when {
        obj.has(PROPERTY_PHYSICAL_DEVICE) ->
          gson.fromJson(obj.get(PROPERTY_PHYSICAL_DEVICE), PhysicalDevice::class.java)
        obj.has(PROPERTY_EMULATOR_DEVICE) ->
          gson.fromJson(obj.get(PROPERTY_EMULATOR_DEVICE), EmulatorDevice::class.java)
        else -> null
      }
    }
  }
}

private val VERSION_TRAILING_ZEROS_REGEX = "(\\.0)+$".toRegex()

private fun String.normalizeVersion(): String {
  return VERSION_TRAILING_ZEROS_REGEX.replace(this, "")
}
