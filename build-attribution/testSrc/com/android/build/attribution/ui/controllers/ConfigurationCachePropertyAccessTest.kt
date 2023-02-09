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

import com.android.build.attribution.data.BuildRequestHolder
import com.android.build.attribution.data.BuildInvocationType
import com.android.build.attribution.data.StudioProvidedInfo
import com.android.tools.idea.gradle.project.build.invoker.GradleBuildInvoker
import com.android.tools.idea.testing.AndroidProjectRule
import com.google.common.truth.Truth
import com.intellij.openapi.application.runWriteAction
import com.intellij.openapi.vfs.VfsUtil
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.testFramework.EdtRule
import com.intellij.testFramework.RunsInEdt
import org.jetbrains.android.refactoring.getProjectProperties
import org.junit.Assert
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.RuleChain
import org.junit.runner.RunWith
import org.junit.runners.Parameterized
import java.io.File

@RunsInEdt
class ConfigurationCachePropertyAccessTest {

  private val projectRule = AndroidProjectRule.inMemory()

  @get:Rule
  val ruleChain = RuleChain.outerRule(projectRule).around(EdtRule())

  private lateinit var gradlePropertiesFile: VirtualFile
  private lateinit var buildRequestHolder: BuildRequestHolder

  @Before
  fun setUp() {
    runWriteAction {
      gradlePropertiesFile = projectRule.fixture.tempDirFixture.createFile("gradle.properties")
      Assert.assertTrue(gradlePropertiesFile.isWritable)
    }
    buildRequestHolder = BuildRequestHolder(
      GradleBuildInvoker.Request.builder(projectRule.project, File(projectRule.fixture.tempDirPath), "assemble").build()
    )
  }

  @Test
  fun testPropertyStateReadWhenSetToTrue() {
    runWriteAction { VfsUtil.saveText(gradlePropertiesFile, "org.gradle.configuration-cache=true") }

    val info = StudioProvidedInfo.fromProject(projectRule.project, buildRequestHolder, BuildInvocationType.REGULAR_BUILD)
    Truth.assertThat(info.configurationCachingGradlePropertyState).isEqualTo("true")
  }

  @Test
  fun testPropertyStateWhenNotSet() {
    val info = StudioProvidedInfo.fromProject(projectRule.project, buildRequestHolder, BuildInvocationType.REGULAR_BUILD)
    Truth.assertThat(info.configurationCachingGradlePropertyState).isNull()
  }

  @Test
  fun testPropertyStateReadWhenSetToFalse() {
    runWriteAction { VfsUtil.saveText(gradlePropertiesFile, "org.gradle.configuration-cache=false") }

    val info = StudioProvidedInfo.fromProject(projectRule.project, buildRequestHolder, BuildInvocationType.REGULAR_BUILD)
    Truth.assertThat(info.configurationCachingGradlePropertyState).isEqualTo("false")
  }

  @Test
  fun testUnsafePropertyStateReadWhenSetToTrue() {
    runWriteAction { VfsUtil.saveText(gradlePropertiesFile, "org.gradle.unsafe.configuration-cache=true") }

    val info = StudioProvidedInfo.fromProject(projectRule.project, buildRequestHolder, BuildInvocationType.REGULAR_BUILD)
    Truth.assertThat(info.configurationCachingGradlePropertyState).isEqualTo("true")
  }

  @Test
  fun testUnsafePropertyStateReadWhenSetToFalse() {
    runWriteAction { VfsUtil.saveText(gradlePropertiesFile, "org.gradle.unsafe.configuration-cache=false") }

    val info = StudioProvidedInfo.fromProject(projectRule.project, buildRequestHolder, BuildInvocationType.REGULAR_BUILD)
    Truth.assertThat(info.configurationCachingGradlePropertyState).isEqualTo("false")
  }

  @Test
  fun testPropertyStateTakePrecedenceWhenBothSet() {
    runWriteAction { VfsUtil.saveText(gradlePropertiesFile, """
      |org.gradle.unsafe.configuration-cache=false
      |org.gradle.configuration-cache=true
    """.trimMargin()) }

    val info = StudioProvidedInfo.fromProject(projectRule.project, buildRequestHolder, BuildInvocationType.REGULAR_BUILD)
    Truth.assertThat(info.configurationCachingGradlePropertyState).isEqualTo("true")
  }
}

/**
 * Please note, in real life this action of adding a configuration cache property should not be suggested when this property
 * is already explicitly present in the properties (both true or false). Because of this we add tests for simple cases when
 * property is already present, but there is no real need to test all the possible combinations of old and new property listed in the file.
 */
@RunWith(Parameterized::class)
@RunsInEdt
class ConfigurationCacheTurningOnTest(private val useStableFeatureProperty: Boolean) {

  companion object {
    @JvmStatic
    @Parameterized.Parameters(name = "useStableFeatureProperty \"{0}\"")
    fun data() = arrayOf(false, true)
  }

  val propertyName = if (useStableFeatureProperty) "org.gradle.configuration-cache" else "org.gradle.unsafe.configuration-cache"
  private val projectRule = AndroidProjectRule.inMemory()

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
  fun testAddingPropertyWhenNotSet() {
    StudioProvidedInfo.turnOnConfigurationCacheInProperties(projectRule.project, useStableFeatureProperty)

    val propertiesText = projectRule.project.getProjectProperties(createIfNotExists = true)?.text
    Truth.assertThat(propertiesText).contains("$propertyName=true")
  }

  @Test
  fun testAddingPropertyWhenSetToFalse() {
    runWriteAction { VfsUtil.saveText(gradlePropertiesFile, "org.gradle.unsafe.configuration-cache=false") }

    StudioProvidedInfo.turnOnConfigurationCacheInProperties(projectRule.project, useStableFeatureProperty)

    val propertiesText = projectRule.project.getProjectProperties(createIfNotExists = true)?.text
    Truth.assertThat(propertiesText).contains("$propertyName=true")
    Truth.assertThat(propertiesText).doesNotContain("$propertyName=false")
  }

  @Test
  fun testAddingPropertyWhenSetToTrue() {
    runWriteAction { VfsUtil.saveText(gradlePropertiesFile, "$propertyName=true") }

    StudioProvidedInfo.turnOnConfigurationCacheInProperties(projectRule.project, useStableFeatureProperty)

    val propertiesText = projectRule.project.getProjectProperties(createIfNotExists = true)?.text
    Truth.assertThat(propertiesText).contains("$propertyName=true")
  }
}