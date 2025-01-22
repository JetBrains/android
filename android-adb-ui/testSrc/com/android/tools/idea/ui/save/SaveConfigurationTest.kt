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
package com.android.tools.idea.ui.save

import com.android.tools.idea.ui.save.SaveConfiguration.Companion.PROJECT_DIR_MACRO
import com.android.tools.idea.ui.save.SaveConfiguration.Companion.USER_HOME_MACRO
import com.google.common.truth.Truth.assertThat
import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.guessProjectDir
import com.intellij.openapi.util.SystemInfo
import com.intellij.testFramework.ProjectRule
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import java.io.File
import java.time.LocalDateTime
import java.time.ZoneId

/** Tests for [SaveConfiguration]. */
class SaveConfigurationTest {
  @get:Rule
  val projectRule = ProjectRule()

  private val project: Project
    get() = projectRule.project
  private val saveConfig: SaveConfiguration
    get() = project.service<SaveConfiguration>()
  private val projectDir
    get() = project.guessProjectDir()!!.toNioPath()
  private val normalizedProjectDir
    get() = projectDir.toString().replace(File.separatorChar, '/')
  private val timestamp = LocalDateTime.of(2025, 1, 21, 10, 22, 14).atZone(ZoneId.systemDefault()).toInstant()
  private lateinit var savedUserHome: String
  private lateinit var userHome: String

  @Before
  fun setUp() {
    savedUserHome = System.getProperty("user.home")
    // Change user home directory to make sure that it is not a child of the project directory.
    userHome = projectDir.resolveSibling("nonexistent_user_home").toString()
    System.setProperty("user.home", userHome)
  }

  @After
  fun tearDown() {
    System.setProperty("user.home", savedUserHome)
  }

  @Test
  fun testExpandFilenamePattern() {
    assertThat(saveConfig.expandFilenamePattern(PROJECT_DIR_MACRO, "screenshots/%Y%M%D_%H%m%S", "png", timestamp, 5))
        .isEqualTo("$normalizedProjectDir/screenshots/20250121_102214.png".toPlatformPath())
    assertThat(saveConfig.expandFilenamePattern("Pictures", "%p_%3d", "png", timestamp, 5))
        .isEqualTo("$userHome/Pictures/${project.name}_005.png".toPlatformPath())
    assertThat(saveConfig.expandFilenamePattern("$USER_HOME_MACRO/Screenshots", "%5d", "png", timestamp, 1))
        .isEqualTo("$userHome/Screenshots/00001.png".toPlatformPath())
  }

  @Test
  fun testGeneralizeSaveLocation() {
    assertThat(saveConfig.generalizeSaveLocation("$normalizedProjectDir/screenshots")).isEqualTo("$PROJECT_DIR_MACRO/screenshots")
    assertThat(saveConfig.generalizeSaveLocation(userHome)).isEqualTo(USER_HOME_MACRO)
    assertThat(saveConfig.generalizeSaveLocation("foo/bar")).isEqualTo("$USER_HOME_MACRO/foo/bar")
    val absPath = if (SystemInfo.isWindows) "C:/foo/bar" else "/foo/bar"
    assertThat(saveConfig.generalizeSaveLocation(absPath)).isEqualTo(absPath)
  }

  private fun String.toPlatformPath(): String =
      this.replace('/', File.separatorChar)
}
