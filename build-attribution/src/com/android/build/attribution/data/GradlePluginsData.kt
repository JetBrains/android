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
package com.android.build.attribution.data

import com.android.ide.common.gradle.Version
import com.google.gson.GsonBuilder
import com.google.gson.JsonDeserializationContext
import com.google.gson.JsonDeserializer
import com.google.gson.JsonElement
import com.google.gson.JsonParseException
import com.google.gson.reflect.TypeToken
import com.intellij.openapi.diagnostic.Logger
import java.lang.reflect.Type

data class GradlePluginsData(
  val pluginsInfo: List<PluginInfo>
) {
  data class PluginInfo(
    val name: String,
    /**
     * List of full plugin class names (implementing gradle Plugin interface) that define this plugin.
     * Can be a project prefix to match all plugin classes under some package.
     * Examples:
     * * `com.android.build.gradle.AppPlugin` to match app plugin class from AGP,
     * * `com.android.build.gradle.` to match all AGP plugins.
     */
    val pluginClasses: List<String>,
    val pluginArtifact: DependencyCoordinates? = null,
    //TODO mlazeba: should this be a list of supported ranges instead?
    val configurationCachingCompatibleFrom: Version? = null
  ) {
    /** Checks if [plugin] matches this PluginInfo entry. */
    fun isThisPlugin(plugin: PluginData): Boolean {
      return plugin.pluginType == PluginData.PluginType.BINARY_PLUGIN
      && pluginClasses.any { plugin.idName.startsWith(it) }
    }
  }

  data class DependencyCoordinates(
    val group: String,
    val name: String
  ) {
    override fun toString(): String {
      return "$group:$name"
    }
  }

  companion object {
    val emptyData = GradlePluginsData(emptyList())

    fun loadFromJson(jsonString: String): GradlePluginsData {
      val versionDeserializer = JsonDeserializer { json: JsonElement, typeOfT: Type?, context: JsonDeserializationContext? ->
        json.asString.takeIf { it != "N/A" }?.let { Version.parse(it) }
      } as JsonDeserializer<Version>
      val dependencyCoordinatesDeserializer = JsonDeserializer { json: JsonElement, typeOfT: Type?, context: JsonDeserializationContext? ->
        json.asString.let{ DependencyCoordinates(it.substringBefore(":"), it.substringAfter(":"))}
      } as JsonDeserializer<DependencyCoordinates>
      val gson = GsonBuilder()
        .registerTypeAdapter(object : TypeToken<Version>() {}.type, versionDeserializer)
        .registerTypeAdapter(object : TypeToken<DependencyCoordinates>() {}.type, dependencyCoordinatesDeserializer)
        .create()
      return try {
        gson.fromJson(jsonString, GradlePluginsData::class.java)
      }
      catch (e: JsonParseException) {
        Logger.getInstance(GradlePluginsData::class.java).error("Parse exception while reading plugins data", e)
        emptyData
      }
    }
  }
}