/*
 * Copyright (C) 2024 The Android Open Source Project
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
package com.android.tools.idea.gradle.dsl.model.android

import com.android.tools.idea.gradle.dcl.lang.ide.DeclarativeIdeSupport
import com.android.tools.idea.gradle.dsl.TestFileName
import com.android.tools.idea.gradle.dsl.model.GradleFileModelTestCase
import com.android.tools.idea.gradle.feature.flags.DeclarativeStudioSupport
import com.google.common.truth.Truth.assertThat
import org.jetbrains.annotations.SystemDependent
import org.junit.After
import org.junit.Before
import org.junit.Test
import java.io.File

class SoftwareTypesModelTest : GradleFileModelTestCase() {

  @Before
  override fun before() {
    isIrrelevantForKotlinScript("Defaults is only for declarative")
    isIrrelevantForGroovy("Defaults is only for declarative")
    DeclarativeIdeSupport.override(true)
    DeclarativeStudioSupport.override(true)
    super.before()
  }

  @After
  fun onAfter() {
    DeclarativeIdeSupport.clearOverride()
    DeclarativeStudioSupport.clearOverride()
  }

  @Test
  fun testAndroidAppAndLibraryBlocksWithStatements() {
    writeToSettingsFile(TestFile.ANDROID_BLOCKS_WITH_STATEMENTS)

    val buildModel = gradleDeclarativeSettingsModel
    val defaults = buildModel.defaults()
    assertNotNull(defaults)

    val androidLibrary = defaults.androidLibrary()
    assertNotNull(androidLibrary)
    assertEquals("defaultPublishConfig", "release", androidLibrary.defaultPublishConfig())

    val androidApp = defaults.androidApp()
    assertNotNull(androidApp)
    assertEquals("defaultPublishConfig", "debug", androidApp.defaultPublishConfig())
  }

  @Test
  fun testCreateAndroidAppAndLibraryBlocksFromScratch() {
    val buildModel = gradleDeclarativeSettingsModel
    val defaults = buildModel.defaults()
    assertNotNull(defaults)

   defaults.androidLibrary().let { library ->
      assertNotNull(library)
     library.buildToolsVersion().setValue(22)
      assertEquals("buildToolsVersion", 22, library.buildToolsVersion())
    }

    defaults.androidApp().let { app ->
      assertNotNull(app)
      app.buildToolsVersion().setValue(21)
      assertEquals("buildToolsVersion", 21, app.buildToolsVersion())
    }

    applyChanges(buildModel)
    buildModel.reparse()

    assertEquals("buildToolsVersion", 22, buildModel.defaults().androidLibrary().buildToolsVersion())
    assertEquals("buildToolsVersion", 21, buildModel.defaults().androidApp().buildToolsVersion())
  }

  @Test
  fun testRemoveAndroidAppAndLibraryBlocks() {
    writeToSettingsFile(TestFile.ANDROID_BLOCKS_WITH_STATEMENTS)
    val buildModel = gradleDeclarativeSettingsModel
    val defaults = buildModel.defaults()
    assertNotNull(defaults)

    defaults.androidLibrary().delete()
    defaults.androidApp().delete()

    applyChanges(buildModel)
    buildModel.reparse()

    // remove defaults if empty
    assertThat(loadBuildFile()).doesNotContain("defaults")
  }

  enum class TestFile(val path: @SystemDependent String) : TestFileName {
    ANDROID_BLOCKS_WITH_STATEMENTS("androidBlocksWithStatements"),
    ;

    override fun toFile(basePath: @SystemDependent String, extension: String): File {
      return super.toFile("$basePath/softwareTypesModel/$path", extension)
    }
  }

}