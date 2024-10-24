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
import org.junit.Assert.assertTrue
import org.junit.Test

@RunsInEdt
class BlockAidlPropertyPresentRefactoringProcessorTest: UpgradeGradleFileModelTestCase() {
  override val projectRule = AndroidProjectRule.onDisk()
  private val propertyKey = "android.defaults.buildfeatures.aidl"

  @Test
  fun `Non blocked if property is not present`() {
    val processor = BlockAidlPropertyPresentRefactoringProcessor(project, AgpVersion.parse("8.0.0"), AgpVersion.parse("9.0.0"))
    assertThat(processor.isBlocked).isFalse()
  }

  @Test
  fun `Non blocked and property removed if it is false`() {
    projectRule.fixture.addFileToProject("gradle.properties", "$propertyKey=false")
    val processor = BlockAidlPropertyPresentRefactoringProcessor(project, AgpVersion.parse("8.0.0"), AgpVersion.parse("9.0.0"))
    assertThat(VfsUtilCore.loadText(project.findGradleProperties()!!.also { it.refresh(false, false) })).contains("$propertyKey=false")
    assertThat(processor.isBlocked).isFalse()
    processor.run()
    assertThat(VfsUtilCore.loadText(project.findGradleProperties()!!.also { it.refresh(false, false) })).doesNotContain(propertyKey)
  }

  @Test
  fun `Blocked if property present and is true`() {
    projectRule.fixture.addFileToProject("gradle.properties", "$propertyKey=true")
    verifyBlockedReasons("8.0.0", listOf("Property android.defaults.buildfeatures.aidl has been removed in 9.0.0-alpha01."))
  }

  @Test
  fun `Blocked if property is not present but upgrading from pre 8_0`() {
    verifyBlockedReasons("7.4.2", listOf("There have been changes in how AIDL is configured."))
  }

  @Test
  fun `Refactoring enabled for 9_0_0-alpha01`() {
    val project = projectRule.project
    val processor = BlockAidlPropertyPresentRefactoringProcessor(project, AgpVersion.parse("8.0.0"), AgpVersion.parse("9.0.0-alpha01"))
    assertTrue(processor.isEnabled)
  }

  @Test
  fun `Refactoring not enabled when current version 9_0_0`() {
    val project = projectRule.project
    val processor = BlockAidlPropertyPresentRefactoringProcessor(project, AgpVersion.parse("9.0.0"), AgpVersion.parse("10.0.0"))
    assertThat(processor.isEnabled).isFalse()
  }

  @Test
  fun `Refactoring not enabled when new version lower than 9_0_0-alpha01`() {
    val project = projectRule.project
    val processor = BlockAidlPropertyPresentRefactoringProcessor(project, AgpVersion.parse("8.0.0"), AgpVersion.parse("8.1000.0"))
    assertThat(processor.isEnabled).isFalse()
  }

  private fun verifyBlockedReasons(from: String, expectedReasons: List<String>) {
    val processor = BlockAidlPropertyPresentRefactoringProcessor(project, AgpVersion.parse(from), AgpVersion.parse("9.0.0"))
    assertThat(processor.isBlocked).isTrue()
    val blockedReasons = processor.blockProcessorReasons().map { it.shortDescription }
    assertThat(blockedReasons).isEqualTo(expectedReasons)
  }

  private fun Project.findGradleProperties(): VirtualFile? = guessProjectDir()?.findChild("gradle.properties")
}
