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
package com.android.tools.idea.common.util

import com.android.tools.idea.gradle.project.build.BuildContext
import com.android.tools.idea.gradle.project.build.BuildStatus
import com.android.tools.idea.gradle.project.build.GradleBuildListener
import com.android.tools.idea.gradle.project.build.GradleBuildState
import com.android.tools.idea.gradle.util.BuildMode
import com.android.tools.idea.projectsystem.ProjectSystemSyncManager
import com.android.tools.idea.util.listenUntilNextSync
import com.android.tools.idea.util.runWhenSmartAndSyncedOnEdt
import com.intellij.openapi.Disposable
import com.intellij.openapi.editor.event.DocumentEvent
import com.intellij.openapi.editor.event.DocumentListener
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.PsiDocumentManager
import com.intellij.psi.PsiFile
import com.intellij.ui.EditorNotifications
import com.intellij.util.ui.update.MergingUpdateQueue
import com.intellij.util.ui.update.Update
import java.util.concurrent.TimeUnit
import java.util.concurrent.locks.ReentrantLock
import java.util.function.Consumer
import kotlin.concurrent.withLock

/**
 * Sets up a change listener for the given [psiFile]. When the file changes, [onDocumentUpdated] will be called. The listener might be
 * called in any thread.
 *
 * The given [parentDisposable] will be used to set the life cycle of the listener. When disposed, the listener will be disposed too.
 */
fun setupChangeListener(
  project: Project,
  psiFile: PsiFile,
  onDocumentUpdated: (Long) -> Unit,
  parentDisposable: Disposable,
  mergeQueue: MergingUpdateQueue = MergingUpdateQueue("Document change queue",
                                                      TimeUnit.SECONDS.toMillis(1).toInt(),
                                                      true,
                                                      null,
                                                      parentDisposable,
                                                      null,
                                                      false).setRestartTimerOnAdd(true),
  timeNanosProvider: () -> Long = { System.nanoTime() }) {
  val documentManager = PsiDocumentManager.getInstance(project)
  documentManager.getDocument(psiFile)!!.addDocumentListener(object : DocumentListener {
    val aggregatedEventsLock = ReentrantLock()
    val aggregatedEvents = mutableSetOf<DocumentEvent>()
    var lastUpdatedNanos = 0L

    private fun onDocumentChanged(events: Set<DocumentEvent>) {
      aggregatedEventsLock.withLock {
        onDocumentUpdated(lastUpdatedNanos)
      }
    }

    override fun documentChanged(event: DocumentEvent) {
      // On documentChange, we simply save the event to be processed later when onDocumentChanged is called.
      aggregatedEventsLock.withLock {
        aggregatedEvents.add(event)
        lastUpdatedNanos = timeNanosProvider()
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

private fun BuildStatus?.isSuccess(): Boolean =
  this == BuildStatus.SKIPPED || this == BuildStatus.SUCCESS

interface BuildListener {
  fun buildSucceeded()
  fun buildFailed() {}
  fun buildStarted() {}
}

/**
 * This sets up a listener that receives updates every time gradle build starts or finishes. On successful build, it calls
 * [BuildListener.buildSucceeded] method of the passed [BuildListener]. If the build fails, [BuildListener.buildFailed] will be called
 * instead.
 * This class ignores "clean" target builds and will not notify the listener when a clean happens since most listeners will not need to
 * listen for changes on "clean" target builds. If you need to listen for "clean" target builds, use [GradleBuildState] directly.
 *
 * This is intended to be used by [com.intellij.openapi.fileEditor.FileEditor]'s. The editor should recreate/amend the model to reflect
 * build changes. This set up should be called in the constructor the last, so that all other members are initialized as it could call
 * [BuildListener.buildSucceeded] method straight away.
 */
fun setupBuildListener(
  project: Project,
  buildable: BuildListener,
  parentDisposable: Disposable) {

  /**
   * Initializes the preview editor and triggers a refresh. This method can only be called once
   * the project has synced and is smart.
   */
  fun initPreviewWhenSmartAndSynced() {
    val status = GradleBuildState.getInstance(project)?.summary?.status
    if (status.isSuccess()) {
      // This is called from runWhenSmartAndSyncedOnEdt callback which should not be called if parentDisposable is disposed
      buildable.buildSucceeded()
    }

    GradleBuildState.subscribe(project, object : GradleBuildListener.Adapter() {
      // We do not have to check isDisposed inside the callbacks since they won't get called if parentDisposable is disposed
      override fun buildStarted(context: BuildContext) {
        if (context.buildMode == BuildMode.CLEAN) return

        buildable.buildStarted()
      }

      override fun buildFinished(status: BuildStatus, context: BuildContext?) {
        if (status.isSuccess() && context?.buildMode != BuildMode.CLEAN) {
          // We do not call refresh if the build was not successful or if it was simply a clean build.
          buildable.buildSucceeded()
        }
        else {
          buildable.buildFailed()
        }
      }
    }, parentDisposable)
  }

  /**
   * Initialize the preview. This method does not make assumptions about the project sync and smart status.
   */
  fun initPreview() {
    if (Disposer.isDisposed(parentDisposable)) return
    // We are not registering before the constructor finishes, so we should be safe here
    project.runWhenSmartAndSyncedOnEdt(parentDisposable, Consumer { result ->
      if (result.isSuccessful) {
        initPreviewWhenSmartAndSynced()
      }
      else {
        // The project failed to sync, run initialization when the project syncs correctly
        project.listenUntilNextSync(parentDisposable, object : ProjectSystemSyncManager.SyncResultListener {
          override fun syncEnded(result: ProjectSystemSyncManager.SyncResult) {
            // Sync has completed but we might not be in smart mode so re-run the initialization
            initPreview()
          }
        })
      }
    })
  }

  initPreview()
}