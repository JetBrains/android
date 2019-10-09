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
package com.android.tools.idea.compose.preview

import com.intellij.openapi.Disposable
import com.intellij.openapi.editor.event.DocumentEvent
import com.intellij.openapi.editor.event.DocumentListener
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.TextRange
import com.intellij.psi.PsiDocumentManager
import com.intellij.psi.PsiFile
import com.intellij.util.ui.update.MergingUpdateQueue
import com.intellij.util.ui.update.Update
import java.util.concurrent.TimeUnit
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.withLock

/**
 * Sets up a change listener for the given [psiFile]. When the file changes, [refreshPreview] will be called if any [PreviewElement] has
 * been affected by the change. [sourceCodeChanged] will be called if there was a potential source code change but the [PreviewElement]s
 * remained the same.
 *
 * The [previewElementProvider] should provide the last valid list of [PreviewElement]s.
 *
 * The given [parentDisposable] will be used to set the life cycle of the listener. When disposed, the listener will be disposed too.
 */
fun setupChangeListener(
  project: Project,
  psiFile: PsiFile,
  previewElementProvider: () -> List<PreviewElement>,
  refreshPreview: () -> Unit,
  sourceCodeChanged: () -> Unit,
  parentDisposable: Disposable,
  mergeQueue: MergingUpdateQueue = MergingUpdateQueue("Document change queue",
                                                      TimeUnit.SECONDS.toMillis(1).toInt(),
                                                      true,
                                                      null,
                                                      parentDisposable).setRestartTimerOnAdd(true)) {
  val documentManager = PsiDocumentManager.getInstance(project)
  documentManager.getDocument(psiFile)!!.addDocumentListener(object : DocumentListener {
    val aggregatedEventsLock = ReentrantLock()
    val aggregatedEvents = mutableSetOf<DocumentEvent>()
    var previousPreviewElements = previewElementProvider()

    private fun onDocumentChanged(event: Set<DocumentEvent>) {
      val currentPreviewElements = previewElementProvider()
      if (currentPreviewElements != previousPreviewElements) {
        // The preview elements have changed so force refresh
        refreshPreview()
      }
      else {
        sourceCodeChanged()
      }

      previousPreviewElements = currentPreviewElements
    }

    override fun documentChanged(event: DocumentEvent) {
      // On documentChange, we simply save the event to be processed later when onDocumentChanged is called.
      aggregatedEventsLock.withLock {
        aggregatedEvents.add(event)
      }

      // We use the merge queue to avoid triggering unnecessary updates. All the changes are aggregated. We wait for the documents to be
      // committed and then we evaluate the change.
      mergeQueue.queue(object : Update("document change") {
        override fun run() {
          documentManager.performLaterWhenAllCommitted( Runnable {
            mergeQueue.queue(object : Update("handle changes") {
              override fun run() {
                onDocumentChanged(aggregatedEventsLock.withLock {
                  val aggregatedEventsCopy = aggregatedEvents.toSet()
                  aggregatedEvents.clear()

                  aggregatedEventsCopy
                })
              }
            })
          })
        }
      })


    }
  }, parentDisposable)
}