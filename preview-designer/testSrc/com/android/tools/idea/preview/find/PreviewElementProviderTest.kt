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
package com.android.tools.idea.preview.find

import com.android.tools.idea.preview.TestPreviewElement
import com.android.tools.preview.PreviewElement
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.SimpleModificationTracker
import com.intellij.psi.PsiFile
import com.intellij.psi.SmartPsiElementPointer
import com.intellij.testFramework.ApplicationRule
import com.intellij.testFramework.LightVirtualFile
import kotlinx.coroutines.runBlocking
import org.junit.Assert
import org.junit.Rule
import org.junit.Test
import org.mockito.Mockito.mock
import org.mockito.kotlin.whenever

class PreviewElementProviderTest {

  @get:Rule val applicationRule = ApplicationRule()

  @Test
  fun testFilteredProvider() = runBlocking {
    val staticPreviewProvider =
      StaticPreviewProvider(
        listOf(TestPreviewElement("PreviewMethod1"), TestPreviewElement("PreviewMethod2"), TestPreviewElement("AMethod"))
      )

    var filterWord = "AM"
    val filtered = FilteredPreviewElementProvider(staticPreviewProvider) { !it.displaySettings.name.contains(filterWord) }

    Assert.assertEquals(3, staticPreviewProvider.previewElements().count())
    // The filtered provider contains all elements without the word internal
    Assert.assertEquals(listOf("PreviewMethod1", "PreviewMethod2"), filtered.previewElements().map { it.displaySettings.name }.toList())

    // Now remove all elements with the word Preview
    filterWord = "Preview"
    Assert.assertEquals("AMethod", filtered.previewElements().single().displaySettings.name)
  }

  @Test
  fun testMemoized() = runBlocking {
    var staticPreviewProvider =
      StaticPreviewProvider(
        listOf(TestPreviewElement("PreviewMethod1"), TestPreviewElement("PreviewMethod2"), TestPreviewElement("AMethod"))
      )

    val modificationTracker = SimpleModificationTracker()
    val memoized =
      MemoizedPreviewElementProvider(
        object : PreviewElementProvider<PreviewElement<Unit>> {
          override suspend fun previewElements() = staticPreviewProvider.previewElements()
        },
        modificationTracker,
      )

    // Before the first refresh, the list is empty
    Assert.assertEquals(3, memoized.previewElements().count())

    staticPreviewProvider = StaticPreviewProvider(listOf(TestPreviewElement("PreviewMethod1")))
    // Updated the source but did not "refresh" by changing the modification stamp
    Assert.assertEquals(3, memoized.previewElements().count())
    modificationTracker.incModificationCount()
    Assert.assertEquals(1, memoized.previewElements().count())
  }

  @Test
  fun testFileProvider() = runBlocking {
    val project = mock<Project>()
    val virtualFile = LightVirtualFile()
    val psiFile = mock<PsiFile>()
    whenever(psiFile.isValid).thenReturn(true)
    whenever(psiFile.virtualFile).thenReturn(virtualFile)
    val psiFilePointer = mock<SmartPsiElementPointer<PsiFile>>()
    whenever(psiFilePointer.project).thenReturn(project)
    whenever(psiFilePointer.element).thenReturn(psiFile)

    val previewElements = listOf(TestPreviewElement(), TestPreviewElement())
    val filePreviewElementFinder = mock<FilePreviewElementFinder<TestPreviewElement>>()
    whenever(filePreviewElementFinder.findPreviewElements(project, virtualFile)).thenReturn(previewElements)

    val provider = FilePreviewElementProvider(psiFilePointer, filePreviewElementFinder)
    Assert.assertEquals(previewElements, provider.previewElements().toList())
  }

  @Test
  fun testFileProviderInvalidFile() = runBlocking {
    val project = mock<Project>()
    val virtualFile = LightVirtualFile()
    val psiFile = mock<PsiFile>()
    whenever(psiFile.isValid).thenReturn(false)
    whenever(psiFile.virtualFile).thenReturn(virtualFile)
    val psiFilePointer = mock<SmartPsiElementPointer<PsiFile>>()
    whenever(psiFilePointer.project).thenReturn(project)
    whenever(psiFilePointer.element).thenReturn(psiFile)

    val previewElements = listOf(TestPreviewElement(), TestPreviewElement())
    val filePreviewElementFinder = mock<FilePreviewElementFinder<TestPreviewElement>>()
    whenever(filePreviewElementFinder.findPreviewElements(project, virtualFile)).thenReturn(previewElements)

    val provider = FilePreviewElementProvider(psiFilePointer, filePreviewElementFinder)
    Assert.assertEquals(0, provider.previewElements().count())
  }

  @Test
  fun testFileProviderNullElement() = runBlocking {
    val project = mock<Project>()
    val psiFilePointer = mock<SmartPsiElementPointer<PsiFile>>()
    whenever(psiFilePointer.project).thenReturn(project)
    whenever(psiFilePointer.element).thenReturn(null)

    val filePreviewElementFinder = mock<FilePreviewElementFinder<TestPreviewElement>>()

    val provider = FilePreviewElementProvider(psiFilePointer, filePreviewElementFinder)
    Assert.assertEquals(0, provider.previewElements().count())
  }
}
