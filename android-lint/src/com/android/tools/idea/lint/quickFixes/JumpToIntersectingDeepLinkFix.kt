/*
 * Copyright (C) 2024 The Android Open Source Project
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
package com.android.tools.idea.lint.quickFixes

import com.android.tools.idea.IdeInfo
import com.android.tools.idea.lint.common.AndroidQuickfixContexts
import com.android.tools.idea.lint.common.DefaultLintQuickFix
import com.android.tools.idea.util.toVirtualFile
import com.android.tools.lint.detector.api.Location
import com.intellij.codeInsight.intention.preview.IntentionPreviewInfo
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.fileEditor.OpenFileDescriptor
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile

class JumpToIntersectingDeepLinkFix(private val destination: Location) :
  DefaultLintQuickFix("Go to activity with intersecting deep link") {

  override fun isApplicable(
    startElement: PsiElement,
    endElement: PsiElement,
    contextType: AndroidQuickfixContexts.ContextType,
  ): Boolean = IdeInfo.getInstance().isAndroidStudio

  override fun apply(
    startElement: PsiElement,
    endElement: PsiElement,
    context: AndroidQuickfixContexts.Context,
  ) {
    val virtualFile = destination.file.toVirtualFile() ?: return
    val offset = destination.start?.offset ?: return
    OpenFileDescriptor(startElement.project, virtualFile, offset).navigate(/* requestFocus= */ true)
  }

  override fun generatePreview(
    project: Project,
    editor: Editor,
    file: PsiFile,
  ): IntentionPreviewInfo {
    return IntentionPreviewInfo.EMPTY
  }
}
