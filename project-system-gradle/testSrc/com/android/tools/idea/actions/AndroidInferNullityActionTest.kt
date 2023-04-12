/*
 * Copyright (C) 2021 The Android Open Source Project
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
package com.android.tools.idea.actions

import com.android.ide.common.repository.GradleVersion
import com.android.testutils.MockitoKt.any
import com.android.testutils.MockitoKt.whenever
import com.android.tools.idea.projectsystem.GoogleMavenArtifactId
import com.android.tools.idea.projectsystem.PLATFORM_SUPPORT_LIBS
import com.android.tools.idea.projectsystem.TestProjectSystem
import com.android.tools.idea.testing.getTextForFile
import com.google.common.truth.Truth
import com.intellij.analysis.AnalysisScope
import com.intellij.codeInsight.NullableNotNullManager
import com.intellij.openapi.ui.Messages
import com.intellij.openapi.ui.TestDialog
import com.intellij.openapi.ui.TestDialogManager
import org.jetbrains.android.AndroidTestCase
import org.mockito.ArgumentCaptor
import org.mockito.Mockito
import org.mockito.Mockito.doNothing
import org.mockito.Mockito.mock
import org.mockito.Mockito.never
import org.mockito.Mockito.spy
import org.mockito.Mockito.times
import org.mockito.Mockito.verify

class AndroidInferNullityActionTest : AndroidTestCase() {

  private lateinit var myProjectSystem: TestProjectSystem
  private lateinit var myNullityManager: NullableNotNullManager

  @Throws(Exception::class)
  public override fun setUp() {
    super.setUp()
    myProjectSystem = TestProjectSystem(project, PLATFORM_SUPPORT_LIBS)
    myProjectSystem.useInTests()
    myFixture.addClass(
      """
import android.graphics.Color;

public class TestNullity {
    public Color myMethod() {
        Color color = null;
        return color;
    }

    public Color myMethod1() {
        Color color = new Color();
        return color;
    }
}
""".trimIndent())
    myNullityManager = NullableNotNullManager.getInstance(project)
  }

  fun testSupportLibAnnotations() {
    myProjectSystem.addDependency(GoogleMavenArtifactId.SUPPORT_ANNOTATIONS, myModule, GradleVersion(1, 1))
    runInferNullityAction()
    Truth.assertThat(myNullityManager.defaultNullable).isEqualTo("android.support.annotation.Nullable")
    Truth.assertThat(myNullityManager.defaultNotNull).isEqualTo("android.support.annotation.NonNull")
  }

  fun testAndroidxAnnotations() {
    myProjectSystem.addDependency(GoogleMavenArtifactId.ANDROIDX_SUPPORT_ANNOTATIONS, myModule, GradleVersion(1, 1))
    runInferNullityAction()
    Truth.assertThat(myNullityManager.defaultNullable).isEqualTo("androidx.annotation.Nullable")
    Truth.assertThat(myNullityManager.defaultNotNull).isEqualTo("androidx.annotation.NonNull")
  }

  fun testFoundCatalogDependency() {
    myFixture.addFileToProject("build.gradle", """
      dependencies{
      implementation libs.support
      }
    """.trimIndent())
    myFixture.addFileToProject("gradle/libs.versions.toml", """
      [libraries]
      support = "com.android.support:support-annotations:+"
    """.trimIndent())

    executeWithoutApplyRunnable {
      try {
        val action = AndroidInferNullityAnnotationAction()
        val scope = AnalysisScope(project)
        action.getAdditionalActionSettings(project, null)

        val testDialog: TestDialog = mock(TestDialog::class.java)
        TestDialogManager.setTestDialog(testDialog)

        action.analyze(project, scope)

        // system should not suggest adding dependency via dialog
        verify(testDialog, never()).show(any())
      }
      finally {
        TestDialogManager.setTestDialog(TestDialog.DEFAULT)
      }
    }
  }


 fun testAddDependencyWithCatalog() {
    myFixture.addFileToProject("build.gradle", """
      dependencies{
      }
    """.trimIndent())
    myFixture.addFileToProject("gradle/libs.versions.toml", """
      [libraries]
    """.trimIndent())

      try {
        val action = AndroidInferNullityAnnotationAction()
        val spyAction = spy(action)
        val scope = AnalysisScope(project)
        // stop before syncAndRestartAnalysis method
        doNothing().whenever(spyAction).syncAndRestartAnalysis(any(), any())

        TestDialogManager.setTestDialog(TestDialog.YES)

        spyAction.analyze(project, scope)

        val buildContent = project.getTextForFile("build.gradle")
        Truth.assertThat(buildContent).contains("implementation libs.support.annotation")
        val catalogContent = project.getTextForFile("gradle/libs.versions.toml")
        Truth.assertThat(catalogContent).contains("group = \"com.android.support\"")
      }
      finally {
        TestDialogManager.setTestDialog(TestDialog.DEFAULT)
      }
    }


  fun testAddAnnotationWithCatalogFull() {
    myFixture.addFileToProject("build.gradle", """
      dependencies{
         implementation libs.support.annotation
      }
    """.trimIndent())
    myFixture.addFileToProject("gradle/libs.versions.toml", """
      [libraries]
      support-annotation = "com.android.support:support-annotations:+"
    """.trimIndent())

    val action = AndroidInferNullityAnnotationAction()
    val scope = AnalysisScope(project)

    action.analyze(project, scope)

    val javaClass = project.getTextForFile("src/TestNullity.java")
    Truth.assertThat(javaClass).contains("@android.support.annotation.Nullable")
    Truth.assertThat(javaClass).contains("@android.support.annotation.NonNull")
  }

  fun testAddAnnotationNoCatalogFull() {
    TestDialogManager.setTestDialog(TestDialog.YES)
    myFixture.addFileToProject("build.gradle", """
      dependencies{
         implementation "com.android.support:support-annotations:+"
      }
    """.trimIndent())
    val action = AndroidInferNullityAnnotationAction()
    val scope = AnalysisScope(project)
    try {
      action.analyze(project, scope)
    }
    finally {
      TestDialogManager.setTestDialog(TestDialog.DEFAULT)
    }

    val javaClass = project.getTextForFile("src/TestNullity.java")
    Truth.assertThat(javaClass).contains("@android.support.annotation.NonNull")
    Truth.assertThat(javaClass).contains("@android.support.annotation.Nullable")
  }

  /**
   * ApplyRunnable contains all code/annotation changes. To isolate tests from this functionality
   * we can mock this method to not call existing business logic.
   */
  private fun executeWithoutApplyRunnable(f: () -> Unit) {
    Mockito.mockStatic(AndroidInferNullityAnnotationAction::class.java, Mockito.CALLS_REAL_METHODS).use { _ ->
      val runnable: Runnable = mock(Runnable::class.java)
      whenever(AndroidInferNullityAnnotationAction.applyRunnable(any(), any())).thenReturn(runnable)

      f.invoke()

      verify(runnable, times(1)).run()
    }
  }

  private fun runInferNullityAction() {
    val action = AndroidInferNullityAnnotationAction()
    val scope = AnalysisScope(project)
    action.getAdditionalActionSettings(project, null)

    runWithDialog(Messages.NO, "JetBrains annotations") {
      action.analyze(project, scope)
    }
  }

  /**
   * Utility method to run function and check whether it start dialog with message similar to given
   */
  private fun runWithDialog(dialogResponse: Int, dialogMessageContains: String, f: () -> Unit) {
    val argument: ArgumentCaptor<String> = ArgumentCaptor.forClass(String::class.java)
    val testDialog: TestDialog = mock(TestDialog::class.java)
    whenever(testDialog.show(any())).thenReturn(dialogResponse)
    TestDialogManager.setTestDialog(testDialog)

    try {
      f.invoke()
    }
    finally {
      TestDialogManager.setTestDialog(TestDialog.DEFAULT)
    }

    verify(testDialog).show(argument.capture())
    Truth.assertThat(argument.value).contains(dialogMessageContains)
  }
}