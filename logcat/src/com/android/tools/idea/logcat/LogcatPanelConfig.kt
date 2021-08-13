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

import com.android.tools.adtui.toolwindow.splittingtabs.SplittingTabsToolWindowFactory
import com.google.gson.Gson
import com.google.gson.JsonSyntaxException
import com.intellij.openapi.components.StoragePathMacros
import com.intellij.openapi.diagnostic.Logger

/**
 * Configuration for a logcat panel.
 *
 * It is persisted into [StoragePathMacros.PRODUCT_WORKSPACE_FILE] by [SplittingTabsToolWindowFactory].
 *
 * @param deviceSerial the serial number of the device monitored by this panel
 * @param packageName the package name of the app monitored by this panel
 */
internal data class LogcatPanelConfig(var deviceSerial: String?, var packageName: String?) {
  companion object {
    private val gson = Gson()

    /**
     * Decodes a JSON string into a [LogcatPanelConfig].
     */
    fun fromJson(json: String?): LogcatPanelConfig? {
      return try {
        gson.fromJson(json, LogcatPanelConfig::class.java)
      } catch (e: JsonSyntaxException) {
        Logger.getInstance(LogcatPanelConfig::class.java).warn("Invalid state", e)
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
}