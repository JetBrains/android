/*
 * Copyright (C) 2026 The Android Open Source Project
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
package org.jetbrains.android.exportSignedPackage.runsGradleSigning

import com.android.mockito.kotlin.whenever
import com.android.tools.idea.projectsystem.getAndroidFacets
import com.android.tools.idea.testing.AndroidGradleProjectRule
import com.android.tools.idea.testing.TestProjectPaths
import com.android.tools.idea.testing.mockStatic
import com.google.common.truth.Truth.assertThat
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.progress.Task
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.DialogWrapper.OK_EXIT_CODE
import com.intellij.testFramework.EdtRule
import com.intellij.testFramework.RunsInEdt
import org.jetbrains.android.exportSignedPackage.ExportSignedPackageWizard
import org.jetbrains.android.facet.AndroidFacet
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify

@RunsInEdt
class ExportSignedPackageWizardTaskBehaviorTest {
  @get:Rule val projectRule = AndroidGradleProjectRule()
  @get:Rule val edtRule = EdtRule()

  private val project: Project
    get() = projectRule.project

  private lateinit var progressManager: ProgressManager
  private lateinit var wizard: TestExportSignedPackageWizard

  @Before
  fun setup() {
    projectRule.loadProject(TestProjectPaths.SIMPLE_APPLICATION)
    wizard = TestExportSignedPackageWizard(project, project.getAndroidFacets())
    progressManager = mock()
    val staticMockProgressManager = mockStatic<ProgressManager>(projectRule.fixture.testRootDisposable)
    staticMockProgressManager.whenever<ProgressManager> { ProgressManager.getInstance() }.thenAnswer { progressManager }
  }

  @After
  fun teardown() {
    wizard.close(OK_EXIT_CODE)
  }

  @Test
  fun testBuildDialogTypeCondition_Background() {
    wizard.setUploadToPlay(false)
    wizard.okAction()

    val taskCaptor = argumentCaptor<Task>()
    verify(progressManager).run(taskCaptor.capture())
    val task = taskCaptor.firstValue
    assertThat(task).isInstanceOf(Task.Backgroundable::class.java)
    assertThat(task.title).isEqualTo("Generating Signed APKs")
  }

  @Test
  fun testBuildDialogTypeCondition_Modal() {
    wizard.setUploadToPlay(true)
    wizard.okAction()

    val taskCaptor = argumentCaptor<Task>()
    verify(progressManager).run(taskCaptor.capture())
    val task = taskCaptor.firstValue
    assertThat(task).isInstanceOf(Task.Modal::class.java)
    assertThat(task.title).isEqualTo("Building and Signing...")
  }

  private class TestExportSignedPackageWizard(project: Project, facet: List<AndroidFacet>) : ExportSignedPackageWizard(project, facet) {
    init {
      setGradleOptions(listOf("app"))
    }

    override fun updateStep() = Unit

    fun okAction() = super.doOKAction()
  }
}
