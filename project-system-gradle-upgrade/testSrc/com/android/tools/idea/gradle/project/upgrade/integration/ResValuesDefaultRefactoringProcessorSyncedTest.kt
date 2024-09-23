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

package com.android.tools.idea.gradle.project.upgrade.integration

import com.android.ide.common.repository.AgpVersion
import com.android.testutils.junit4.OldAgpTest
import com.android.tools.idea.gradle.project.sync.snapshots.PreparedTestProject
import com.android.tools.idea.gradle.project.sync.snapshots.TestProject
import com.android.tools.idea.gradle.project.sync.snapshots.TestProjectDefinition.Companion.prepareTestProject
import com.android.tools.idea.gradle.project.upgrade.ResValuesDefaultRefactoringProcessor
import com.android.tools.idea.testing.AgpVersionSoftwareEnvironmentDescriptor
import com.android.tools.idea.testing.AndroidProjectRule
import com.android.tools.idea.testing.IntegrationTestEnvironmentRule
import com.android.tools.idea.testing.findAppModule
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.guessModuleDir
import com.intellij.openapi.project.guessProjectDir
import com.intellij.openapi.vfs.VfsUtil
import com.intellij.openapi.vfs.VfsUtilCore
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.testFramework.RunsInEdt
import com.intellij.testFramework.UsefulTestCase
import org.junit.Assert
import org.junit.Rule
import org.junit.Test

@OldAgpTest(gradleVersions = ["7.0.2"], agpVersions = ["7.0.0"])
@RunsInEdt
class ResValuesDefaultRefactoringProcessorSyncedTest {

  @get:Rule
  val projectRule: IntegrationTestEnvironmentRule = AndroidProjectRule.withIntegrationTestEnvironment()

  @Test
  fun testProjectWithNoResValues() {
    projectRule
      .prepareTestProject(TestProject.SIMPLE_APPLICATION, agpVersion = AgpVersionSoftwareEnvironmentDescriptor.AGP_70)
      .open { project ->
        val buildGradleVfsFile = project.findAppBuildGradle()
        val appBuildGradleText = VfsUtilCore.loadText(buildGradleVfsFile.also { it.refresh(false, false) })
        VfsUtilCore.loadText(project.findGradleProperties().also { it.refresh(false, false) })
        val processor = ResValuesDefaultRefactoringProcessor(project, AgpVersion.parse("7.0.0"), AgpVersion.parse("9.0.0"))
        Assert.assertFalse(processor.isBlocked)
        val usages = processor.findUsages()
        UsefulTestCase.assertSize(1, usages)
        processor.run()
        Assert.assertEquals(appBuildGradleText, VfsUtilCore.loadText(buildGradleVfsFile.also { it.refresh(false, false) }))
        Assert.assertTrue(VfsUtilCore.loadText(project.findGradleProperties().also { it.refresh(false, false) })
          .contains("android.defaults.buildfeatures.resvalues=true"))
      }
  }

  @Test
  fun testProjectWithResValuesFalseInGradleProperties() {
    projectRule
      .prepareTestProject(TestProject.SIMPLE_APPLICATION, agpVersion = AgpVersionSoftwareEnvironmentDescriptor.AGP_70)
      .addResValuesFlagInGradleProperties(false)
      .open { project ->
        project.findGradleProperties().also { it.refresh(false, false) }
        val buildGradleVfsFile = project.findAppBuildGradle()
        val appBuildGradleText = VfsUtilCore.loadText(buildGradleVfsFile.also { it.refresh(false, false) })
        VfsUtilCore.loadText(project.findGradleProperties().also { it.refresh(false, false) })
        val processor = ResValuesDefaultRefactoringProcessor(project, AgpVersion.parse("7.0.0"), AgpVersion.parse("9.0.0"))
        Assert.assertFalse(processor.isBlocked)
        val usages = processor.findUsages()
        UsefulTestCase.assertSize(0, usages)
        processor.run()
        Assert.assertEquals(appBuildGradleText, VfsUtilCore.loadText(buildGradleVfsFile.also { it.refresh(false, false) }))
        Assert.assertTrue(VfsUtilCore.loadText(project.findGradleProperties().also { it.refresh(false, false) })
          .contains("android.defaults.buildfeatures.resvalues=false"))
      }
  }

  @Test
  fun testProjectWithResValuesTrueInGradleProperties() {
    projectRule
      .prepareTestProject(TestProject.SIMPLE_APPLICATION, agpVersion = AgpVersionSoftwareEnvironmentDescriptor.AGP_70)
      .addResValuesFlagInGradleProperties(true)
      .open { project ->
        project.findGradleProperties().also { it.refresh(false, false) }
        val buildGradleVfsFile = project.findAppBuildGradle()
        val appBuildGradleText = VfsUtilCore.loadText(buildGradleVfsFile.also { it.refresh(false, false) })
        VfsUtilCore.loadText(project.findGradleProperties().also { it.refresh(false, false) })
        val processor = ResValuesDefaultRefactoringProcessor(project, AgpVersion.parse("7.0.0"), AgpVersion.parse("9.0.0"))
        Assert.assertFalse(processor.isBlocked)
        val usages = processor.findUsages()
        UsefulTestCase.assertSize(0, usages)
        processor.run()
        Assert.assertEquals(appBuildGradleText, VfsUtilCore.loadText(buildGradleVfsFile.also { it.refresh(false, false) }))
        Assert.assertTrue(VfsUtilCore.loadText(project.findGradleProperties().also { it.refresh(false, false) })
          .contains("android.defaults.buildfeatures.resvalues=true"))
      }
  }

  private fun PreparedTestProject.addResValuesFlagInGradleProperties(flag: Boolean): PreparedTestProject {
    root.resolve("gradle.properties").run {
      appendText("android.defaults.buildfeatures.resvalues=$flag\n")
      VfsUtil.markDirtyAndRefresh(false, false, false, this)
    }
    return this
  }

  private fun Project.findAppBuildGradle(): VirtualFile = findAppModule().guessModuleDir()!!.findChild("build.gradle")!!
  private fun Project.findGradleProperties(): VirtualFile = guessProjectDir()?.findChild("gradle.properties")!!
}