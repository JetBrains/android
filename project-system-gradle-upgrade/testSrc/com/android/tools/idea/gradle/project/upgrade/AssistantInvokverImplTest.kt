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
package com.android.tools.idea.gradle.project.upgrade

import com.android.ide.common.repository.GradleVersion.AgpVersion
import com.android.testutils.MockitoKt.whenever
import com.android.tools.idea.testing.AndroidProjectRule
import com.google.common.truth.Truth.assertThat
import com.intellij.psi.PsiElement
import org.junit.Rule
import org.junit.Test
import org.mockito.Mockito.mock
import org.mockito.Mockito.verify

class AssistantInvokerImplTest {
  @get:Rule
  val projectRule = AndroidProjectRule.withSdk()

  val project by lazy { projectRule.project }

  @Test
  fun testDialogUpgradeAccepted() {
    projectRule.fixture.addFileToProject("build.gradle", """
      dependencies {
        compile 'com.android.tools.build:gradle:4.2.0'
      }
    """.trimIndent())
    val assistantInvoker = AssistantInvokerImpl()
    val dialog = mock(AgpUpgradeRefactoringProcessorWithCompileRuntimeSpecialCaseDialog::class.java)
    val element = mock(PsiElement::class.java)
    val processor = AgpUpgradeRefactoringProcessor(project, AgpVersion.parse("4.2.0"), AgpVersion.parse("8.0.0"))
    whenever(dialog.showAndGet()).thenReturn(true)
    val run = assistantInvoker.showAndGetDeprecatedConfigurationsUpgradeDialog(processor, element, { dialog })
    assertThat(run).isTrue()
    verify(dialog).showAndGet()
  }

  @Test
  fun testDialogUpgradeRefused() {
    projectRule.fixture.addFileToProject("build.gradle", """
      dependencies {
        compile 'com.android.tools.build:gradle:4.2.0'
      }
    """.trimIndent())
    val assistantInvoker = AssistantInvokerImpl()
    val dialog = mock(AgpUpgradeRefactoringProcessorWithCompileRuntimeSpecialCaseDialog::class.java)
    val element = mock(PsiElement::class.java)
    val processor = AgpUpgradeRefactoringProcessor(project, AgpVersion.parse("4.2.0"), AgpVersion.parse("8.0.0"))
    whenever(dialog.showAndGet()).thenReturn(false)
    val run = assistantInvoker.showAndGetDeprecatedConfigurationsUpgradeDialog(processor, element, { dialog })
    assertThat(run).isFalse()
    verify(dialog).showAndGet()
  }
}