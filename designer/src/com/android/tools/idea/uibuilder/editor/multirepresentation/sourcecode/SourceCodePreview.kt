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
package com.android.tools.idea.uibuilder.editor.multirepresentation.sourcecode

import com.android.tools.idea.editors.setupChangeListener
import com.android.tools.idea.projectsystem.getSyncManager
import com.android.tools.idea.uibuilder.editor.multirepresentation.MultiRepresentationPreview
import com.android.tools.idea.uibuilder.editor.multirepresentation.PreviewRepresentationProvider
import com.android.tools.idea.util.runWhenSmartAndSynced
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.DumbService
import com.intellij.openapi.project.DumbService.DUMB_MODE
import com.intellij.psi.PsiFile
import java.util.concurrent.atomic.AtomicBoolean

/**
 * A [MultiRepresentationPreview] tailored to the source code files.
 *
 * @param psiFile the file being edited by this editor.
 * @param editor the [Editor] for the file.
 * @param providers list of [PreviewRepresentationProvider] for this file type.
 */
internal class SourceCodePreview(psiFile: PsiFile, textEditor: Editor, providers: Collection<PreviewRepresentationProvider>) :
  MultiRepresentationPreview(psiFile, textEditor, providers) {

  val project = psiFile.project

  private val afterSyncUpdateScheduled = AtomicBoolean(false)

  init {
    project.messageBus.connect(this).subscribe(DUMB_MODE, object : DumbService.DumbModeListener {
      /**
       * In case we are at the project startup we do not want to get [updateRepresentationsAsync] executed simply on the [exitDumbMode] for
       * the project is not started yet and AndroidModel is not initialized. Instead, we want to schedule [runWhenSmartAndSynced] during
       * the Dumb Mode so that it gets scheduled with [DumbService.runWhenSmart] that waits for the project to finish initialization.
       */
      override fun enteredDumbMode() {
        if (afterSyncUpdateScheduled.getAndSet(true)) {
          return
        }
        project.runWhenSmartAndSynced(
          parentDisposable = this@SourceCodePreview,
          callback = {
            afterSyncUpdateScheduled.set(false)
            updateRepresentationsAsync()
          }
        )
      }
    })

    setupChangeListener(project, psiFile, {
      if (project.getSyncManager().isSyncInProgress()) {
        return@setupChangeListener
      }
      updateRepresentationsAsync()
    }, this)
  }
}