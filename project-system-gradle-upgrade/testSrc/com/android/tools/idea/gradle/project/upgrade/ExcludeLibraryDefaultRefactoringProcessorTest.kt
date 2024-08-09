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
package com.android.tools.idea.gradle.project.upgrade

import com.android.ide.common.repository.AgpVersion
import com.android.tools.idea.testing.AndroidProjectRule
import com.google.common.truth.Truth.assertThat
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.guessProjectDir
import com.intellij.openapi.vfs.VfsUtilCore
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.testFramework.RunsInEdt
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

@RunsInEdt
class ExcludeLibraryDefaultRefactoringProcessorTest : UpgradeGradleFileModelTestCase() {
  override val projectRule = AndroidProjectRule.onDisk()
  private val experimentalKey = "android.experimental.dependency.excludeLibraryComponentsFromConstraints"
  private val propertyKey = "android.dependency.excludeLibraryComponentsFromConstraints"

  @Test
  fun `Read More Url is correct`() {
    val project = projectRule.project
    val processor = ExcludeLibraryDefaultRefactoringProcessor(project, AgpVersion.parse("8.0.0"), AgpVersion.parse("9.0.0"))
    assertEquals("https://developer.android.com/r/tools/upgrade-assistant/exclude-library-constraints-default", processor.getReadMoreUrl())
  }

  @Test
  fun `Property file created when not present`() {
    val project = projectRule.project
    val processor = ExcludeLibraryDefaultRefactoringProcessor(project, AgpVersion.parse("8.0.0"), AgpVersion.parse("9.0.0"))
    assertThat(project.findGradleProperties()).isNull()
    processor.run()
    assertThat(VfsUtilCore.loadText(project.findGradleProperties()!!.also { it.refresh(false, false) })).contains("$propertyKey=false")
  }

  @Test
  fun `Property added when not present`() {
    val project = projectRule.project
    projectRule.fixture.addFileToProject("gradle.properties", "")
    val processor = ExcludeLibraryDefaultRefactoringProcessor(project, AgpVersion.parse("8.0.0"), AgpVersion.parse("9.0.0"))
    processor.run()
    assertThat(VfsUtilCore.loadText(project.findGradleProperties()!!.also { it.refresh(false, false) })).contains("$propertyKey=false")
  }

  @Test
  fun `Property kept if present and is false`() {
    val project = projectRule.project
    projectRule.fixture.addFileToProject("gradle.properties", "$propertyKey=false")
    val processor = ExcludeLibraryDefaultRefactoringProcessor(project, AgpVersion.parse("8.0.0"), AgpVersion.parse("9.0.0"))
    processor.run()
    assertThat(VfsUtilCore.loadText(project.findGradleProperties()!!.also { it.refresh(false, false) })).contains("$propertyKey=false")
  }

  @Test
  fun `Property kept if present and is true`() {
    val project = projectRule.project
    projectRule.fixture.addFileToProject("gradle.properties", "$propertyKey=true")
    val processor = ExcludeLibraryDefaultRefactoringProcessor(project, AgpVersion.parse("8.0.0"), AgpVersion.parse("9.0.0"))
    processor.run()
    assertThat(VfsUtilCore.loadText(project.findGradleProperties()!!.also { it.refresh(false, false) })).contains("$propertyKey=true")
  }

  @Test
  fun `Experimental property kept when is false and new key is added`() {
    val project = projectRule.project
    projectRule.fixture.addFileToProject("gradle.properties", "$experimentalKey=false")
    val processor = ExcludeLibraryDefaultRefactoringProcessor(project, AgpVersion.parse("8.0.0"), AgpVersion.parse("9.0.0"))
    processor.run()
    val propertiesText = VfsUtilCore.loadText(project.findGradleProperties()!!.also { it.refresh(false, false) })
    assertThat(propertiesText).contains("$propertyKey=false")
    assertThat(propertiesText).contains("$experimentalKey=false")
  }

  @Test
  fun `Experimental property kept when is true and new key is added`() {
    val project = projectRule.project
    projectRule.fixture.addFileToProject("gradle.properties", "$experimentalKey=true")
    val processor = ExcludeLibraryDefaultRefactoringProcessor(project, AgpVersion.parse("8.0.0"), AgpVersion.parse("9.0.0"))
    processor.run()
    val propertiesText = VfsUtilCore.loadText(project.findGradleProperties()!!.also { it.refresh(false, false) })
    assertThat(propertiesText).contains("$propertyKey=false")
    assertThat(propertiesText).contains("$experimentalKey=true")
  }

  @Test
  fun `Refactoring enabled for 9_0_0-alpha01`() {
    val project = projectRule.project
    val processor = ExcludeLibraryDefaultRefactoringProcessor(project, AgpVersion.parse("8.0.0"), AgpVersion.parse("9.0.0-alpha01"))
    assertTrue(processor.isEnabled)
  }

  private fun Project.findGradleProperties(): VirtualFile? = guessProjectDir()?.findChild("gradle.properties")
}