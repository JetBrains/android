/*
 * Copyright (C) 2021 The Android Open Source Project
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
package com.android.tools.idea.compose.preview.literals

import com.android.tools.idea.compose.preview.PsiFileSnapshotFilter
import com.android.tools.idea.editors.literals.LiveLiteralsService
import com.intellij.openapi.Disposable
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.ModificationTracker
import com.intellij.openapi.util.SimpleModificationTracker
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile

/**
 * A [PsiFileSnapshotFilter] that filters literals if they are managed by the [LiveLiteralsPsiFileSnapshotFilter].
 */
class LiveLiteralsPsiFileSnapshotFilter private constructor(parentDisposable: Disposable,
                                                            private val editorFile: PsiFile,
                                                            private val modificationTracker: SimpleModificationTracker) : PsiFileSnapshotFilter, ModificationTracker by modificationTracker {
  constructor(parentDisposable: Disposable, editorFile: PsiFile): this(parentDisposable, editorFile, SimpleModificationTracker())

  private val project: Project = editorFile.project
  init {
    LiveLiteralsService.getInstance(project).addOnManagedElementsUpdatedListener(parentDisposable, object: LiveLiteralsService.ManagedElementsUpdatedListener {
      override fun onChange(file: PsiFile) {
        if (file.isEquivalentTo(editorFile)) modificationTracker.incModificationCount()
      }
    })
  }

  override fun accepts(element: PsiElement): Boolean = !LiveLiteralsService.getInstance(project).isElementManaged(element)
}