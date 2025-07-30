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
package com.android.tools.idea.gradle.project.upgrade

import com.android.ide.common.repository.AgpVersion
import com.android.tools.idea.testing.AndroidModuleModelBuilder
import com.android.tools.idea.testing.AndroidProjectBuilder
import com.android.tools.idea.testing.AndroidProjectRule
import com.google.common.truth.Truth.assertThat
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.guessProjectDir
import com.intellij.openapi.vfs.VfsUtilCore
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.testFramework.RunsInEdt
import org.junit.Assert.assertTrue
import org.junit.Test

@RunsInEdt
class BuildTypesUnitTestRefactoringProcessorTest : UpgradeGradleFileModelTestCase() {
  override val projectRule = AndroidProjectRule.withAndroidModels(
    AndroidModuleModelBuilder(
      gradlePath = ":",
      selectedBuildVariant = "release",
      projectBuilder = AndroidProjectBuilder()
    )
  )

  @Test
  fun `Property file created when not present and release unit tests exists`() {
    val project = projectRule.project
    val processor = BuildTypesUnitTestDefaultRefactoringProcessor(project, AgpVersion.parse("8.0.0"), AgpVersion.parse("9.0.0"))
    assertThat(project.findGradleProperties()).isNull()
    processor.run()
    assertThat(VfsUtilCore.loadText(project.findGradleProperties()!!.also { it.refresh(false, false) })).contains(
      "android.onlyEnableUnitTestForTheTestedBuildType=false")
  }

  @Test
  fun `Property added when not present and release unit tests exists`() {
    val project = projectRule.project
    projectRule.fixture.addFileToProject("gradle.properties", "")
    val processor = BuildTypesUnitTestDefaultRefactoringProcessor(project, AgpVersion.parse("8.0.0"), AgpVersion.parse("9.0.0"))
    assertThat(VfsUtilCore.loadText(project.findGradleProperties()!!.also { it.refresh(false, false) })).doesNotContain(
      "android.onlyEnableUnitTestForTheTestedBuildType=false")
    processor.run()
    assertThat(VfsUtilCore.loadText(project.findGradleProperties()!!.also { it.refresh(false, false) })).contains(
      "android.onlyEnableUnitTestForTheTestedBuildType=false")
  }

  @Test
  fun `Property kept if present and is false`() {
    val project = projectRule.project
    projectRule.fixture.addFileToProject("gradle.properties", "android.onlyEnableUnitTestForTheTestedBuildType=false")
    val processor = BuildTypesUnitTestDefaultRefactoringProcessor(project, AgpVersion.parse("8.0.0"), AgpVersion.parse("9.0.0"))
    processor.run()
    assertThat(VfsUtilCore.loadText(project.findGradleProperties()!!.also { it.refresh(false, false) })).contains(
      "android.onlyEnableUnitTestForTheTestedBuildType=false")
  }

  @Test
  fun `Property kept if present and is true`() {
    val project = projectRule.project
    projectRule.fixture.addFileToProject("gradle.properties", "android.onlyEnableUnitTestForTheTestedBuildType=true")
    val processor = BuildTypesUnitTestDefaultRefactoringProcessor(project, AgpVersion.parse("8.0.0"), AgpVersion.parse("9.0.0"))
    processor.run()
    assertThat(VfsUtilCore.loadText(project.findGradleProperties()!!.also { it.refresh(false, false) })).contains(
      "android.onlyEnableUnitTestForTheTestedBuildType=true")
  }

  @Test
  fun `Refactoring enabled for 9_0_0-alpha01`() {
    val project = projectRule.project
    val processor = BuildTypesUnitTestDefaultRefactoringProcessor(project, AgpVersion.parse("8.0.0"), AgpVersion.parse("9.0.0-alpha01"))
    assertTrue(processor.isEnabled)
  }

  private fun Project.findGradleProperties(): VirtualFile? = guessProjectDir()?.findChild("gradle.properties")
}

@RunsInEdt
class BuildTypesUnitTestRefactoringProcessorNoOpTest : UpgradeGradleFileModelTestCase() {
  override val projectRule = AndroidProjectRule.withAndroidModel()

  @Test
  fun `Property not added when not present and release unit tests do not exist`() {
    val project = projectRule.project
    projectRule.fixture.addFileToProject("gradle.properties", "")
    val processor = BuildTypesUnitTestDefaultRefactoringProcessor(project, AgpVersion.parse("8.0.0"), AgpVersion.parse("9.0.0"))
    assertThat(VfsUtilCore.loadText(project.findGradleProperties()!!.also { it.refresh(false, false) })).doesNotContain(
      "android.onlyEnableUnitTestForTheTestedBuildType")
    processor.run()
    assertThat(VfsUtilCore.loadText(project.findGradleProperties()!!.also { it.refresh(false, false) })).doesNotContain(
      "android.onlyEnableUnitTestForTheTestedBuildType")
  }

  private fun Project.findGradleProperties(): VirtualFile? = guessProjectDir()?.findChild("gradle.properties")
}
