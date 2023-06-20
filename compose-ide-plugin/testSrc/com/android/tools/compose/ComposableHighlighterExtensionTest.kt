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
package com.android.tools.compose

import androidx.compose.compiler.plugins.kotlin.ComposeFqNames
import com.android.testutils.MockitoKt.mock
import com.android.testutils.MockitoKt.whenever
import com.android.tools.idea.projectsystem.AndroidModuleSystem
import com.android.tools.idea.projectsystem.AndroidProjectSystem
import com.android.tools.idea.projectsystem.ProjectSystemService
import com.android.tools.idea.testing.AndroidProjectRule
import com.google.common.truth.Truth.assertThat
import com.intellij.openapi.module.ModuleUtilCore
import com.intellij.openapi.roots.ProjectFileIndex
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import org.jetbrains.kotlin.descriptors.CallableDescriptor
import org.jetbrains.kotlin.descriptors.DeclarationDescriptor
import org.jetbrains.kotlin.descriptors.annotations.AnnotationDescriptor
import org.jetbrains.kotlin.descriptors.annotations.Annotations
import org.jetbrains.kotlin.resolve.calls.model.ResolvedCall
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.JUnit4

@RunWith(JUnit4::class)
class ComposableHighlighterExtensionTest {
  @get:Rule
  var projectRule = AndroidProjectRule.inMemory()

  private val highlighter = ComposableHighlighterExtension()

  private val mockDescriptor: DeclarationDescriptor = mock()
  private val mockElement: PsiElement = mock()
  private val mockResolvedCall: ResolvedCall<*> = mock()

  // State variable toggles
  private var isComposableInvocation = false
  private var isInLibrarySource = false
  private var moduleUsesCompose = false

  @Before
  fun setup() {
    val mockAnnotations: Annotations = mock()
    val mockCandidateDescriptor: CallableDescriptor = mock()
    val mockModuleSystem: AndroidModuleSystem = mock()

    // Wire up AndroidModuleSystem
    val mockProjectSystemService = projectRule.mockProjectService(ProjectSystemService::class.java)
    val mockProjectSystem = mock<AndroidProjectSystem>()
    whenever(mockProjectSystemService.projectSystem).thenReturn(mockProjectSystem)
    whenever(mockProjectSystem.getModuleSystem(projectRule.module)).thenReturn(mockModuleSystem)

    // Setup required PsiElement properties
    val mockProjectFileIndex = projectRule.mockProjectService(ProjectFileIndex::class.java)
    val mockContainingFile: PsiFile = mock()
    val mockVirtualFile: VirtualFile = mock()
    whenever(mockElement.containingFile).thenReturn(mockContainingFile)
    whenever(mockElement.project).thenReturn(projectRule.project)
    whenever(mockContainingFile.isValid).thenReturn(true)
    whenever(mockContainingFile.project).thenReturn(projectRule.project)
    whenever(mockContainingFile.virtualFile).thenReturn(mockVirtualFile)
    whenever(mockContainingFile.getUserData(ModuleUtilCore.KEY_MODULE)).thenReturn(projectRule.module)

    // Setup mocks to return whether the function is composable
    whenever(mockProjectFileIndex.isInLibrarySource(mockVirtualFile)).thenAnswer { isInLibrarySource }

    // Setup mocks to return whether the function is in library source.
    whenever(mockResolvedCall.candidateDescriptor).thenReturn(mockCandidateDescriptor)
    whenever(mockCandidateDescriptor.annotations).thenReturn(mockAnnotations)
    whenever(mockAnnotations.findAnnotation(ComposeFqNames.Composable)).thenAnswer {
      if (isComposableInvocation) mock<AnnotationDescriptor>() else null
    }

    // Setup mocks for whether compose is enabled in the module
    whenever(mockModuleSystem.usesCompose).thenAnswer { moduleUsesCompose }
  }

  @Test
  fun highlightDeclaration_returnsNull() {
    assertThat(highlighter.highlightDeclaration(mockElement, mockDescriptor)).isNull()
  }

  @Test
  fun highlightCall_notComposableInvocation_returnsNull() {
    isComposableInvocation = false

    moduleUsesCompose = false
    isInLibrarySource = false
    assertThat(highlighter.highlightCall(mockElement, mockResolvedCall)).isNull()

    moduleUsesCompose = false
    isInLibrarySource = true
    assertThat(highlighter.highlightCall(mockElement, mockResolvedCall)).isNull()

    moduleUsesCompose = true
    isInLibrarySource = false
    assertThat(highlighter.highlightCall(mockElement, mockResolvedCall)).isNull()

    moduleUsesCompose = true
    isInLibrarySource = true
    assertThat(highlighter.highlightCall(mockElement, mockResolvedCall)).isNull()
  }

  @Test
  fun highlightCall_composableInvocation_returnsAttributesWhenExpected() {
    isComposableInvocation = true
    moduleUsesCompose = true

    // If the call is outside a compose-enabled module or library source, there should be no highlighting.
    moduleUsesCompose = false
    isInLibrarySource = false
    assertThat(highlighter.highlightCall(mockElement, mockResolvedCall)).isNull()

    moduleUsesCompose = false
    isInLibrarySource = true
    assertThat(highlighter.highlightCall(mockElement, mockResolvedCall)).isEqualTo(
      ComposableHighlighterExtension.COMPOSABLE_CALL_TEXT_ATTRIBUTES_KEY)

    moduleUsesCompose = true
    isInLibrarySource = false
    assertThat(highlighter.highlightCall(mockElement, mockResolvedCall)).isEqualTo(
      ComposableHighlighterExtension.COMPOSABLE_CALL_TEXT_ATTRIBUTES_KEY)

    moduleUsesCompose = true
    isInLibrarySource = true
    assertThat(highlighter.highlightCall(mockElement, mockResolvedCall)).isEqualTo(
      ComposableHighlighterExtension.COMPOSABLE_CALL_TEXT_ATTRIBUTES_KEY)
  }
}
