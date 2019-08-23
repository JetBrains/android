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
package com.android.tools.idea.common.editor

import com.intellij.openapi.project.DumbService
import com.intellij.psi.PsiComment
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.psi.PsiTreeChangeAdapter
import com.intellij.psi.PsiTreeChangeEvent
import com.intellij.psi.util.PsiTreeUtil

/**
 * A PsiTreeChangeListener implementation that ignores all before- triggers and calls [onSourceCodeChanged] callback whenever byte-code
 * affecting source code in the [psiFile] is changed (changes in the comments are ignored)
 */
class SourceCodeChangeListener(private val psiFile: PsiFile,
                               private val onSourceCodeChanged: (psiElement: PsiElement) -> Unit) : PsiTreeChangeAdapter() {
  private fun elementChanged(eventPsiFile: PsiFile?, psiElement: PsiElement?) {
    if (psiElement == null || eventPsiFile != psiFile) {
      return
    }

    if (DumbService.isDumb(psiFile.project)) {
      return
    }

    if (PsiTreeUtil.getParentOfType(psiElement, PsiComment::class.java, false) != null) {
      // Ignore comments
      return
    }

    onSourceCodeChanged(psiElement)
  }

  override fun childAdded(event: PsiTreeChangeEvent) {
    elementChanged(event.file, event.child?.parent)
  }

  override fun childRemoved(event: PsiTreeChangeEvent) {
    elementChanged(event.file, event.child?.parent)
  }

  override fun childrenChanged(event: PsiTreeChangeEvent) {
    elementChanged(event.file, event.child?.parent)
  }

  override fun childReplaced(event: PsiTreeChangeEvent) {
    elementChanged(event.file, event.child?.parent)
  }

  override fun childMoved(event: PsiTreeChangeEvent) {
    elementChanged(event.file, event.child?.parent)
  }

  override fun propertyChanged(event: PsiTreeChangeEvent) {
    elementChanged(event.file, event.child?.parent)
  }
}