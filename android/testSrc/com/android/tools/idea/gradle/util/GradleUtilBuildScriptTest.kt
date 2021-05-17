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
import com.android.tools.idea.gradle.util.GradleUtil.isGradleScript
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.testFramework.fixtures.BareTestFixtureTestCase
import com.intellij.testFramework.rules.TempDirectory
import junit.framework.TestCase.assertEquals
import junit.framework.TestCase.assertFalse
import junit.framework.TestCase.assertNotNull
import junit.framework.TestCase.assertNull
import junit.framework.TestCase.assertTrue
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
    assertNotNull(groovyBuildFile)
    val foundBuildFile = GradleUtil.findGradleBuildFile(tempDir.root.toVFile())
    assertNotNull(foundBuildFile)
    assertEquals(groovyBuildFile, foundBuildFile)
  }

  @Test
  fun findKotlinBuildFile() {
    val kotlinBuildFile = tempDir.newFile(FN_BUILD_GRADLE_KTS).toVFile()
    assertNotNull(kotlinBuildFile)
    val foundBuildFile = GradleUtil.findGradleBuildFile(tempDir.root.toVFile())
    assertNotNull(foundBuildFile)
    assertEquals(kotlinBuildFile, foundBuildFile)
  }

  @Test
  fun findGroovySettingsFile() {
    val groovySettingsFile = tempDir.newFile(FN_SETTINGS_GRADLE).toVFile()
    assertNotNull(groovySettingsFile)
    val foundSettingsFile = GradleUtil.findGradleSettingsFile(tempDir.root.toVFile())
    assertNotNull(foundSettingsFile)
    assertEquals(groovySettingsFile, foundSettingsFile)
  }

  @Test
  fun findKotlinSettingsFile() {
    val kotlinSettingsFile = tempDir.newFile(FN_SETTINGS_GRADLE_KTS).toVFile()
    assertNotNull(kotlinSettingsFile)
    val foundSettingsFile = GradleUtil.findGradleSettingsFile(tempDir.root.toVFile())
    assertNotNull(foundSettingsFile)
    assertEquals(kotlinSettingsFile, foundSettingsFile)
  }

  @Test
  fun ignoreNoneDefaultBuildScripts() {
    tempDir.newFile("app.gradle").toVFile()
    tempDir.newFile("lib.gradle.kts")
    assertNull(GradleUtil.findGradleBuildFile(tempDir.root.toVFile()))
  }

  @Test
  fun ignoreDirectories() {
    tempDir.newDirectory(FN_BUILD_GRADLE)
    tempDir.newDirectory(FN_SETTINGS_GRADLE)
    tempDir.newDirectory(FN_BUILD_GRADLE_KTS)
    tempDir.newDirectory(FN_SETTINGS_GRADLE_KTS)
    assertNull(GradleUtil.findGradleBuildFile(tempDir.root.toVFile()))
    assertNull(GradleUtil.findGradleSettingsFile(tempDir.root.toVFile()))
  }

  @Test
  fun findGroovyBeforeKotlin() {
    val groovyBuildFile = tempDir.newFile(FN_BUILD_GRADLE).toVFile()
    assertNotNull(groovyBuildFile)
    val kotlinBuildFile = tempDir.newFile(FN_BUILD_GRADLE_KTS).toVFile()
    assertNotNull(kotlinBuildFile)
    val foundBuildFile = GradleUtil.findGradleBuildFile(tempDir.root.toVFile())
    assertNotNull(foundBuildFile)
    assertEquals(groovyBuildFile, foundBuildFile)

    val groovySettingsFile = tempDir.newFile(FN_SETTINGS_GRADLE).toVFile()
    assertNotNull(groovyBuildFile)
    val kotlinSettingsFile = tempDir.newFile(FN_SETTINGS_GRADLE_KTS).toVFile()
    assertNotNull(kotlinSettingsFile)
    val foundSettingsFile = GradleUtil.findGradleSettingsFile(tempDir.root.toVFile())
    assertNotNull(foundBuildFile)
    assertEquals(groovySettingsFile, foundSettingsFile)
  }

  @Test
  fun isGradleFile() {
    val groovyBuildFile = tempDir.newFile(FN_BUILD_GRADLE).toVFile()
    assertTrue(isGradleScript(groovyBuildFile))
    val kotlinBuildFile = tempDir.newFile(FN_BUILD_GRADLE_KTS).toVFile()
    assertTrue(isGradleScript(kotlinBuildFile))
    val groovySettingsFile = tempDir.newFile(FN_SETTINGS_GRADLE).toVFile()
    assertTrue(isGradleScript(groovySettingsFile))
    val kotlinSettingsFile = tempDir.newFile(FN_SETTINGS_GRADLE_KTS).toVFile()
    assertTrue(isGradleScript(kotlinSettingsFile))
    val renamedGroovyBuildFile = tempDir.newFile("somefile.gradle").toVFile()
    assertTrue(isGradleScript(renamedGroovyBuildFile))
    val renamedKotlinBuildFile = tempDir.newFile("somefile.gradle.kts").toVFile()
    assertTrue(isGradleScript(renamedKotlinBuildFile))
    val nonBuildFile = tempDir.newFile("someotherfilename.txt").toVFile()
    assertFalse(isGradleScript(nonBuildFile))
  }

  @Test
  fun isGradeFileDirectory() {
    val randomDir = tempDir.newDirectory("coolDir").toVFile()
    assertFalse(isGradleScript(randomDir))
    val buildGradleFolder = tempDir.newDirectory(FN_BUILD_GRADLE).toVFile()
    assertFalse(isGradleScript(buildGradleFolder))
    val settingsGradleFolder = tempDir.newDirectory(FN_SETTINGS_GRADLE_KTS).toVFile()
    assertFalse(isGradleScript(settingsGradleFolder))
  }
}