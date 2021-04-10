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

import com.android.build.attribution.data.GradlePluginsData
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
class PluginVersionDeclarationFinderTest {

  protected val projectRule = AndroidProjectRule.onDisk()

  @get:Rule
  val ruleChain = RuleChain.outerRule(projectRule).around(EdtRule())

  private lateinit var gradleBuildFile: VirtualFile

  @Before
  fun setUp() {
    runWriteAction {
      gradleBuildFile = projectRule.fixture.tempDirFixture.createFile("build.gradle")
      Assert.assertTrue(gradleBuildFile.isWritable)
    }
  }

  @Test
  fun testPluginDeclaredInDependencyBlock() {
    runWriteAction {
      VfsUtil.saveText(gradleBuildFile, """
      buildscript {
          repositories {}
          dependencies {
              classpath 'my.test:gradle:1.5.0'
          }
      }

      allprojects {
          repositories {}
      }
    """.trimIndent())
    }

    val buildGradleText = runReadAction { VfsUtilCore.loadText(gradleBuildFile) }

    PluginVersionDeclarationFinder(projectRule.project).findFileToOpen(
      GradlePluginsData.DependencyCoordinates("my.test", "gradle"),
      setOf("my.test.plugin")
    ).let {
      Truth.assertThat(it?.file).isEqualTo(gradleBuildFile)
      Truth.assertThat(it?.offset).isEqualTo(buildGradleText.indexOf("'my.test:gradle:1.5.0'"))
    }
  }

  @Test
  fun testPluginDeclaredInPluginsBlock() {
    runWriteAction {
      VfsUtil.saveText(gradleBuildFile, """
      buildscript {
          repositories {}
          dependencies {
          // Some other plugin defined here
              classpath 'my.test2:gradle:1.5.0'
          }
      }

      plugins {
      // Plugin of interest defined here
        id 'my.test.plugin' version '1.5.0' apply false
      }

      allprojects {
          repositories {}
      }
    """.trimIndent())
    }

    val buildGradleText = runReadAction { VfsUtilCore.loadText(gradleBuildFile) }

    PluginVersionDeclarationFinder(projectRule.project).findFileToOpen(
      GradlePluginsData.DependencyCoordinates("my.test", "gradle"),
      setOf("my.test.plugin")
    ).let {
      Truth.assertThat(it?.file).isEqualTo(gradleBuildFile)
      Truth.assertThat(it?.offset).isEqualTo(buildGradleText.indexOf("id 'my.test.plugin'"))
    }
  }

  @Test
  fun testPluginDeclarationNotFound() {
    runWriteAction {
      VfsUtil.saveText(gradleBuildFile, """
      buildscript {
          repositories {}
          dependencies {
          // Some other plugin defined here
              classpath 'my.test2:gradle:1.5.0'
          }
      }

      plugins {
      // Some other plugin defined here
        id 'my.test.plugin3' version '1.5.0' apply false
      }

      allprojects {
          repositories {}
      }
    """.trimIndent())
    }

    val buildGradleText = runReadAction { VfsUtilCore.loadText(gradleBuildFile) }

    PluginVersionDeclarationFinder(projectRule.project).findFileToOpen(
      GradlePluginsData.DependencyCoordinates("my.test", "gradle"),
      setOf("my.test.plugin")
    ).let {
      Truth.assertThat(it?.file).isEqualTo(gradleBuildFile)
      // Cursor sets at psi element of dependencies block.
      Truth.assertThat(it?.offset).isEqualTo(buildGradleText.indexOf("dependencies {") + "dependencies ".length)
    }
  }

  @Test
  fun testEvenDependenciesBlockNotFound() {
    runWriteAction {
      VfsUtil.saveText(gradleBuildFile, """
      buildscript {
          repositories {}
      }

      plugins {
      // Some other plugin defined here
        id 'my.test.plugin3' version '1.5.0' apply false
      }

      allprojects {
          repositories {}
      }
    """.trimIndent())
    }

    PluginVersionDeclarationFinder(projectRule.project).findFileToOpen(
      GradlePluginsData.DependencyCoordinates("my.test", "gradle"),
      setOf("my.test.plugin")
    ).let {
      Truth.assertThat(it?.file).isEqualTo(gradleBuildFile)
      // Just open root build.gradle in this case.
      Truth.assertThat(it?.offset).isEqualTo(-1)
    }
  }
}