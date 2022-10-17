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

import com.android.SdkConstants
import com.android.tools.idea.sdk.IdeSdks
import com.android.tools.idea.testing.AndroidGradleTests
import com.android.tools.idea.testing.AndroidProjectRule
import com.android.tools.idea.testing.GradleIntegrationTest
import com.android.tools.idea.testing.OpenPreparedProjectOptions
import com.android.tools.idea.testing.TestProjectPaths
import com.android.tools.idea.testing.findAppModule
import com.android.tools.idea.testing.findModule
import com.android.tools.idea.testing.onEdt
import com.android.tools.idea.testing.openPreparedProject
import com.android.tools.idea.testing.prepareGradleProject
import com.android.utils.FileUtils
import com.google.common.truth.Expect
import com.google.common.truth.Truth
import com.google.common.truth.Truth.assertThat
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.module.Module
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.io.FileUtil
import com.intellij.testFramework.RunsInEdt
import junit.framework.Assert.assertEquals
import junit.framework.Assert.assertFalse
import junit.framework.Assert.assertTrue
import org.jetbrains.plugins.gradle.service.GradleInstallationManager
import org.jetbrains.plugins.gradle.settings.GradleSettings
import org.junit.Rule
import org.junit.Test
import java.io.File
import java.nio.file.Paths

@RunsInEdt
class GradleUtilAndroidGradleTest : GradleIntegrationTest {

  @get:Rule
  val projectRule = AndroidProjectRule.withAndroidModels().onEdt()

  @get:Rule
  val expect = Expect.createAndEnableStackTrace()!!

  @Test
  fun testGetGradleBuildFileFromAppModule() {
    prepareGradleProject(TestProjectPaths.SIMPLE_APPLICATION, "p")
    openPreparedProject("p") { project ->
      verifyBuildFile(project, project.findAppModule(), "app", "build.gradle")
    }
  }

  @Test
  fun testGetGradleBuildFileFromProjectModule() {
    prepareGradleProject(TestProjectPaths.SIMPLE_APPLICATION, "p")
    openPreparedProject("p") { project ->
      verifyBuildFile(project, project.findModule(project.name), "build.gradle")
    }
  }

  @Test
  fun testHasKtsBuildFilesKtsBasedProject() {
    prepareGradleProject(TestProjectPaths.KOTLIN_GRADLE_DSL, "p")
    openPreparedProject("p", options = OpenPreparedProjectOptions(disableKtsRelatedIndexing = true)) { project ->
      assertTrue(GradleUtil.projectBuildFilesTypes(project).contains(SdkConstants.DOT_KTS))
    }
  }

  @Test
  fun testHasKtsBuildFilesGroovyBasedProject() {
    prepareGradleProject(TestProjectPaths.SIMPLE_APPLICATION, "p")
    openPreparedProject("p") { project ->
      assertFalse(GradleUtil.projectBuildFilesTypes(project).contains(SdkConstants.DOT_KTS))
    }
  }

  @Test
  fun testJdkPathFromProjectJava8() {
    val jdk8Path = AndroidGradleTests.getEmbeddedJdk8Path()
    verifyJdkPathFromProject(jdk8Path)
  }

  @Test
  fun testJdkPathFromProjectJavaCurrent() {
    verifyJdkPathFromProject(IdeSdks.getInstance().jdkPath!!.toAbsolutePath().toString())
  }

  @Test
  fun testUserGradlePropertiesFileDetectionForGradleHomeChangedInSettings() {
    prepareGradleProject(TestProjectPaths.SIMPLE_APPLICATION, "p")
    openPreparedProject("p") { project ->
      val gradleHome = Paths.get(getBaseTestPath(), "gradleHome").toString()
      ApplicationManager.getApplication().runWriteAction { GradleSettings.getInstance(project).serviceDirectoryPath = gradleHome }
      val userGradlePropertiesFile = GradleUtil.getUserGradlePropertiesFile(project)
      assertThat(userGradlePropertiesFile).isEqualTo(File(gradleHome, "gradle.properties"))
    }
  }

  private fun verifyBuildFile(project: Project, module: Module, vararg expectedPath: String) {
    val basePath = project.basePath
    assertThat(basePath).isNotNull()
    val fullPath = Paths.get(basePath, *expectedPath)
    val moduleBuildFile = GradleUtil.getGradleBuildFile(module)
    Truth.assertThat(moduleBuildFile).isNotNull()
    val modulePath = moduleBuildFile!!.path
    Truth.assertThat(modulePath).isNotNull()
    assertEquals(FileUtil.toSystemIndependentName(fullPath.toString()), modulePath)
  }

  private fun verifyJdkPathFromProject(javaPath: String) {
    prepareGradleProject(TestProjectPaths.SIMPLE_APPLICATION, "p")
    openPreparedProject("p") { project ->
      // Change value returned by IdeSdks.getJdkPath to Java 8
      ApplicationManager.getApplication().runWriteAction { IdeSdks.getInstance().setJdkPath(Paths.get(javaPath)) }
      val basePath = project.basePath
      assertThat(basePath).isNotNull()
      assertThat(basePath).isNotEmpty()
      val managerPath = GradleInstallationManager.getInstance().getGradleJvmPath(project, basePath!!)
      assertThat(managerPath).isNotNull()
      val settings = GradleUtil.getOrCreateGradleExecutionSettings(project)
      val settingsPath = settings.javaHome
      assertThat(settingsPath).isNotNull()
      assertThat(settingsPath).isNotEmpty()
      assertTrue(FileUtils.isSameFile(File(settingsPath), File(managerPath)))
    }
  }

  override fun getBaseTestPath(): String = projectRule.fixture.tempDirPath
  override fun getTestDataDirectoryWorkspaceRelativePath(): String = TestProjectPaths.TEST_DATA_PATH
  override fun getAdditionalRepos(): Collection<File> = listOf()
}