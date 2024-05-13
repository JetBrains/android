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
package com.android.tools.idea.gradle.project

import com.android.SdkConstants.FN_SETTINGS_GRADLE_KTS
import com.android.testutils.VirtualTimeScheduler
import com.android.tools.analytics.TestUsageTracker
import com.android.tools.analytics.UsageTracker
import com.android.tools.idea.gradle.dsl.utils.FN_SETTINGS_GRADLE
import com.google.wireless.android.sdk.stats.AndroidStudioEvent.EventKind.GRADLE_VERSION_CATALOG_DETECTOR
import com.google.wireless.android.sdk.stats.GradleVersionCatalogDetectorEvent.State.EXPLICIT
import com.google.wireless.android.sdk.stats.GradleVersionCatalogDetectorEvent.State.IMPLICIT
import com.google.wireless.android.sdk.stats.GradleVersionCatalogDetectorEvent.State.NONE
import com.google.wireless.android.sdk.stats.GradleVersionCatalogDetectorEvent.State.UNSUPPORTED
import com.intellij.openapi.application.runWriteAction
import com.intellij.testFramework.HeavyPlatformTestCase
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.Parameterized
import org.junit.runners.Parameterized.Parameter
import org.junit.runners.Parameterized.Parameters

@RunWith(Parameterized::class)
class GradleVersionCatalogDetectorTest: HeavyPlatformTestCase() {
  companion object {
    @JvmStatic
    @Parameters(name="{0}")
    fun parameters() = listOf(
      arrayOf(FN_SETTINGS_GRADLE),
      arrayOf(FN_SETTINGS_GRADLE_KTS),
    )
  }

  @Parameter
  lateinit var settingsFilename: String

  private val tracker = TestUsageTracker(VirtualTimeScheduler())

  @Before
  fun setUpTracker(): Unit = UsageTracker.setWriterForTest(tracker).let { }

  @After
  fun tearDownTracker(): Unit = UsageTracker.cleanAfterTesting()

  @Test fun testEmptyProject() {
    assertFalse(GradleVersionCatalogDetector.getInstance(project).isVersionCatalogProject)
    val event = tracker.usages.single { it.studioEvent.kind == GRADLE_VERSION_CATALOG_DETECTOR }
    assertEquals(UNSUPPORTED, event.studioEvent.gradleVersionCatalogDetectorEvent.state)
  }

  @Test fun testGradle6CallProject() {
    addWrapperFile("6.9")
    addSettingsFile(settingsFilename, enablePreview = true, callVersionCatalogs = true)
    assertFalse(GradleVersionCatalogDetector.getInstance(project).isVersionCatalogProject)
    val event = tracker.usages.single { it.studioEvent.kind == GRADLE_VERSION_CATALOG_DETECTOR }
    assertEquals(UNSUPPORTED, event.studioEvent.gradleVersionCatalogDetectorEvent.state)
  }

  @Test fun testGradle70PreviewCallProject() {
    addWrapperFile("7.0")
    addSettingsFile(settingsFilename, enablePreview = true, callVersionCatalogs = true)
    assertTrue(GradleVersionCatalogDetector.getInstance(project).isVersionCatalogProject)
    val event = tracker.usages.single { it.studioEvent.kind == GRADLE_VERSION_CATALOG_DETECTOR }
    assertEquals(EXPLICIT, event.studioEvent.gradleVersionCatalogDetectorEvent.state)
  }

  @Test fun testGradle70PreviewNoCallProject() {
    addWrapperFile("7.0")
    addSettingsFile(settingsFilename, enablePreview = true, callVersionCatalogs = false)
    assertFalse(GradleVersionCatalogDetector.getInstance(project).isVersionCatalogProject)
    val event = tracker.usages.single { it.studioEvent.kind == GRADLE_VERSION_CATALOG_DETECTOR }
    assertEquals(NONE, event.studioEvent.gradleVersionCatalogDetectorEvent.state)
  }

  @Test fun testGradle70PreviewLibsProject() {
    addWrapperFile("7.0")
    addSettingsFile(settingsFilename, enablePreview = true, callVersionCatalogs = false)
    addLibsTomlFile()
    assertTrue(GradleVersionCatalogDetector.getInstance(project).isVersionCatalogProject)
    val event = tracker.usages.single { it.studioEvent.kind == GRADLE_VERSION_CATALOG_DETECTOR }
    assertEquals(IMPLICIT, event.studioEvent.gradleVersionCatalogDetectorEvent.state)
  }

  // This would fail to configure, but let's test the logic anyway: since we have a Gradle version of 7.0 and
  // no preview enabled, this is not a Version Catalog project.
  @Test fun testGradle70NoPreviewCallProject() {
    addWrapperFile("7.0")
    addSettingsFile(settingsFilename, enablePreview = false, callVersionCatalogs = true)
    assertFalse(GradleVersionCatalogDetector.getInstance(project).isVersionCatalogProject)
    val event = tracker.usages.single { it.studioEvent.kind == GRADLE_VERSION_CATALOG_DETECTOR }
    assertEquals(NONE, event.studioEvent.gradleVersionCatalogDetectorEvent.state)
  }

  // Likewise, this will fail to configure: the presence of libs.versions.toml without the preview feature causes an early abort in
  // gradle.  Testing the logic anyway.
  @Test fun testGradle70NoPreviewLibsProject() {
    addWrapperFile("7.0")
    addSettingsFile(settingsFilename, enablePreview = false, callVersionCatalogs = false)
    addLibsTomlFile()
    assertFalse(GradleVersionCatalogDetector.getInstance(project).isVersionCatalogProject)
    val event = tracker.usages.single { it.studioEvent.kind == GRADLE_VERSION_CATALOG_DETECTOR }
    assertEquals(NONE, event.studioEvent.gradleVersionCatalogDetectorEvent.state)
  }

  @Test fun testGradle70NoPreviewNoCallProject() {
    addWrapperFile("7.0")
    addSettingsFile(settingsFilename, enablePreview = false, callVersionCatalogs = false)
    assertFalse(GradleVersionCatalogDetector.getInstance(project).isVersionCatalogProject)
    val event = tracker.usages.single { it.studioEvent.kind == GRADLE_VERSION_CATALOG_DETECTOR }
    assertEquals(NONE, event.studioEvent.gradleVersionCatalogDetectorEvent.state)
  }

  @Test fun testGradle74CallProject() {
    addWrapperFile("7.4")
    addSettingsFile(settingsFilename, enablePreview = false, callVersionCatalogs = true)
    assertTrue(GradleVersionCatalogDetector.getInstance(project).isVersionCatalogProject)
    val event = tracker.usages.single { it.studioEvent.kind == GRADLE_VERSION_CATALOG_DETECTOR }
    assertEquals(EXPLICIT, event.studioEvent.gradleVersionCatalogDetectorEvent.state)
  }

  @Test fun testGradle74NoCallProject() {
    addWrapperFile("7.4")
    addSettingsFile(settingsFilename, enablePreview = false, callVersionCatalogs = false)
    assertFalse(GradleVersionCatalogDetector.getInstance(project).isVersionCatalogProject)
    val event = tracker.usages.single { it.studioEvent.kind == GRADLE_VERSION_CATALOG_DETECTOR }
    assertEquals(NONE, event.studioEvent.gradleVersionCatalogDetectorEvent.state)
  }

  @Test fun testGradle74LibsProject() {
    addWrapperFile("7.4")
    addSettingsFile(settingsFilename, enablePreview = false, callVersionCatalogs = false)
    addLibsTomlFile()
    assertTrue(GradleVersionCatalogDetector.getInstance(project).isVersionCatalogProject)
    val event = tracker.usages.single { it.studioEvent.kind == GRADLE_VERSION_CATALOG_DETECTOR }
    assertEquals(IMPLICIT, event.studioEvent.gradleVersionCatalogDetectorEvent.state)
  }

  @Test fun testMultipleCallsSingleEvent() {
    assertFalse(GradleVersionCatalogDetector.getInstance(project).isVersionCatalogProject)
    assertFalse(GradleVersionCatalogDetector.getInstance(project).isVersionCatalogProject)
    val event = tracker.usages.single { it.studioEvent.kind == GRADLE_VERSION_CATALOG_DETECTOR }
    assertEquals(UNSUPPORTED, event.studioEvent.gradleVersionCatalogDetectorEvent.state)
  }

  @Test fun testSettingsDslNoEntry() {
    addWrapperFile("7.4")
    addSettingsFile(settingsFilename, enablePreview = false, callVersionCatalogs = true, dslEntry = false)
    assertFalse(GradleVersionCatalogDetector.getInstance(project).isSettingsCatalogEntry)
    val event = tracker.usages.single { it.studioEvent.kind == GRADLE_VERSION_CATALOG_DETECTOR }
    assertEquals(EXPLICIT, event.studioEvent.gradleVersionCatalogDetectorEvent.state)
  }

  @Test fun testSettingsDslEntry() {
    addWrapperFile("7.4")
    addSettingsFile(settingsFilename, enablePreview = false, callVersionCatalogs = true, dslEntry = true)
    assertTrue(GradleVersionCatalogDetector.getInstance(project).isSettingsCatalogEntry)
    val event = tracker.usages.single { it.studioEvent.kind == GRADLE_VERSION_CATALOG_DETECTOR }
    assertEquals(EXPLICIT, event.studioEvent.gradleVersionCatalogDetectorEvent.state)
  }

  private fun addWrapperFile(gradleVersion: String) {
    runWriteAction {
      val baseDir = getOrCreateProjectBaseDir()
      val propertiesFile = baseDir
        .run { findChild("gradle") ?: createChildDirectory(this, "gradle") }
        .run { findChild("wrapper") ?: createChildDirectory(this, "wrapper") }
        .findOrCreateChildData(this, "gradle-wrapper.properties")
      propertiesFile.setBinaryContent(
        """distributionUrl=https\://services.gradle.org/distributions/gradle-$gradleVersion-bin.zip""".toByteArray()
      )
    }
  }

  // For our purposes for now we can write the same text as valid Groovy and Kotlinscript
  private fun addSettingsFile(filename: String, enablePreview: Boolean, callVersionCatalogs: Boolean, dslEntry: Boolean = false) {
    if (dslEntry) assertTrue(callVersionCatalogs)
    runWriteAction {
      val baseDir = getOrCreateProjectBaseDir()
      val settingsFile = baseDir.findOrCreateChildData(this, filename)
      settingsFile.setBinaryContent(
        """${if (enablePreview) """enableFeaturePreview("VERSION_CATALOGS")""" else ""}
        |${
          if (callVersionCatalogs) "" +
                                   "dependencyResolutionManagement {\n" +
                                   "  versionCatalogs {\n" +
                                   "    getByName(\"libs\") {\n" +
                                   (if (dslEntry) "      library(\"foo\", \"com.example:example:1.2.3\")\n" else "") +
                                   "    }\n" +
                                   "  }\n" +
                                   "}\n"
          else ""
        }
      """.trimMargin().toByteArray()
      )
    }
  }

  private fun addLibsTomlFile() {
    runWriteAction {
      val baseDir = getOrCreateProjectBaseDir()
      val tomlFile = baseDir
        .run { findChild("gradle") ?: createChildDirectory(this, "gradle") }
        .findOrCreateChildData(this, "libs.versions.toml")
      tomlFile.setBinaryContent("".toByteArray())
    }
  }
}