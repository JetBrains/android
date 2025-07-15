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

import com.android.sdklib.AndroidApiLevel
import com.android.tools.adtui.swing.popup.JBPopupRule
import com.android.tools.idea.testing.AndroidProjectRule
import com.android.tools.idea.testing.ApplicationServiceRule
import com.google.common.truth.Truth.assertThat
import com.intellij.debugger.engine.PositionManagerImpl
import com.intellij.execution.filters.FileHyperlinkInfo
import com.intellij.lang.java.JavaLanguage
import com.intellij.openapi.fileEditor.FileNavigator
import com.intellij.openapi.fileEditor.OpenFileDescriptor
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.PsiFile
import com.intellij.psi.PsiFileFactory
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.testFramework.EdtRule
import com.intellij.testFramework.RuleChain
import com.intellij.testFramework.RunsInEdt
import kotlin.test.fail
import org.junit.Rule
import org.junit.Test

/** Tests for [SdkSourceRedirectLinkInfo] */
@RunsInEdt
class SdkSourceRedirectLinkInfoTest {
  private val androidProjectRule = AndroidProjectRule.withSdk()
  private val popupRule = JBPopupRule()

  private val fakeFileNavigator = FakeFileNavigator()

  @get:Rule
  val rule =
    RuleChain(
      androidProjectRule,
      popupRule,
      ApplicationServiceRule(FileNavigator::class.java, fakeFileNavigator),
      EdtRule(),
    )

  private val project
    get() = androidProjectRule.project

  @Test
  fun navigate_opensSdkFile() {
    val file = getSdkFile("android.view.View")
    val info =
      SdkSourceRedirectLinkInfo(
        project,
        listOf(file.virtualFile),
        OpenFileDescriptor(project, file.virtualFile, 10, 0),
        AndroidApiLevel(27),
      )

    info.navigate(project)

    assertThat(fakeFileNavigator.navigationRequests)
      .containsExactly(
        NavigationRequest(
          "sources/android-27/android/view/View.java",
          line = 10,
          offset = 366,
          requestFocus = true,
        )
      )
  }

  @Test
  fun navigate_opensNonSdkFile() {
    val file = getNonSdkFile("Foo.java")
    val info =
      SdkSourceRedirectLinkInfo(
        project,
        listOf(file.virtualFile),
        OpenFileDescriptor(project, file.virtualFile, 1, 0),
        apiLevel = AndroidApiLevel(27),
      )

    info.navigate(project)

    assertThat(fakeFileNavigator.navigationRequests)
      .containsExactly(NavigationRequest("/Foo.java", line = 1, offset = 7, requestFocus = true))
  }

  @Test
  fun navigate_multipleFiles_selectFile1() {
    val file1 = getNonSdkFile("Foo.java").virtualFile
    val file2 = getNonSdkFile("Bar.java").virtualFile
    val info =
      SdkSourceRedirectLinkInfo(
        project,
        listOf(file1, file2),
        OpenFileDescriptor(project, file1, 1, 0),
        AndroidApiLevel(27),
      )

    info.navigate(project)

    val popup = popupRule.fakePopupFactory.getPopup<VirtualFile>(0)
    assertThat(popup.items).containsExactly(file1, file2).inOrder()
    popup.selectItem(file1)
    assertThat(fakeFileNavigator.navigationRequests)
      .containsExactly(NavigationRequest("/Foo.java", line = 1, offset = 7, requestFocus = true))
  }

  @Test
  fun navigate_multipleFiles_selectFile2() {
    val file1 = getNonSdkFile("Foo.java").virtualFile
    val file2 = getNonSdkFile("Bar.java").virtualFile
    val info =
      SdkSourceRedirectLinkInfo(
        project,
        listOf(file1, file2),
        OpenFileDescriptor(project, file1, 1, 0),
        AndroidApiLevel(27),
      )

    info.navigate(project)

    val popup = popupRule.fakePopupFactory.getPopup<VirtualFile>(0)
    assertThat(popup.items).containsExactly(file1, file2).inOrder()
    popup.selectItem(file2)
    assertThat(fakeFileNavigator.navigationRequests)
      .containsExactly(NavigationRequest("/Bar.java", line = 1, offset = 7, requestFocus = true))
  }

  @Test
  fun navigate_descriptorWithOffset() {
    val file = getNonSdkFile("Foo.java")
    val info =
      SdkSourceRedirectLinkInfo(
        project,
        listOf(file.virtualFile),
        OpenFileDescriptor(project, file.virtualFile, 7),
        AndroidApiLevel(27),
      )

    info.navigate(project)

    assertThat(fakeFileNavigator.navigationRequests)
      .containsExactly(NavigationRequest("/Foo.java", line = -1, offset = 7, requestFocus = true))
  }

  /**
   * [SdkSourceRedirectLinkInfo] must be a [FileHyperlinkInfo] in order for
   * `LogcatOccurrenceNavigator` to work.
   */
  @Test
  fun isFileHyperlinkInfo() {
    assertThat(
        FileHyperlinkInfo::class.java.isAssignableFrom(SdkSourceRedirectLinkInfo::class.java)
      )
      .isTrue()
  }

  @Suppress("SameParameterValue")
  private fun getSdkFile(name: String): PsiFile {
    val psiClass =
      PositionManagerImpl.findClass(project, name, GlobalSearchScope.allScope(project), true)
    return psiClass?.containingFile ?: fail("Failed to get file for $name")
  }

  @Suppress("SameParameterValue")
  private fun getNonSdkFile(name: String): PsiFile =
    PsiFileFactory.getInstance(project)
      .createFileFromText(
        name,
        JavaLanguage.INSTANCE,
        """
      Line 1
      Line 2
      """
          .trimIndent(),
      )

  private data class NavigationRequest(
    val file: String,
    val line: Int,
    val offset: Int,
    val requestFocus: Boolean,
  )

  private class FakeFileNavigator : FileNavigator {
    val navigationRequests = mutableListOf<NavigationRequest>()

    override fun navigate(descriptor: OpenFileDescriptor, requestFocus: Boolean) {
      val path = descriptor.file.path
      val trim = path.lastIndexOf("sources/")
      navigationRequests.add(
        NavigationRequest(
          if (trim < 0) path else path.substring(trim),
          descriptor.line,
          descriptor.offset,
          requestFocus,
        )
      )
    }

    override fun navigateInEditor(descriptor: OpenFileDescriptor, requestFocus: Boolean): Boolean {
      navigate(descriptor, requestFocus)
      return true
    }
  }
}
