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
class EnforceUniquePackageNamesRefactoringProcessorTest : UpgradeGradleFileModelTestCase() {
  override val projectRule = AndroidProjectRule.onDisk()
  private val experimentalKey = "com.android.tools.agp.upgrade.enforceUniquePackageNames"
  private val propertyKey = "android.uniquePackageNames"

  @Test
  fun `Property file created when not present`() {
    val project = projectRule.project
    val processor = EnforceUniquePackageNameRefactoringProcessor(project, AgpVersion.parse("8.0.0"), AgpVersion.parse("9.0.0"))
    assertThat(project.findGradleProperties()).isNull()
    processor.run()
    assertThat(VfsUtilCore.loadText(project.findGradleProperties()!!.also { it.refresh(false, false) })).contains("$propertyKey=false")
  }

  @Test
  fun `Property added when not present`() {
    val project = projectRule.project
    projectRule.fixture.addFileToProject("gradle.properties", "")
    val processor = EnforceUniquePackageNameRefactoringProcessor(project, AgpVersion.parse("8.0.0"), AgpVersion.parse("9.0.0"))
    processor.run()
    assertThat(VfsUtilCore.loadText(project.findGradleProperties()!!.also { it.refresh(false, false) })).contains("$propertyKey=false")
  }

  @Test
  fun `Property kept if present and is false`() {
    val project = projectRule.project
    projectRule.fixture.addFileToProject("gradle.properties", "$propertyKey=false")
    val processor = EnforceUniquePackageNameRefactoringProcessor(project, AgpVersion.parse("8.0.0"), AgpVersion.parse("9.0.0"))
    processor.run()
    assertThat(VfsUtilCore.loadText(project.findGradleProperties()!!.also { it.refresh(false, false) })).contains("$propertyKey=false")
  }

  @Test
  fun `Property kept if present and is true`() {
    val project = projectRule.project
    projectRule.fixture.addFileToProject("gradle.properties", "$propertyKey=true")
    val processor = EnforceUniquePackageNameRefactoringProcessor(project, AgpVersion.parse("8.0.0"), AgpVersion.parse("9.0.0"))
    processor.run()
    assertThat(VfsUtilCore.loadText(project.findGradleProperties()!!.also { it.refresh(false, false) })).contains("$propertyKey=true")
  }

  @Test
  fun `Property is true and new key is added`() {
    val project = projectRule.project
    projectRule.fixture.addFileToProject("gradle.properties", "$experimentalKey=true")
    val processor = EnforceUniquePackageNameRefactoringProcessor(project, AgpVersion.parse("8.0.0"), AgpVersion.parse("9.0.0"))
    processor.run()
    val propertiesText = VfsUtilCore.loadText(project.findGradleProperties()!!.also { it.refresh(false, false) })
    assertThat(propertiesText).contains("$propertyKey=false")
    assertThat(propertiesText).contains("$experimentalKey=true")
  }

  @Test
  fun `Refactoring enabled for 9_0_0-alpha01`() {
    val project = projectRule.project
    val processor = EnforceUniquePackageNameRefactoringProcessor(project, AgpVersion.parse("8.0.0"), AgpVersion.parse("9.0.0-alpha01"))
    assertTrue(processor.isEnabled)
  }

  private fun Project.findGradleProperties(): VirtualFile? = guessProjectDir()?.findChild("gradle.properties")
}