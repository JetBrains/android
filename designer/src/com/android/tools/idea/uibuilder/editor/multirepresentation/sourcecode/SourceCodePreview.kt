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
import com.intellij.openapi.Disposable
import com.intellij.openapi.application.invokeLater
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.DumbService
import com.intellij.openapi.project.DumbService.Companion.DUMB_MODE
import com.intellij.psi.PsiFile
import java.util.concurrent.atomic.AtomicBoolean

/**
 * A [MultiRepresentationPreview] tailored to the source code files.
 *
 * @param psiFile the file being edited by this editor.
 * @param editor the [Editor] for the file.
 * @param providers list of [PreviewRepresentationProvider] for this file type.
 */
internal class SourceCodePreview(
  psiFile: PsiFile,
  textEditor: Editor,
  providers: Collection<PreviewRepresentationProvider>,
) : MultiRepresentationPreview(psiFile, textEditor, providers) {

  val project = psiFile.project

  private val afterSyncUpdateScheduled = AtomicBoolean(false)

  init {
    // Update the representation in case we are at the project startup.
    // Because we couldn't get into non-Smart Mode, we need to update
    // the representations when this object is initialised.
    scheduleRepresentationsUpdates()
    project.messageBus
      .connect(this as Disposable)
      .subscribe(
        DUMB_MODE,
        object : DumbService.DumbModeListener {
          /**
           * Update representations in case we are at the project startup and we get into non-Smart
           * Mode. We do not want to get [updateRepresentationsAsync] executed simply on the
           * [exitDumbMode] as the project is not started yet and AndroidModel is not initialized.
           * Instead, we want to schedule the representation updates during the non-Smart Mode.
           */
          override fun enteredDumbMode() {
            if (afterSyncUpdateScheduled.getAndSet(true)) {
              return
            }
            scheduleRepresentationsUpdates()
          }
        },
      )

    setupChangeListener(
      project,
      psiFile,
      {
        if (project.getSyncManager().isSyncInProgress()) {
          return@setupChangeListener
        }
        updateRepresentationsAsync()
      },
      this,
    )
  }

  /**
   * Schedules [runWhenSmartAndSynced] so that it gets scheduled with [DumbService.runWhenSmart]
   * that waits for the project to finish initialization.
   */
  private fun scheduleRepresentationsUpdates() {
    // invokeLater required due to IDEA-321276: calling runWhenSmart() inside
    // DumbModeListener does not work properly.
    invokeLater {
      project.runWhenSmartAndSynced(
        parentDisposable = this@SourceCodePreview,
        callback = {
          afterSyncUpdateScheduled.set(false)
          updateRepresentationsAsync()
        },
      )
    }
  }
}
