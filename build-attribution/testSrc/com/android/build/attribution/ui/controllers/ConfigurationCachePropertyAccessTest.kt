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
package com.android.build.attribution.ui.controllers

import com.android.build.attribution.data.StudioProvidedInfo
import com.android.tools.idea.testing.AndroidProjectRule
import com.google.common.truth.Truth
import com.intellij.openapi.application.runReadAction
import com.intellij.openapi.application.runWriteAction
import com.intellij.openapi.vfs.VfsUtil
import com.intellij.openapi.vfs.VfsUtilCore
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.testFramework.EdtRule
import com.intellij.testFramework.RunsInEdt
import org.junit.Assert
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.RuleChain

@RunsInEdt
class ConfigurationCachePropertyAccessTest {

  protected val projectRule = AndroidProjectRule.onDisk()

  @get:Rule
  val ruleChain = RuleChain.outerRule(projectRule).around(EdtRule())

  private lateinit var gradlePropertiesFile: VirtualFile

  @Before
  fun setUp() {
    runWriteAction {
      gradlePropertiesFile = projectRule.fixture.tempDirFixture.createFile("gradle.properties")
      Assert.assertTrue(gradlePropertiesFile.isWritable)
    }
  }

  @Test
  fun testPropertyStateReadWhenSetToTrue() {
    runWriteAction { VfsUtil.saveText(gradlePropertiesFile, "org.gradle.unsafe.configuration-cache=true") }

    val info = StudioProvidedInfo.fromProject(projectRule.project)
    Truth.assertThat(info.configurationCachingGradlePropertyState).isEqualTo("true")
  }

  @Test
  fun testPropertyStateWhenNotSet() {
    val info = StudioProvidedInfo.fromProject(projectRule.project)
    Truth.assertThat(info.configurationCachingGradlePropertyState).isNull()
  }

  @Test
  fun testPropertyStateReadWhenSetToFalse() {
    runWriteAction { VfsUtil.saveText(gradlePropertiesFile, "org.gradle.unsafe.configuration-cache=false") }

    val info = StudioProvidedInfo.fromProject(projectRule.project)
    Truth.assertThat(info.configurationCachingGradlePropertyState).isEqualTo("false")
  }

  @Test
  fun testAddingPropertyWhenNotSet() {
    Truth.assertThat(StudioProvidedInfo.fromProject(projectRule.project).configurationCachingGradlePropertyState).isNull()

    StudioProvidedInfo.turnOnConfigurationCacheInProperties(projectRule.project)

    Truth.assertThat(runReadAction { VfsUtilCore.loadText(gradlePropertiesFile) }).contains("org.gradle.unsafe.configuration-cache=true")
  }

  @Test
  fun testAddingPropertyWhenSetToFalse() {
    runWriteAction { VfsUtil.saveText(gradlePropertiesFile, "org.gradle.unsafe.configuration-cache=false") }

    StudioProvidedInfo.turnOnConfigurationCacheInProperties(projectRule.project)

    val propertiesText = runReadAction { VfsUtilCore.loadText(gradlePropertiesFile) }
    Truth.assertThat(propertiesText).contains("org.gradle.unsafe.configuration-cache=true")
    Truth.assertThat(propertiesText).doesNotContain("org.gradle.unsafe.configuration-cache=false")
  }

  @Test
  fun testAddingPropertyWhenSetToTrue() {
    runWriteAction { VfsUtil.saveText(gradlePropertiesFile, "org.gradle.unsafe.configuration-cache=true") }

    StudioProvidedInfo.turnOnConfigurationCacheInProperties(projectRule.project)

    Truth.assertThat(runReadAction { VfsUtilCore.loadText(gradlePropertiesFile) }).contains("org.gradle.unsafe.configuration-cache=true")
  }
}