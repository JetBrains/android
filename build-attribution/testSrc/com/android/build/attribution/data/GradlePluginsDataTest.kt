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
import com.google.common.truth.Truth
import org.junit.Test

class GradlePluginsDataTest {

  @Test
  fun testJsonV1Parsed() {
    val fileContent = """
    {"pluginsInfo":[{
      "name": "MyPlugin",
      "pluginClasses": ["my.plugin.pluginA","my.plugin.pluginB"],
      "pluginArtifact": "org.my:gradle-plugin",
      "configurationCachingCompatibleFrom": "1.0.0"
    }]}
    """.trimIndent()

    val parsedData = GradlePluginsData.loadFromJson(fileContent)

    Truth.assertThat(parsedData).isEqualTo(
      GradlePluginsData(
        listOf(
          GradlePluginsData.PluginInfo(
            pluginClasses = listOf("my.plugin.pluginA", "my.plugin.pluginB"),
            name = "MyPlugin",
            pluginArtifact = GradlePluginsData.DependencyCoordinates("org.my", "gradle-plugin"),
            configurationCachingCompatibleFrom = Version.parse("1.0.0")
          )
        )
      )
    )
  }

  @Test
  fun testJsonParsedWithoutSomeFields() {
    val fileContent = """
    {"pluginsInfo":[{
      "pluginClasses": ["my.plugin.pluginA","my.plugin.pluginB"],
      "name": "MyPlugin"
    }]}
    """.trimIndent()

    val parsedData = GradlePluginsData.loadFromJson(fileContent)

    Truth.assertThat(parsedData).isEqualTo(
      GradlePluginsData(
        listOf(
          GradlePluginsData.PluginInfo(
            pluginClasses = listOf("my.plugin.pluginA", "my.plugin.pluginB"),
            name = "MyPlugin"
          )
        )
      )
    )
  }

  @Test
  fun testJsonParsedWithExtraFields() {
    val fileContent = """
    { "pluginsInfo": [{
        "name": "MyPlugin",
        "pluginClasses": ["my.plugin.pluginA","my.plugin.pluginB"],
        "pluginArtifact": "org.my:gradle-plugin",
        "configurationCachingCompatibleFrom": "1.0.0",
        "newField": "newValue"
      }]}
    """.trimIndent()


    val parsedData = GradlePluginsData.loadFromJson(fileContent)

    Truth.assertThat(parsedData).isEqualTo(
      GradlePluginsData(
        listOf(
          GradlePluginsData.PluginInfo(
            pluginClasses = listOf("my.plugin.pluginA", "my.plugin.pluginB"),
            name = "MyPlugin",
            pluginArtifact = GradlePluginsData.DependencyCoordinates("org.my", "gradle-plugin"),
            configurationCachingCompatibleFrom = Version.parse("1.0.0")
          )
        )
      )
    )
  }

  @Test
  fun testJsonParsedWithWrongVersionFormat() {
    val fileContent = """
    { "pluginsInfo": [{
        "name": "MyPlugin",
        "pluginClasses": ["my.plugin.pluginA","my.plugin.pluginB"],
        "pluginArtifact": "org.my:gradle-plugin",
        "configurationCachingCompatibleFrom": "N/A"
      }]}
    """.trimIndent()


    val parsedData = GradlePluginsData.loadFromJson(fileContent)
    Truth.assertThat(parsedData).isEqualTo(GradlePluginsData(listOf(
      GradlePluginsData.PluginInfo(
        pluginClasses = listOf("my.plugin.pluginA", "my.plugin.pluginB"),
        name = "MyPlugin",
        pluginArtifact = GradlePluginsData.DependencyCoordinates("org.my", "gradle-plugin"),
        configurationCachingCompatibleFrom = null
      )
    )))
  }
}