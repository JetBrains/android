/*
 * Copyright (C) 2019 The Android Open Source Project
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
package com.android.tools.idea.gradle.util

import com.android.SdkConstants.FN_BUILD_GRADLE
import com.android.SdkConstants.FN_BUILD_GRADLE_KTS
import com.android.SdkConstants.FN_SETTINGS_GRADLE
import com.android.SdkConstants.FN_SETTINGS_GRADLE_KTS
import com.google.common.truth.Truth.assertThat
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.testFramework.fixtures.BareTestFixtureTestCase
import com.intellij.testFramework.rules.TempDirectory
import org.junit.Rule
import org.junit.Test
import java.io.File

class GradleUtilBuildScriptTest : BareTestFixtureTestCase() {
  @get:Rule
  val tempDir = TempDirectory();

  private fun File.toVFile() = LocalFileSystem.getInstance().refreshAndFindFileByIoFile(this)!!

  @Test
  fun findGroovyBuildFile() {
    val groovyBuildFile = tempDir.newFile(FN_BUILD_GRADLE).toVFile()
    assertThat(groovyBuildFile).isNotNull()
    val foundBuildFile =
      GradleProjectSystemUtil.findGradleBuildFile(tempDir.root.toVFile())
    assertThat(foundBuildFile).isEqualTo(groovyBuildFile)
  }

  @Test
  fun findKotlinBuildFile() {
    val kotlinBuildFile = tempDir.newFile(FN_BUILD_GRADLE_KTS).toVFile()
    assertThat(kotlinBuildFile).isNotNull()
    val foundBuildFile =
      GradleProjectSystemUtil.findGradleBuildFile(tempDir.root.toVFile())
    assertThat(foundBuildFile).isEqualTo(kotlinBuildFile)
  }

  @Test
  fun findGroovySettingsFile() {
    val groovySettingsFile = tempDir.newFile(FN_SETTINGS_GRADLE).toVFile()
    assertThat(groovySettingsFile).isNotNull()
    val foundSettingsFile =
      GradleProjectSystemUtil.findGradleSettingsFile(tempDir.root.toVFile())
    assertThat(foundSettingsFile).isEqualTo(groovySettingsFile)
  }

  @Test
  fun findKotlinSettingsFile() {
    val kotlinSettingsFile = tempDir.newFile(FN_SETTINGS_GRADLE_KTS).toVFile()
    assertThat(kotlinSettingsFile).isNotNull()
    val foundSettingsFile =
      GradleProjectSystemUtil.findGradleSettingsFile(tempDir.root.toVFile())
    assertThat(foundSettingsFile).isEqualTo(kotlinSettingsFile)
  }

  @Test
  fun ignoreNoneDefaultBuildScripts() {
    tempDir.newFile("app.gradle").toVFile()
    tempDir.newFile("lib.gradle.kts")
    assertThat(GradleProjectSystemUtil.findGradleBuildFile(tempDir.root.toVFile())).isNull()
  }

  @Test
  fun ignoreDirectories() {
    tempDir.newDirectory(FN_BUILD_GRADLE)
    tempDir.newDirectory(FN_SETTINGS_GRADLE)
    tempDir.newDirectory(FN_BUILD_GRADLE_KTS)
    tempDir.newDirectory(FN_SETTINGS_GRADLE_KTS)
    assertThat(GradleProjectSystemUtil.findGradleBuildFile(tempDir.root.toVFile())).isNull()
    assertThat(GradleProjectSystemUtil.findGradleSettingsFile(tempDir.root.toVFile())).isNull()
  }

  @Test
  fun findGroovyBeforeKotlin() {
    val groovyBuildFile = tempDir.newFile(FN_BUILD_GRADLE).toVFile()
    assertThat(groovyBuildFile).isNotNull()
    val kotlinBuildFile = tempDir.newFile(FN_BUILD_GRADLE_KTS).toVFile()
    assertThat(kotlinBuildFile).isNotNull()
    val foundBuildFile =
      GradleProjectSystemUtil.findGradleBuildFile(tempDir.root.toVFile())
    assertThat(foundBuildFile).isEqualTo(groovyBuildFile)

    val groovySettingsFile = tempDir.newFile(FN_SETTINGS_GRADLE).toVFile()
    assertThat(groovyBuildFile).isNotNull()
    val kotlinSettingsFile = tempDir.newFile(FN_SETTINGS_GRADLE_KTS).toVFile()
    assertThat(kotlinSettingsFile).isNotNull()
    val foundSettingsFile =
      GradleProjectSystemUtil.findGradleSettingsFile(tempDir.root.toVFile())
    assertThat(foundSettingsFile).isEqualTo(groovySettingsFile)
  }
}