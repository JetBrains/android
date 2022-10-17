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

import com.android.ide.common.repository.AgpVersion
import com.android.testutils.MockitoKt.whenever
import com.android.tools.idea.testing.AndroidProjectRule
import com.google.common.truth.Truth.assertThat
import org.junit.Rule
import org.junit.Test
import org.mockito.Mockito.mock
import org.mockito.Mockito.verify
import org.mockito.Mockito.verifyNoInteractions

class RefactoringProcessorInstantiatorTest {
  @get:Rule
  val projectRule = AndroidProjectRule.withSdk()

  val project by lazy { projectRule.project }

  @Test
  fun testShowAndGetAgpUpgradeDialogAccepted() {
    projectRule.fixture.addFileToProject("build.gradle", """
      buildscript {
        dependencies {
          classpath 'com.android.tools.build:gradle:4.2.0'
        }
      }
    """.trimIndent())
    val refactoringProcessorInstantiator = RefactoringProcessorInstantiator()
    val processor = refactoringProcessorInstantiator.createProcessor(project, AgpVersion.parse("4.2.0"), AgpVersion.parse("7.0.0"))
    val cannotUpgradeDialog = mock(AgpUpgradeRefactoringProcessorCannotUpgradeDialog::class.java)
    val upgradeDialog = mock(AgpUpgradeRefactoringProcessorDialog::class.java)
    whenever(upgradeDialog.showAndGet()).thenReturn(true)
    val run = refactoringProcessorInstantiator.showAndGetAgpUpgradeDialog(processor, { cannotUpgradeDialog }, { _, _ -> upgradeDialog })
    assertThat(run).isTrue()
    verifyNoInteractions(cannotUpgradeDialog)
    verify(upgradeDialog).showAndGet()
  }

  @Test
  fun testShowAndGetAgpUpgradeDialogRefused() {
    projectRule.fixture.addFileToProject("build.gradle", """
      buildscript {
        dependencies {
          classpath 'com.android.tools.build:gradle:4.2.0'
        }
      }
    """.trimIndent())
    val refactoringProcessorInstantiator = RefactoringProcessorInstantiator()
    val processor = refactoringProcessorInstantiator.createProcessor(project, AgpVersion.parse("4.2.0"), AgpVersion.parse("7.0.0"))
    val cannotUpgradeDialog = mock(AgpUpgradeRefactoringProcessorCannotUpgradeDialog::class.java)
    val upgradeDialog = mock(AgpUpgradeRefactoringProcessorDialog::class.java)
    whenever(upgradeDialog.showAndGet()).thenReturn(false)
    val run = refactoringProcessorInstantiator.showAndGetAgpUpgradeDialog(processor, { cannotUpgradeDialog }, { _, _ -> upgradeDialog })
    assertThat(run).isFalse()
    verifyNoInteractions(cannotUpgradeDialog)
    verify(upgradeDialog).showAndGet()
  }

  @Test
  fun testShowAndGetAgpUpgradeDialogNoFiles() {
    val refactoringProcessorInstantiator = RefactoringProcessorInstantiator()
    val processor = refactoringProcessorInstantiator.createProcessor(project, AgpVersion.parse("4.2.0"), AgpVersion.parse("7.0.0"))
    val cannotUpgradeDialog = mock(AgpUpgradeRefactoringProcessorCannotUpgradeDialog::class.java)
    val upgradeDialog = mock(AgpUpgradeRefactoringProcessorDialog::class.java)
    refactoringProcessorInstantiator.showAndGetAgpUpgradeDialog(processor, { cannotUpgradeDialog }, { _, _ -> upgradeDialog })
    verify(cannotUpgradeDialog).show()
    verifyNoInteractions(upgradeDialog)
  }
}