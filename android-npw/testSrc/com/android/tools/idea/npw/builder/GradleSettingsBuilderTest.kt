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
package com.android.tools.idea.npw.builder

import com.android.tools.idea.npw.builders.GradleSettingsBuilder
import java.net.URL
import java.net.URI
import org.junit.Test
import kotlin.test.assertEquals
import org.jetbrains.plugins.gradle.frameworkSupport.settingsScript.getFoojayPluginVersion

class GradleSettingsBuilderTest {

  @Test(expected = IllegalArgumentException::class)
  fun testBuildGradleSettingsWithProjectNameUsingBackslashResultsOnException() {
    GradleSettingsBuilder("\\", false) {}
  }

  @Test
  fun testBuildGradleSettingsWithJustProjectName() {
    val gradleSettings = GradleSettingsBuilder("test", false) {}.build()
    assertEquals("rootProject.name = \"test\"", gradleSettings)
  }

  @Test
  fun testBuildGroovyGradleSettings() {
    val gradleSettings = GradleSettingsBuilder("groovyProject", false) {
      withDependencyResolutionManagement(listOfUrls(
        "https://www.example.com/1",
      ))
      withFoojayPlugin()
      withPluginManager(listOfUrls(
        "https://www.example.com/2",
      ))
    }.build()

    val expectedGradleSettings = """
dependencyResolutionManagement {
  repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
  repositories {
    maven { url = uri("https://www.example.com/1") }
    google()
    mavenCentral()
  }
}
plugins {
    id 'org.gradle.toolchains.foojay-resolver-convention' version '${getFoojayPluginVersion()}'
}
pluginManagement {
  repositories {
    maven { url = uri("https://www.example.com/2") }
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
}

rootProject.name = "groovyProject"""".trimIndent()
    assertEquals(expectedGradleSettings, gradleSettings)
  }

  @Test
  fun testBuildKotlinGradleSettings() {
    val gradleSettings = GradleSettingsBuilder("kotlinProject", true) {
      withDependencyResolutionManagement(listOfUrls(
        "https://www.example.com/1",
        "https://www.example.com/2",
      ))
      withFoojayPlugin()
      withPluginManager(listOfUrls(
        "https://www.example.com/3",
        "https://www.example.com/4",
      ))
    }.build()

    val expectedGradleSettings = """
dependencyResolutionManagement {
  repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
  repositories {
    maven { url = uri("https://www.example.com/1") }
    maven { url = uri("https://www.example.com/2") }
    google()
    mavenCentral()
  }
}
plugins {
    id("org.gradle.toolchains.foojay-resolver-convention") version "${getFoojayPluginVersion()}"
}
pluginManagement {
  repositories {
    maven { url = uri("https://www.example.com/3") }
    maven { url = uri("https://www.example.com/4") }
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
}

rootProject.name = "kotlinProject"""".trimIndent()
    assertEquals(expectedGradleSettings, gradleSettings)
  }

  private fun listOfUrls(vararg urls: String): List<URL> = urls.map { URI.create(it).toURL() }
}