/*
 * Copyright (C) 2019 The Android Open Source Project
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
package com.android.tools.idea.lang.databinding.reference

import com.android.tools.idea.databinding.DataBindingMode
import com.android.tools.idea.gradle.project.sync.snapshots.testProjectTemplateFromPath
import com.android.tools.idea.lang.databinding.LangDataBindingTestData.PROJECT_WITH_DATA_BINDING_ANDROID_X
import com.android.tools.idea.lang.databinding.LangDataBindingTestData.PROJECT_WITH_DATA_BINDING_SUPPORT
import com.android.tools.idea.lang.databinding.getTestDataPath
import com.android.tools.idea.testing.AndroidProjectRule
import com.google.common.truth.Truth.assertThat
import com.intellij.psi.PsiMethod
import com.intellij.psi.PsiParameter
import com.intellij.psi.impl.source.resolve.reference.impl.PsiMultiReference
import com.intellij.testFramework.RunsInEdt
import com.intellij.testFramework.fixtures.JavaCodeInsightTestFixture
import org.junit.Assert
import org.junit.Assume.assumeTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.Parameterized

/**
 * Tests for data binding elements that have references outside the module.
 */
@RunsInEdt
@RunWith(Parameterized::class)
class DataBindingExprReferenceContributorGradleTest(private val mode: DataBindingMode) {
  companion object {
    @JvmStatic
    @Parameterized.Parameters(name = "{0}")
    fun modes() = listOf(DataBindingMode.SUPPORT,
                         DataBindingMode.ANDROIDX)
  }

  @get:Rule
  val projectRule =
    AndroidProjectRule.testProject(
      testProjectTemplateFromPath(
        path = when (mode) {
          DataBindingMode.SUPPORT -> PROJECT_WITH_DATA_BINDING_SUPPORT
          else -> PROJECT_WITH_DATA_BINDING_ANDROID_X
        }, testDataPath = getTestDataPath()
      )
    )

  /**
   * Expose the underlying project rule fixture directly.
   *
   * We know that the underlying fixture is a [JavaCodeInsightTestFixture] because our
   * [AndroidProjectRule] is initialized to use the disk.
   *
   * In some cases, using the specific subclass provides us with additional methods we can
   * use to inspect the state of our parsed files. In other cases, it's just fewer characters
   * to type.
   */
  private val fixture: JavaCodeInsightTestFixture
    get() = projectRule.fixture

  private fun moveCaretToString(substring: String) {
    val editor = fixture.editor
    val text = editor.document.text
    val offset = text.indexOf(substring)
    Assert.assertTrue(offset > 0)
    fixture.editor.caretModel.moveToOffset(offset)
  }

  @Test
  fun dbReferencesLiveData() {
    val layoutFile = projectRule.project.baseDir.findFileByRelativePath("app/src/main/res/layout/activity_main.xml")!!
    fixture.configureFromExistingVirtualFile(layoutFile)

    moveCaretToString("getLiveDataString")
    // Call configureFromExistingVirtualFile again to set fixture.file to DbFile at the caret position.
    fixture.configureFromExistingVirtualFile(layoutFile)

    val javaStrValue = fixture.findClass("com.android.example.appwithdatabinding.SampleVo").findMethodsByName("getLiveDataString",
                                                                                                             false)[0].sourceElement!!
    val xmlStrValue = fixture.getReferenceAtCaretPosition()!!
    // If both of these are true, it means XML can reach Java and Java can reach XML
    assertThat(xmlStrValue.isReferenceTo(javaStrValue)).isTrue()
    assertThat(xmlStrValue.resolve()).isEqualTo(javaStrValue)
  }

  @Test
  fun dbReferencesStateFlow() {
    assumeTrue("StateFlow is only available in AndroidX projects", mode == DataBindingMode.ANDROIDX)

    val layoutFile = projectRule.project.baseDir.findFileByRelativePath("app/src/main/res/layout/activity_main.xml")!!
    fixture.configureFromExistingVirtualFile(layoutFile)

    moveCaretToString("getStateFlowString")
    // Call configureFromExistingVirtualFile again to set fixture.file to DbFile at the caret position.
    fixture.configureFromExistingVirtualFile(layoutFile)

    val javaStrValue = fixture.findClass("com.android.example.appwithdatabinding.SampleVo").findMethodsByName("getStateFlowString",
                                                                                                             false)[0].sourceElement!!
    val xmlStrValue = fixture.getReferenceAtCaretPosition()!!
    // If both of these are true, it means XML can reach Java and Java can reach XML
    assertThat(xmlStrValue.isReferenceTo(javaStrValue)).isTrue()
    assertThat(xmlStrValue.resolve()).isEqualTo(javaStrValue)
  }

  @Test
  fun dbReferencesObservableFields() {
    val layoutFile = projectRule.project.baseDir.findFileByRelativePath("app/src/main/res/layout/activity_main.xml")!!
    fixture.configureFromExistingVirtualFile(layoutFile)

    moveCaretToString("getObservableFieldString")
    // Call configureFromExistingVirtualFile again to set fixture.file to DbFile at the caret position.
    fixture.configureFromExistingVirtualFile(layoutFile)
    val javaStrValue = fixture.findClass("com.android.example.appwithdatabinding.SampleVo").findMethodsByName("getObservableFieldString",
                                                                                                             false)[0].sourceElement!!
    val xmlStrValue = fixture.getReferenceAtCaretPosition()!!
    // If both of these are true, it means XML can reach Java and Java can reach XML
    assertThat(xmlStrValue.isReferenceTo(javaStrValue)).isTrue()
    assertThat(xmlStrValue.resolve()).isEqualTo(javaStrValue)
  }

  @Test
  fun dbReferencesBindingMethods() {
    val layoutFile = projectRule.project.baseDir.findFileByRelativePath("app/src/main/res/layout/activity_main.xml")!!
    fixture.configureFromExistingVirtualFile(layoutFile)

    // Move to android:onClick="@{v|iew -> vo.saveView(view)}"/>
    moveCaretToString("iew -> vo.save")
    // Call configureFromExistingVirtualFile again to set fixture.file to DbFile at the caret position.
    fixture.configureFromExistingVirtualFile(layoutFile)
    val parameterReference = (fixture.getReferenceAtCaretPosition() as PsiMultiReference).references.first { it.resolve() is PsiParameter }

    val psiMethod = fixture.findClass("android.view.View.OnClickListener").findMethodsByName("onClick",
                                                                                             false)[0].sourceElement!! as PsiMethod
    // If both of these are true, it means XML can reach Java and Java can reach XML
    assertThat(parameterReference.isReferenceTo(psiMethod.parameterList.parameters[0]))
    assertThat(parameterReference.resolve()).isEqualTo(psiMethod.parameterList.parameters[0])
  }

  @Test
  fun dbReferencesBindingAdapters() {
    val layoutFile = projectRule.project.baseDir.findFileByRelativePath("app/src/main/res/layout/activity_main.xml")!!
    fixture.configureFromExistingVirtualFile(layoutFile)

    // Move to app:onClick2=="@{v|iew2 -> vo.saveView(view2)}"/>
    moveCaretToString("iew2 -> vo.save")
    // Call configureFromExistingVirtualFile again to set fixture.file to DbFile at the caret position.
    fixture.configureFromExistingVirtualFile(layoutFile)
    val parameterReference = (fixture.getReferenceAtCaretPosition() as PsiMultiReference).references.first { it.resolve() is PsiParameter }

    val psiMethod = fixture.findClass("android.view.View.OnClickListener").findMethodsByName("onClick",
                                                                                             false)[0].sourceElement!! as PsiMethod
    // If both of these are true, it means XML can reach Java and Java can reach XML
    assertThat(parameterReference.isReferenceTo(psiMethod.parameterList.parameters[0]))
    assertThat(parameterReference.resolve()).isEqualTo(psiMethod.parameterList.parameters[0])
  }

  @Test
  fun dbAttributeWithoutPrefixReferencesBindingAdapters() {
    val layoutFile = projectRule.project.baseDir.findFileByRelativePath("app/src/main/res/layout/activity_main.xml")!!
    fixture.configureFromExistingVirtualFile(layoutFile)

    // Move to onClick3="@{v|iew3 -> vo.saveView(view3)}"/>
    moveCaretToString("iew3 -> vo.save")
    // Call configureFromExistingVirtualFile again to set fixture.file to DbFile at the caret position.
    fixture.configureFromExistingVirtualFile(layoutFile)
    val parameterReference = (fixture.getReferenceAtCaretPosition() as PsiMultiReference).references.first { it.resolve() is PsiParameter }

    val psiMethod = fixture.findClass("android.view.View.OnClickListener").findMethodsByName("onClick",
                                                                                             false)[0].sourceElement!! as PsiMethod
    // If both of these are true, it means XML can reach Java and Java can reach XML
    assertThat(parameterReference.isReferenceTo(psiMethod.parameterList.parameters[0]))
    assertThat(parameterReference.resolve()).isEqualTo(psiMethod.parameterList.parameters[0])
  }

  @Test
  fun dbAttributeReferencesBindingAdapterMethod() {
    val layoutFile = projectRule.project.baseDir.findFileByRelativePath("app/src/main/res/layout/activity_main.xml")!!
    fixture.configureFromExistingVirtualFile(layoutFile)

    // Move to onClick3="@{v|iew3 -> vo.saveView(view3)}"/>
    moveCaretToString("Click3")
    // Call configureFromExistingVirtualFile again to set fixture.file to DbFile at the caret position.
    fixture.configureFromExistingVirtualFile(layoutFile)
    val parameterReference = (fixture.getReferenceAtCaretPosition() as PsiMultiReference).references.first { it.resolve() is PsiMethod }

    val psiMethod = fixture.findClass("com.android.example.appwithdatabinding.MyAdapter")
      .findMethodsByName("bindOnClick3", false)[0].sourceElement!! as PsiMethod
    // If both of these are true, it means XML can reach Java and Java can reach XML
    assertThat(parameterReference.isReferenceTo(psiMethod))
    assertThat(parameterReference.resolve()).isEqualTo(psiMethod)
  }
}