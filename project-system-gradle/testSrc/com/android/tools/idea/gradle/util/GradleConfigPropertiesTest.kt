/*
 * Copyright (C) 2023 The Android Open Source Project
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

import com.intellij.openapi.util.io.FileUtil
import com.intellij.testFramework.LightPlatformTestCase
import org.jetbrains.plugins.gradle.properties.GRADLE_CACHE_DIR_NAME
import org.jetbrains.plugins.gradle.properties.GRADLE_LOCAL_PROPERTIES_FILE_NAME
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.nio.file.Paths

class GradleConfigPropertiesTest: LightPlatformTestCase() {

  @JvmField
  @Rule
  var temporaryFolder = TemporaryFolder()

  override fun setUp() {
    super.setUp()
    temporaryFolder.create()
  }

  @Test
  fun testUndefinedProperties() {
    val properties = GradleConfigProperties(temporaryFolder.root)
    assertNull(properties.javaHome)
  }

  @Test
  fun testEmptyProperties() {
    val properties = GradleConfigProperties(temporaryFolder.root).apply {
      javaHome = File("")
      save()
    }
    assertNotNull(properties.javaHome)
    assertEmpty(properties.javaHome.toString())
  }

  @Test
  fun testCreateFileOnSave() {
    GradleConfigProperties(temporaryFolder.root).apply {
      javaHome = File("/test")
      save()
    }
    val propertiesFile = File(temporaryFolder.root, Paths.get(GRADLE_CACHE_DIR_NAME, GRADLE_LOCAL_PROPERTIES_FILE_NAME).toString())
    assertTrue(propertiesFile.isFile)
  }

  @Test
  fun testSetJavaHome() {
    val javaHome = File("/path/to/java/home")
    val properties = GradleConfigProperties(temporaryFolder.root).apply {
      this.javaHome = javaHome
      save()
    }
    assertNotNull(properties.javaHome)
    assertEquals(javaHome, properties.javaHome)
  }

  @Test
  fun testGetJavaHome() {
    val javaHome = File("/my/java/home")
    val propertiesFile = temporaryFolder.newFolder(GRADLE_CACHE_DIR_NAME).resolve(GRADLE_LOCAL_PROPERTIES_FILE_NAME)
    FileUtil.writeToFile(propertiesFile, "java.home=${FileUtil.toCanonicalPath(javaHome.path)}")

    val properties = GradleConfigProperties(temporaryFolder.root)
    assertEquals(javaHome, properties.javaHome)
  }
}