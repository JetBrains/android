package com.android.tools.idea.editors

import com.android.annotations.concurrency.GuardedBy
import com.intellij.AppTopics
import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ReadAction
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.editor.Document
import com.intellij.openapi.editor.EditorFactory
import com.intellij.openapi.editor.event.DocumentEvent
import com.intellij.openapi.editor.event.DocumentListener
import com.intellij.openapi.fileEditor.FileDocumentManagerListener
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiDocumentManager
import com.intellij.psi.PsiFile
import com.intellij.psi.SmartPointerManager
import com.intellij.util.ui.update.MergingUpdateQueue
import com.intellij.util.ui.update.Update
import java.util.concurrent.TimeUnit
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.withLock

private class DocumentChangeListener(private val onDocumentsUpdated: (Collection<Document>, Long) -> Unit,
                                     private val documentManager: PsiDocumentManager,
                                     private val mergeQueue: MergingUpdateQueue,
                                     private val timeNanosProvider: () -> Long) : DocumentListener {
  val aggregatedEventsLock = ReentrantLock()

  @GuardedBy("aggregatedEventsLock")
  val aggregatedEvents = mutableSetOf<DocumentEvent>()

  @GuardedBy("aggregatedEventsLock")
  var lastUpdatedNanos = 0L

  private fun onDocumentChanged(events: Set<DocumentEvent>) {
    val documents = events
      .map { it.document }
      .distinct()

    onDocumentsUpdated(documents, aggregatedEventsLock.withLock { lastUpdatedNanos })
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
        documentManager.performLaterWhenAllCommitted {
          mergeQueue.queue(object : Update("handle changes") {
            override fun run() {
              onDocumentChanged(aggregatedEventsLock.withLock {
                val aggregatedEventsCopy = aggregatedEvents.toSet()
                aggregatedEvents.clear()

                aggregatedEventsCopy
              })
            }
          })
        }
      }
    })
  }
}

/**
 * Sets up a change listener for the given [project]. When the file changes, [onDocumentUpdated] will be called. The listener might be
 * called in any thread.
 *
 * The given [parentDisposable] will be used to set the life cycle of the listener. When disposed, the listener will be disposed too.
 */
fun setupChangeListener(
  project: Project,
  onDocumentsUpdated: (Collection<Document>, Long) -> Unit,
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
  EditorFactory.getInstance().eventMulticaster.addDocumentListener(
    DocumentChangeListener(onDocumentsUpdated, documentManager, mergeQueue, timeNanosProvider),
    parentDisposable)
}


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
  val document = ReadAction.compute<Document, Throwable> { documentManager.getDocument(psiFile)!! }
  document.addDocumentListener(
    DocumentChangeListener({ _, lastUpdatedNanos -> onDocumentUpdated(lastUpdatedNanos) }, documentManager, mergeQueue, timeNanosProvider),
    parentDisposable)
}

/**
 * Sets up a save listener for the given [psiFile]. When the file is saved, [onSave] will be called. The listener might be
 * called in any thread.
 *
 * The given [parentDisposable] will be used to set the life cycle of the listener. When disposed, the listener will be disposed too.
 */
fun setupOnSaveListener(
  project: Project,
  psiFile: PsiFile,
  onSave: () -> Unit,
  parentDisposable: Disposable,
  mergeQueue: MergingUpdateQueue = MergingUpdateQueue("Document save queue",
                                                      TimeUnit.SECONDS.toMillis(1).toInt(),
                                                      true,
                                                      null,
                                                      parentDisposable,
                                                      null,
                                                      false).setRestartTimerOnAdd(true)) {
  val psiFilePointer = SmartPointerManager.createPointer(psiFile)
  project.messageBus.connect(parentDisposable).subscribe(AppTopics.FILE_DOCUMENT_SYNC, object : FileDocumentManagerListener {
    override fun beforeDocumentSaving(document: Document) {
      val psiFile = psiFilePointer.element ?: return
      val fileDocument = PsiDocumentManager.getInstance(project).getDocument(psiFile)
      if (fileDocument == document) {
        mergeQueue.queue(object : Update("document saved") {
          override fun run() {
            val logger = Logger.getInstance("com.android.tools.idea.common.util.ChangeManager")
            if (psiFile.project.isDisposed) {
              logger.debug("project already disposed")
              return
            }

            logger.debug("onSave")
            onSave()
          }
        })
      }
    }
  })
}