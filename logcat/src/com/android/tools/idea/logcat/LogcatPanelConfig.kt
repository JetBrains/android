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
package com.android.tools.idea.logcat

import com.android.adblib.DevicePropertyNames.RO_BUILD_VERSION_RELEASE
import com.android.adblib.DevicePropertyNames.RO_BUILD_VERSION_SDK
import com.android.adblib.DevicePropertyNames.RO_PRODUCT_MANUFACTURER
import com.android.adblib.DevicePropertyNames.RO_PRODUCT_MODEL
import com.android.tools.adtui.toolwindow.splittingtabs.SplittingTabsToolWindowFactory
import com.android.tools.idea.logcat.LogcatPanelConfig.FormattingConfig
import com.android.tools.idea.logcat.devices.Device
import com.android.tools.idea.logcat.messages.FormattingOptions
import com.android.tools.idea.logcat.util.LOGGER
import com.google.gson.Gson
import com.google.gson.GsonBuilder
import com.google.gson.JsonDeserializationContext
import com.google.gson.JsonDeserializer
import com.google.gson.JsonElement
import com.google.gson.JsonObject
import com.google.gson.JsonSerializationContext
import com.google.gson.JsonSerializer
import com.google.gson.JsonSyntaxException
import com.intellij.openapi.components.StoragePathMacros
import java.lang.reflect.Type

private const val PROPERTY_NAME_PRESET = "preset"
private const val PROPERTY_NAME_CUSTOM = "custom"

/**
 * Configuration for a logcat panel.
 *
 * It is persisted into [StoragePathMacros.PRODUCT_WORKSPACE_FILE] by [SplittingTabsToolWindowFactory].
 */
internal data class LogcatPanelConfig(
  var device: Device?,
  var formattingConfig: FormattingConfig,
  var filter: String,
  var isSoftWrap: Boolean,
) {

  companion object {
    /**
     * Decodes a JSON string into a [LogcatPanelConfig].
     */
    fun fromJson(json: String?): LogcatPanelConfig? {
      return try {
        if (json?.contains("ro.product.manufacturer") == true) {
          fromOldFormat(json)
        }
        else {
          val config = gson.fromJson(json, LogcatPanelConfig::class.java)
          config?.device?.let {
            @Suppress("SENSELESS_COMPARISON") // GSON doesn't care that model is non-null
            if (it.model == null) {
              config.device = it.copy(model = "")
            }
          }
          config
        }
      }
      catch (e: JsonSyntaxException) {
        LOGGER.warn("Invalid state JSON string: '$json'")
        null
      }
    }

    // If the device was saved in a different format by a previous version, this loads it correctly.
    private fun fromOldFormat(json: String): LogcatPanelConfig? {
      return try {
        val oldDevice = gson.fromJson(json, OldConfigObject::class.java).device
        val device = if (oldDevice.isEmulator) {
          Device.createEmulator(
            oldDevice.serialNumber,
            isOnline = false,
            oldDevice.properties[RO_BUILD_VERSION_RELEASE]?.toIntOrNull() ?: 0,
            oldDevice.properties[RO_BUILD_VERSION_SDK]?.toIntOrNull() ?: 0,
            oldDevice.avdName,
          )
        }
        else {
          Device.createPhysical(
            oldDevice.serialNumber,
            isOnline = false,
            oldDevice.properties[RO_BUILD_VERSION_RELEASE]?.toIntOrNull() ?: 0,
            oldDevice.properties[RO_BUILD_VERSION_SDK]?.toIntOrNull() ?: 0,
            oldDevice.properties[RO_PRODUCT_MANUFACTURER] ?: "",
            oldDevice.properties[RO_PRODUCT_MODEL] ?: "",
          )
        }

        gson.fromJson(json, LogcatPanelConfig::class.java).apply {
          this.device = device
        }
      }
      catch (e: JsonSyntaxException) {
        LOGGER.warn("Invalid state", e)
        null
      }
    }

    /**
     * Encodes a [LogcatPanelConfig] into a JSON string.
     *
     * We replace all double quotes with single quotes because the XML serializer will replace double quotes with `@quot;` while single
     * quotes seem to be fine. This makes the JSON string more human readable.
     *
     * GSON can handle single quoted JSON strings.
     */
    fun toJson(config: LogcatPanelConfig): String = gson.toJson(config).replace(Regex("(?<!\\\\)\""), "'")
  }

  sealed class FormattingConfig {
    internal data class Preset(val style: FormattingOptions.Style) : FormattingConfig()
    internal data class Custom(val formattingOptions: FormattingOptions) : FormattingConfig()

    fun toFormattingOptions(): FormattingOptions {
      return when (this) {
        is Preset -> style.formattingOptions
        is Custom -> formattingOptions
      }
    }

    // This is required since Gson can't deal with the sealed base class.
    internal class Serializer : JsonSerializer<FormattingConfig>, JsonDeserializer<FormattingConfig> {
      override fun serialize(src: FormattingConfig, type: Type, context: JsonSerializationContext): JsonElement {
        val obj = JsonObject()
        when (src) {
          is Preset -> obj.addProperty(PROPERTY_NAME_PRESET, src.style.name)
          is Custom -> obj.add(PROPERTY_NAME_CUSTOM, Gson().toJsonTree(src.formattingOptions))
        }
        return obj
      }

      override fun deserialize(element: JsonElement, type: Type, context: JsonDeserializationContext): FormattingConfig {
        return element.asJsonObject.run {
          when {
            has(PROPERTY_NAME_PRESET) -> Preset(FormattingOptions.Style.valueOf(get(PROPERTY_NAME_PRESET).asString))
            has(PROPERTY_NAME_CUSTOM) -> Custom(Gson().fromJson(get(PROPERTY_NAME_CUSTOM), FormattingOptions::class.java))
            else -> throw IllegalStateException("Invalid FormattingConfig element: $element")
          }
        }
      }
    }
  }

  private class OldConfigObject(val device: Device) {
    data class Device(
      val serialNumber: String,
      val name: String,
      val isEmulator: Boolean,
      val avdName: String,
      val properties: Map<String, String>)
  }
}

private val gson = GsonBuilder()
  .registerTypeAdapter(FormattingConfig::class.java, FormattingConfig.Serializer())
  .create()
