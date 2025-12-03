/*
 * Copyright (C) 2025 The Android Open Source Project
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
package com.android.tools.idea.npw.builders

import org.gradle.util.GradleVersion
import org.jetbrains.kotlin.idea.gradleCodeInsightCommon.FOOJAY_RESOLVER_CONVENTION_NAME
import org.jetbrains.plugins.gradle.frameworkSupport.settingsScript.getFoojayPluginVersion
import java.net.URL

class GradleSettingsBuilder(
  private val projectName: String,
  private val useGradleKts: Boolean,
  private val builderFunction: GradleSettings.() -> Unit
) {

  init {
    require(!projectName.contains("\\")) { "Backslash should not be present in the application title" }
  }

  fun build(): String {
    val settingsStringBuilder = StringBuilder()
    val gradleSettings = GradleSettings(settingsStringBuilder)
    builderFunction.invoke(gradleSettings)

    val escapedAppTitle = projectName.replace("$", "\\$")
    return settingsStringBuilder.apply {
      if (isNotEmpty()) {
        append("\n")
      }
      append("rootProject.name = \"$escapedAppTitle\"")
    }.toString().gradleSettingsToKtsIfKts(useGradleKts)
  }

  private fun String.gradleSettingsToKtsIfKts(isKts: Boolean): String = if (isKts) {
    split("\n").joinToString("\n") {
      it.replace("'", "\"")
        .replace("id ", "id(").replace(" version", ") version")
    }
  } else {
    this
  }
}

class GradleSettings(private val settingsBuilder: StringBuilder) {
  fun withPluginManager(repositoriesUrls: List<URL>) {
    settingsBuilder.appendLine("""
pluginManagement {
  repositories {${repositoriesUrls.toMavenUrlRepositories()}
    google {
      content {
        includeGroupByRegex("com\\.android.*")
        includeGroupByRegex("com\\.google.*")
        includeGroupByRegex("androidx.*")
      }
    }
    mavenCentral()
    gradlePluginPortal()
  }
}""".trimIndent())
  }

  fun withFoojayPlugin(gradleVersion: GradleVersion) {
    settingsBuilder.appendLine("""
plugins {
    id '$FOOJAY_RESOLVER_CONVENTION_NAME' version '${getFoojayPluginVersion(gradleVersion)}'
}""".trimIndent())
  }

  fun withDependencyResolutionManagement(repositoriesUrls: List<URL>) {
    settingsBuilder.appendLine("""
dependencyResolutionManagement {
  repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
  repositories {${repositoriesUrls.toMavenUrlRepositories()}
    google()
    mavenCentral()
  }
}""".trimIndent())
  }

  private fun List<URL>.toMavenUrlRepositories() = if(isEmpty()) "" else joinToString("") { "\n    maven { url = uri(\"${it}\") }" }
}
