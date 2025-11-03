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
package com.android.tools.idea.testartifacts.instrumented.testsuite.api

import com.android.tools.idea.testing.AndroidGradleProjectRule
import com.android.tools.idea.testing.TestProjectPaths
import com.intellij.openapi.application.runReadAction
import com.intellij.openapi.module.Module
import com.intellij.openapi.module.ModuleUtilCore
import com.intellij.openapi.vfs.VfsUtil
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.PsiClass
import com.intellij.psi.PsiMethod
import com.intellij.testFramework.junit5.RunInEdt
import java.io.File
import org.assertj.core.api.Assertions.assertThat
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.mockito.Mockito.mock
import org.mockito.kotlin.whenever

@RunInEdt
class DefaultAndroidTestResultsPsiElementProviderTest {

  @get:Rule
  val projectRule = AndroidGradleProjectRule()

  private lateinit var applicationTestFile: VirtualFile
  private lateinit var androidTestModule: Module

  @Before
  fun setUp() {
    projectRule.loadProject(TestProjectPaths.TEST_ARTIFACTS_KOTLIN)

    applicationTestFile = requireNotNull(
      VfsUtil.findFileByIoFile(
        File(projectRule.project.basePath + "/app/src/androidTest/java/com/example/android/kotlin/ExampleInstrumentedTest.kt"), true)
    )
    androidTestModule = ModuleUtilCore.findModuleForFile(applicationTestFile, projectRule.project)!!
  }

  @Test
  fun isApplicable_returnsTrue_forAllRunConfigurations() {
    val provider = DefaultAndroidTestResultsPsiElementProvider()
    assertThat(provider.isApplicable(mock())).isTrue()
  }

  @Test
  fun getPsiElement_returnsMethodPsiElement_forMethodTestCase() {
    val androidTestResults: AndroidTestResults = mock()
    whenever(androidTestResults.methodName).thenReturn("useAppContext")
    whenever(androidTestResults.packageName).thenReturn("com.example.android.kotlin")
    whenever(androidTestResults.className).thenReturn("ExampleInstrumentedTest")

    val provider = DefaultAndroidTestResultsPsiElementProvider()
    val psiElement = runReadAction { provider.getPsiElement(projectRule.project, androidTestResults, androidTestModule) }

    assertThat(psiElement).isNotNull()
    assertThat(psiElement).isInstanceOf(PsiMethod::class.java)

    val psiMethod = psiElement as PsiMethod
    assertThat(psiMethod.name).isEqualTo("useAppContext")
    assertThat(psiMethod.containingClass?.qualifiedName).isEqualTo("com.example.android.kotlin.ExampleInstrumentedTest")
    assertThat(psiMethod.containingFile.virtualFile).isEqualTo(applicationTestFile)
  }

  @Test
  fun getPsiElement_returnsMethodPsiElement_forParameterizedMethodTestCase() {
    val androidTestResults: AndroidTestResults = mock()
    whenever(androidTestResults.methodName).thenReturn("exampleParameterizedTest[0]")
    whenever(androidTestResults.packageName).thenReturn("com.example.android.kotlin")
    whenever(androidTestResults.className).thenReturn("ParameterizedTest")

    val provider = DefaultAndroidTestResultsPsiElementProvider()
    val psiElement = runReadAction { provider.getPsiElement(projectRule.project, androidTestResults, androidTestModule) }

    assertThat(psiElement).isNotNull()
    assertThat(psiElement).isInstanceOf(PsiMethod::class.java)

    val psiMethod = psiElement as PsiMethod
    assertThat(psiMethod.name).isEqualTo("exampleParameterizedTest")

    val containingClass = runReadAction { psiMethod.containingClass?.name }
    assertThat(containingClass).isEqualTo("ParameterizedTest")

    val expectedContainingFile = requireNotNull(
      VfsUtil.findFileByIoFile(
        File(projectRule.project.basePath + "/app/src/androidTest/java/com/example/android/kotlin/ParameterizedTest.kt"), true)
    )
    assertThat(psiMethod.containingFile.virtualFile).isEqualTo(expectedContainingFile)
  }

  @Test
  fun getPsiElement_returnsClassPsiElement_whenMethodNameIsInvalid() {
    val androidTestResults: AndroidTestResults = mock()
    whenever(androidTestResults.methodName).thenReturn("")
    whenever(androidTestResults.packageName).thenReturn("com.example.android.kotlin")
    whenever(androidTestResults.className).thenReturn("ExampleInstrumentedTest")

    val provider = DefaultAndroidTestResultsPsiElementProvider()
    val psiElement = runReadAction { provider.getPsiElement(projectRule.project, androidTestResults, androidTestModule) }

    assertThat(psiElement).isNotNull()
    assertThat(psiElement).isInstanceOf(PsiClass::class.java)

    val psiClass = psiElement as PsiClass
    val className = runReadAction { psiClass.name }
    assertThat(className).isEqualTo("ExampleInstrumentedTest")

    assertThat(psiClass.containingFile.virtualFile).isEqualTo(applicationTestFile)
  }

  @Test
  fun getPsiElement_returnsNull_whenClassDoesNotExist() {
    val androidTestResults: AndroidTestResults = mock()
    whenever(androidTestResults.methodName).thenReturn("")
    whenever(androidTestResults.packageName).thenReturn("com.example.android.kotlin")
    whenever(androidTestResults.className).thenReturn("NonExistingClass")

    val provider = DefaultAndroidTestResultsPsiElementProvider()
    val psiElement = runReadAction { provider.getPsiElement(projectRule.project, androidTestResults, androidTestModule) }

    assertThat(psiElement).isNull()
  }
}