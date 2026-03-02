/*
 * Copyright (C) 2026 The Android Open Source Project
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
package com.android.tools.idea.preview.util

import com.intellij.openapi.application.runReadAction
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Segment
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.PsiFile
import com.intellij.psi.PsiManager
import com.intellij.psi.SmartPointerManager
import com.intellij.psi.SmartPsiElementPointer
import java.lang.ref.WeakReference

/**
 * Version of [SmartPsiElementPointer] for a [psiFile] that attempts to recover from invalidated pointers by trying to recover the new
 * [PsiFile] from the original [virtualFile] and [project].
 *
 * This is needed for the Preview tools to recover from such unexpected situations and keep the tool running without needing to close and
 * re-open the tab in the editor.
 *
 * @param psiFile the file the pointer should reference
 * @param onInvalidationRecovered the callback to be executed whenever the file reference gets invalidated and a new file reference is
 *   recovered
 */
class PreviewFilePointer(psiFile: PsiFile, private val onInvalidationRecovered: () -> Unit) : SmartPsiElementPointer<PsiFile> {
  private val virtualFile = WeakReference(psiFile.virtualFile)
  private val project = WeakReference(psiFile.project)
  private var delegate = runReadAction { SmartPointerManager.createPointer(psiFile) }
    get() {
      if (field.element == null) {
        val project = project.get() ?: return field
        val vFile = virtualFile.get() ?: return field
        runReadAction {
          PsiManager.getInstance(project).findFile(vFile)?.let {
            field = SmartPointerManager.createPointer(it)
            onInvalidationRecovered()
          }
        }
      }
      return field
    }

  override fun getElement(): PsiFile? = delegate.element

  override fun getContainingFile(): PsiFile? = delegate.containingFile

  override fun getProject(): Project = runReadAction { delegate.project }

  override fun getVirtualFile(): VirtualFile = delegate.virtualFile

  override fun getRange(): Segment? = delegate.range

  override fun getPsiRange(): Segment? = delegate.psiRange
}
