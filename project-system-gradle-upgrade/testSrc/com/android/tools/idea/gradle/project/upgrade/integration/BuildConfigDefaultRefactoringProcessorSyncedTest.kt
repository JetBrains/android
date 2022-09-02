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

import com.android.ide.common.repository.GradleVersion
import com.android.testutils.junit4.OldAgpTest
import com.android.tools.idea.gradle.project.sync.snapshots.PreparedTestProject
import com.android.tools.idea.gradle.project.sync.snapshots.TestProject
import com.android.tools.idea.gradle.project.sync.snapshots.TestProjectDefinition.Companion.prepareTestProject
import com.android.tools.idea.gradle.project.upgrade.BuildConfigDefaultRefactoringProcessor
import com.android.tools.idea.gradle.project.upgrade.BuildConfigDefaultRefactoringProcessor.SourcesNotGenerated
import com.android.tools.idea.gradle.project.upgrade.BuildConfigDefaultRefactoringProcessorTest
import com.android.tools.idea.testing.AgpVersionSoftwareEnvironmentDescriptor
import com.android.tools.idea.testing.AndroidProjectRule
import com.android.tools.idea.testing.findAppModule
import com.android.tools.idea.testing.onEdt
import com.google.common.truth.Truth.assertThat
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.guessModuleDir
import com.intellij.openapi.vfs.VfsUtilCore
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.testFramework.RunsInEdt
import com.intellij.testFramework.UsefulTestCase
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
  val projectRule = AndroidProjectRule.withAndroidModels().onEdt()

  @Test
  fun testProjectWithoutGeneratedSources() {
    projectRule
      .prepareTestProject(TestProject.SIMPLE_APPLICATION, agpVersion = AgpVersionSoftwareEnvironmentDescriptor.AGP_70)
      .addBuildConfigUsingClass()
      .open { project ->
        val buildGradleVfsFile = project.findAppBuildGradle()
        val appBuildGradleText = VfsUtilCore.loadText(buildGradleVfsFile.also { it.refresh(false, false) })
        val processor = BuildConfigDefaultRefactoringProcessor(project, GradleVersion.parse("7.0.0"), GradleVersion.parse("8.0.0"))
        Assert.assertTrue(processor.isBlocked)
        UsefulTestCase.assertSize(1, processor.blockProcessorReasons())
        assertThat(processor.blockProcessorReasons()[0]).isInstanceOf(SourcesNotGenerated::class.java)
        val usages = processor.findUsages()
        UsefulTestCase.assertSize(0, usages)
        processor.run()
        Assert.assertEquals(appBuildGradleText, VfsUtilCore.loadText(buildGradleVfsFile.also { it.refresh(false, false) }))
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
        val processor = BuildConfigDefaultRefactoringProcessor(project, GradleVersion.parse("7.0.0"), GradleVersion.parse("8.0.0"))
        Assert.assertFalse(processor.isBlocked)
        val usages = processor.findUsages()
        UsefulTestCase.assertSize(0, usages)
        processor.run()
        Assert.assertEquals(appBuildGradleText, VfsUtilCore.loadText(buildGradleVfsFile.also { it.refresh(false, false) }))
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
        val processor = BuildConfigDefaultRefactoringProcessor(project, GradleVersion.parse("7.0.0"), GradleVersion.parse("8.0.0"))
        Assert.assertFalse(processor.isBlocked)
        val usages = processor.findUsages()
        UsefulTestCase.assertSize(1, usages)
        processor.run()
        Assert.assertTrue(VfsUtilCore.loadText(buildGradleVfsFile.also { it.refresh(false, false) }).contains("buildConfig true"))
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
    return this
  }

  private fun Project.findAppBuildGradle(): VirtualFile = findAppModule().guessModuleDir()!!.findChild("build.gradle")!!
}