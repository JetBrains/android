/*
 * Copyright (C) 2023 The Android Open Source Project
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
package com.android.tools.idea.logcat.hyperlinks

import com.android.tools.adtui.swing.popup.JBPopupRule
import com.android.tools.idea.testing.AndroidProjectRule
import com.google.common.truth.Truth.assertThat
import com.intellij.debugger.engine.PositionManagerImpl
import com.intellij.lang.java.JavaLanguage
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.psi.PsiFile
import com.intellij.psi.PsiFileFactory
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.testFramework.EdtRule
import com.intellij.testFramework.RuleChain
import com.intellij.testFramework.RunsInEdt
import org.junit.Rule
import org.junit.Test
import kotlin.test.fail

/**
 * Tests for [SdkSourceRedirectLinkInfo]
 */
@RunsInEdt
class SdkSourceRedirectLinkInfoTest {
  private val androidProjectRule = AndroidProjectRule.withSdk()
  private val popupRule = JBPopupRule()

  @get:Rule
  val rule = RuleChain(androidProjectRule, popupRule, EdtRule())

  private val project get() = androidProjectRule.project

  private val fileEditorManager by lazy { FileEditorManager.getInstance(project) }

  @Test
  fun navigate_opensSdkFile() {
    val file = getSdkFile("android.view.View")
    val info = SdkSourceRedirectLinkInfo(project, listOf(file.virtualFile), 10, 27)

    info.navigate(project)

    val openFiles = fileEditorManager.openFiles
    assertThat(openFiles).asList().hasSize(1)
    assertThat(openFiles[0].path).endsWith("/android-27/android/view/View.java")
  }

  @Test
  fun navigate_opensNonSdkFile() {
    val file =  getNonSdkFile("Foo.java")
    val info = SdkSourceRedirectLinkInfo(project, listOf(file.virtualFile), 10, 27)

    info.navigate(project)

    assertThat(fileEditorManager.openFiles).asList().containsExactly(file.virtualFile)
  }

  @Test
  fun navigate_multipleFiles_selectFile1() {
    val file1 =  getNonSdkFile("Foo.java")
    val file2 =  getNonSdkFile("Bar.java")
    val info = SdkSourceRedirectLinkInfo(project, listOf(file1.virtualFile, file2.virtualFile), 10, 27)

    info.navigate(project)

    val popup = popupRule.fakePopupFactory.getPopup<PsiFile>(0)
    assertThat(popup.items).containsExactly(file1, file2).inOrder()
    popup.selectItem(file1)
    assertThat(fileEditorManager.openFiles).asList().containsExactly(file1.virtualFile)
  }

  @Test
  fun navigate_multipleFiles_selectFile2() {
    val file1 =  getNonSdkFile("Foo.java")
    val file2 =  getNonSdkFile("Bar.java")
    val info = SdkSourceRedirectLinkInfo(project, listOf(file1.virtualFile, file2.virtualFile), 10, 27)

    info.navigate(project)

    val popup = popupRule.fakePopupFactory.getPopup<PsiFile>(0)
    assertThat(popup.items).containsExactly(file1, file2).inOrder()
    popup.selectItem(file2)
    assertThat(fileEditorManager.openFiles).asList().containsExactly(file2.virtualFile)
  }


  @Suppress("SameParameterValue")
  private fun getSdkFile(name: String): PsiFile {
    val psiClass = PositionManagerImpl.findClass(project, name, GlobalSearchScope.allScope(project), true)
    return psiClass?.containingFile ?: fail("Failed to get file for $name")
  }

  @Suppress("SameParameterValue")
  private fun getNonSdkFile(name: String): PsiFile =
    PsiFileFactory.getInstance(project).createFileFromText(name, JavaLanguage.INSTANCE, "")

}