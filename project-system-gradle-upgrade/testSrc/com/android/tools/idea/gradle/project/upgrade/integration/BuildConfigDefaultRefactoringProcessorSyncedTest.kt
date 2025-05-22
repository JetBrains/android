/*
 * Copyright (C) 2022 The Android Open Source Project
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
import com.android.tools.idea.gradle.project.upgrade.BuildConfigDefaultRefactoringProcessor
import com.android.tools.idea.gradle.project.upgrade.BuildConfigDefaultRefactoringProcessorTest
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
import com.intellij.testFramework.PlatformTestUtil
import com.intellij.testFramework.RunsInEdt
import com.intellij.testFramework.UsefulTestCase
import com.intellij.util.ThrowableRunnable
import org.junit.Assert
import org.junit.Rule
import org.junit.Test
import java.io.File

/**
 * Unlike many/most of the processors, [BuildConfigDefaultRefactoringProcessor] inspects (but does not modify) the project source code
 * to detect whether there are any users of the (generated) BuildConfig class.  This means that rather more of the project needs to be
 * set up than in the simpler cases of [AndroidProjectRule].  These tests are relatively heavy, so future testers of this processor should
 * prefer adding tests in [BuildConfigDefaultRefactoringProcessorTest].
 */
@OldAgpTest(gradleVersions = ["7.0.2"], agpVersions = ["7.0.0"])
@RunsInEdt
class BuildConfigDefaultRefactoringProcessorSyncedTest {

  @get:Rule
  val projectRule: IntegrationTestEnvironmentRule = AndroidProjectRule.withIntegrationTestEnvironment()

  @Test
  fun testProjectWithoutGeneratedSources() {
    projectRule
      .prepareTestProject(TestProject.SIMPLE_APPLICATION, agpVersion = AgpVersionSoftwareEnvironmentDescriptor.AGP_70)
      .addBuildConfigUsingClass()
      .open { project ->
        val buildGradleVfsFile = project.findAppBuildGradle()
        val appBuildGradleText = VfsUtilCore.loadText(buildGradleVfsFile.also { it.refresh(false, false) })
        val processor = BuildConfigDefaultRefactoringProcessor(project, AgpVersion.parse("7.0.0"), AgpVersion.parse("8.0.0"))
        Assert.assertFalse(processor.isBlocked)
        val usages = processor.findUsages()
        UsefulTestCase.assertSize(1, usages)
        processor.run()
        Assert.assertEquals(appBuildGradleText, VfsUtilCore.loadText(buildGradleVfsFile.also { it.refresh(false, false) }))
      }
  }

  @Test
  fun testProjectWithoutGeneratedSourcesWithFalseFlag() {
    PlatformTestUtil.withSystemProperty("idea.skip.indices.initialization", "false", ThrowableRunnable {
      projectRule
        .prepareTestProject(TestProject.SIMPLE_APPLICATION, agpVersion = AgpVersionSoftwareEnvironmentDescriptor.AGP_70)
        .addBuildConfigFlagInGradleProperties(false).addBuildConfigUsingClass()
        .open { project ->
          project.findGradleProperties().also { it.refresh(false, false) }
          val buildGradleVfsFile = project.findAppBuildGradle()
          val appBuildGradleText = VfsUtilCore.loadText(buildGradleVfsFile.also { it.refresh(false, false) })
          val gradlePropertiesText = VfsUtilCore.loadText(project.findGradleProperties().also { it.refresh(false, false) })
          val processor = BuildConfigDefaultRefactoringProcessor(project, AgpVersion.parse("7.0.0"), AgpVersion.parse("8.0.0"))
          Assert.assertFalse(processor.isBlocked)
          val usages = processor.findUsages()
          UsefulTestCase.assertSize(0, usages)
          processor.run()
          Assert.assertEquals(appBuildGradleText, VfsUtilCore.loadText(buildGradleVfsFile.also { it.refresh(false, false) }))
          Assert.assertEquals(gradlePropertiesText, VfsUtilCore.loadText(project.findGradleProperties().also { it.refresh(false, false) }))
        }
    })
  }

  @Test
  fun testProjectWithoutGeneratedSourcesWithTrueFlag() {
    PlatformTestUtil.withSystemProperty("idea.skip.indices.initialization", "false", ThrowableRunnable {
      projectRule
        .prepareTestProject(TestProject.SIMPLE_APPLICATION, agpVersion = AgpVersionSoftwareEnvironmentDescriptor.AGP_70)
        .addBuildConfigUsingClass().addBuildConfigFlagInGradleProperties(true)
        .open { project ->
          project.findGradleProperties().also { it.refresh(false, false) }
          val buildGradleVfsFile = project.findAppBuildGradle()
          val appBuildGradleText = VfsUtilCore.loadText(buildGradleVfsFile.also { it.refresh(false, false) })
          val gradlePropertiesText = VfsUtilCore.loadText(project.findGradleProperties().also { it.refresh(false, false) })
          val processor = BuildConfigDefaultRefactoringProcessor(project, AgpVersion.parse("7.0.0"), AgpVersion.parse("8.0.0"))
          Assert.assertFalse(processor.isBlocked)
          val usages = processor.findUsages()
          UsefulTestCase.assertSize(0, usages)
          processor.run()
          Assert.assertEquals(appBuildGradleText, VfsUtilCore.loadText(buildGradleVfsFile.also { it.refresh(false, false) }))
          Assert.assertEquals(gradlePropertiesText, VfsUtilCore.loadText(project.findGradleProperties().also { it.refresh(false, false) }))
        }
    })
  }

  @Test
  fun testProjectWithoutGeneratedSourcesWithFalseBuildFeature() {
    projectRule
      .prepareTestProject(TestProject.SIMPLE_APPLICATION, agpVersion = AgpVersionSoftwareEnvironmentDescriptor.AGP_70)
      .addBuildConfigUsingClass().patchBuildConfigFlagInBuildFeatures(false)
      .open { project ->
        val buildGradleVfsFile = project.findAppBuildGradle()
        val appBuildGradleText = VfsUtilCore.loadText(buildGradleVfsFile.also { it.refresh(false, false) })
        val processor = BuildConfigDefaultRefactoringProcessor(project, AgpVersion.parse("7.0.0"), AgpVersion.parse("8.0.0"))
        Assert.assertFalse(processor.isBlocked)
        val usages = processor.findUsages()
        UsefulTestCase.assertSize(1, usages)
        processor.run()
        Assert.assertEquals(appBuildGradleText, VfsUtilCore.loadText(buildGradleVfsFile.also { it.refresh(false, false) }))
        Assert.assertTrue(VfsUtilCore.loadText(project.findGradleProperties().also { it.refresh(false, false) })
                            .contains("android.defaults.buildfeatures.buildconfig=true"))
      }
  }

  @Test
  fun testProjectWithoutGeneratedSourcesWithTrueBuildFeature() {
    projectRule
      .prepareTestProject(TestProject.SIMPLE_APPLICATION, agpVersion = AgpVersionSoftwareEnvironmentDescriptor.AGP_70)
      .addBuildConfigUsingClass().patchBuildConfigFlagInBuildFeatures(true)
      .open { project ->
        val buildGradleVfsFile = project.findAppBuildGradle()
        val appBuildGradleText = VfsUtilCore.loadText(buildGradleVfsFile.also { it.refresh(false, false) })
        val processor = BuildConfigDefaultRefactoringProcessor(project, AgpVersion.parse("7.0.0"), AgpVersion.parse("8.0.0"))
        Assert.assertFalse(processor.isBlocked)
        val usages = processor.findUsages()
        UsefulTestCase.assertSize(1, usages)
        processor.run()
        Assert.assertEquals(appBuildGradleText, VfsUtilCore.loadText(buildGradleVfsFile.also { it.refresh(false, false) }))
        Assert.assertTrue(VfsUtilCore.loadText(project.findGradleProperties().also { it.refresh(false, false) })
                            .contains("android.defaults.buildfeatures.buildconfig=true"))
      }
  }

  @Test
  fun testProjectNotUsingBuildConfig() {
    projectRule
      .prepareTestProject(TestProject.SIMPLE_APPLICATION, agpVersion = AgpVersionSoftwareEnvironmentDescriptor.AGP_70)
      .addBuildConfigClass()
      .open { project ->
        val buildGradleVfsFile = project.findAppBuildGradle()
        val appBuildGradleText = VfsUtilCore.loadText(buildGradleVfsFile.also { it.refresh(false, false) })
        val processor = BuildConfigDefaultRefactoringProcessor(project, AgpVersion.parse("7.0.0"), AgpVersion.parse("8.0.0"))
        Assert.assertFalse(processor.isBlocked)
        val usages = processor.findUsages()
        UsefulTestCase.assertSize(1, usages)
        processor.run()
        Assert.assertEquals(appBuildGradleText, VfsUtilCore.loadText(buildGradleVfsFile.also { it.refresh(false, false) }))
        Assert.assertTrue(VfsUtilCore.loadText(project.findGradleProperties().also { it.refresh(false, false) })
                            .contains("android.defaults.buildfeatures.buildconfig=true"))
      }
  }

  @Test
  fun testProjectUsingBuildConfig() {
    projectRule
      .prepareTestProject(TestProject.SIMPLE_APPLICATION, agpVersion = AgpVersionSoftwareEnvironmentDescriptor.AGP_70)
      .addBuildConfigClass().addBuildConfigUsingClass()
      .open { project ->
        val buildGradleVfsFile = project.findAppBuildGradle()
        val appBuildGradleText = VfsUtilCore.loadText(buildGradleVfsFile.also { it.refresh(false, false) })
        Assert.assertFalse(appBuildGradleText.contains("buildConfig true"))
        val processor = BuildConfigDefaultRefactoringProcessor(project, AgpVersion.parse("7.0.0"), AgpVersion.parse("8.0.0"))
        Assert.assertFalse(processor.isBlocked)
        val usages = processor.findUsages()
        UsefulTestCase.assertSize(1, usages)
        processor.run()
        Assert.assertEquals(appBuildGradleText, VfsUtilCore.loadText(buildGradleVfsFile.also { it.refresh(false, false) }))
        Assert.assertTrue(VfsUtilCore.loadText(project.findGradleProperties().also { it.refresh(false, false) })
                            .contains("android.defaults.buildfeatures.buildconfig=true"))
      }
  }

  /**
   * The BuildConfig definition is normally generated (if configured) by build, not sync.  Rather than attempt to run a build, we can
   * verify that if we have a file with the appropriate contents in the appropriate location, the processor can resolve it.
   */
  private fun PreparedTestProject.addBuildConfigClass(): PreparedTestProject {
    val buildConfigDir = File(root, "app/build/generated/source/buildConfig/debug/google/simpleapplication")
    buildConfigDir
      .apply(File::mkdirs)
      .resolve("BuildConfig.java")
      .writeText(
        """
          package google.simpleapplication;

          public final class BuildConfig {
            public static final String BUILD_TYPE = "debug";
          }
        """.trimIndent())
    VfsUtil.markDirtyAndRefresh(false, true, true, buildConfigDir)
    return this
  }

  private fun PreparedTestProject.addBuildConfigUsingClass(): PreparedTestProject {
    val simpleApplicationJavaSrcDir = File(root, "app/src/main/java/google/simpleapplication")
    simpleApplicationJavaSrcDir
      .apply(File::mkdirs)
      .resolve("Foo.java")
      .writeText(
        """
          package google.simpleapplication;

          public class Foo {
            public String toString() {
              return "aFoo " + BuildConfig.BUILD_TYPE;
            }
          }
      """.trimIndent())
    VfsUtil.markDirtyAndRefresh(false, true, true, simpleApplicationJavaSrcDir)
    return this
  }

  private fun PreparedTestProject.addBuildConfigFlagInGradleProperties(flag: Boolean): PreparedTestProject {
    root.resolve("gradle.properties").run {
      appendText("android.defaults.buildfeatures.buildconfig=$flag\n")
      VfsUtil.markDirtyAndRefresh(false, false, false, this)
    }
    return this
  }

  private fun PreparedTestProject.patchBuildConfigFlagInBuildFeatures(flag: Boolean): PreparedTestProject {
    root.resolve("app/build.gradle").run {
      appendText("\nandroid.buildFeatures.buildConfig $flag\n")
      VfsUtil.markDirtyAndRefresh(false, false, false, this)
    }
    return this
  }

  private fun Project.findAppBuildGradle(): VirtualFile = findAppModule().guessModuleDir()!!.findChild("build.gradle")!!
  private fun Project.findGradleProperties(): VirtualFile = guessProjectDir()?.findChild("gradle.properties")!!
}