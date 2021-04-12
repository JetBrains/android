/*
 * Copyright (C) 2020 The Android Open Source Project
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
package com.android.tools.idea.editors.literals

import com.intellij.openapi.application.ReadAction
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Segment
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.psi.SmartPointerManager
import com.intellij.psi.SmartPsiElementPointer
import com.intellij.psi.util.parentsOfType
import com.intellij.serviceContainer.AlreadyDisposedException
import org.jetbrains.kotlin.idea.core.util.range
import org.jetbrains.kotlin.idea.core.util.start

private fun SmartPsiElementPointer<PsiElement>.isValid() = range != null && ReadAction.compute<Boolean, Throwable> { element != null }

/**
 * A [SmartPsiElementPointer] that is able to find a equivalent [PsiElement] when there is been a deletion and then an insertion. Usually,
 * when a [PsiElement], the element will be invalidated and the tracking of the element by the pointer will stop. This implementation tries
 * to find the equivalent [PsiElement] when a new is added. This operation should be transparent to the user of this pointer.
 *
 * @param originalElement the [PsiElement] to track
 */
class ReattachableSmartPsiElementPointer(originalElement: PsiElement): SmartPsiElementPointer<PsiElement> {
  private val originalStartOffset = originalElement.range.start
  private val originalElementClass = originalElement::class.java
  private var elementPointer = SmartPointerManager.createPointer(originalElement)

  /**
   * Verifies that [elementPointer] is valid. If its not, it will try to find the equivalent [PsiElement] and return the pointer to that
   * one.
   */
  private fun verifyAndReattach(): SmartPsiElementPointer<PsiElement> =
    if (elementPointer.isValid()) {
      elementPointer
    }
    else {
      // Try to reattach. Now we use a simple heuristic where, we get the element at the same offset. If it's from the same type (class),
      // then we re-attach the pointer to that one.
      try {
        ReadAction.run<Throwable> {
          elementPointer.containingFile
            ?.findElementAt(originalStartOffset)
            ?.parentsOfType(originalElementClass, true)
            ?.firstOrNull { it.range.start == originalStartOffset }
            ?.let {
              elementPointer = SmartPointerManager.createPointer(it)
            }
        }
      }
      catch (_: AlreadyDisposedException) {
        // The project was probably disposed while waiting for the read lock
      }
      elementPointer
    }

  override fun getElement(): PsiElement? = verifyAndReattach().element
  override fun getContainingFile(): PsiFile? = verifyAndReattach().containingFile
  override fun getProject(): Project = verifyAndReattach().project
  override fun getVirtualFile(): VirtualFile = verifyAndReattach().virtualFile
  override fun getRange(): Segment? = verifyAndReattach().range
  override fun getPsiRange(): Segment? = verifyAndReattach().psiRange
}