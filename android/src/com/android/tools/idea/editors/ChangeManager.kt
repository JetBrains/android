package com.android.tools.idea.editors

import com.android.annotations.concurrency.GuardedBy
import com.android.tools.idea.concurrency.disposableCallbackFlow
import com.intellij.codeInsight.lookup.LookupEvent
import com.intellij.codeInsight.lookup.LookupListener
import com.intellij.codeInsight.lookup.LookupManager
import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ReadAction
import com.intellij.openapi.application.runReadAction
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
                                     private val lookupManager: LookupManager,
                                     private val mergeQueue: MergingUpdateQueue,
                                     private val timeNanosProvider: () -> Long) : DocumentListener {
  val aggregatedEventsLock = ReentrantLock()

  @GuardedBy("aggregatedEventsLock")
  val aggregatedEvents = mutableSetOf<DocumentEvent>()

  @GuardedBy("aggregatedEventsLock")
  var lastUpdatedNanos = 0L

  /**
   * [LookupListener] that is added to the active lookup to detect when the completion is closed.
   */
  private val lookupListener = object: LookupListener {
    override fun lookupCanceled(event: LookupEvent) {
      event.lookup.removeLookupListener(this)
      queueDocumentUpdates()
    }

    override fun itemSelected(event: LookupEvent) {
      event.lookup.removeLookupListener(this)
      queueDocumentUpdates()
    }
  }

  private fun onDocumentChanged(events: Set<DocumentEvent>) {
    val documents = events
      .map { it.document }
      .distinct()

    onDocumentsUpdated(documents, aggregatedEventsLock.withLock { lastUpdatedNanos })
  }

  private fun queueDocumentUpdates() {
    if (aggregatedEventsLock.withLock { aggregatedEvents.isEmpty() }) return

    // We use the merge queue to avoid triggering unnecessary updates. All the changes are aggregated. We wait for the documents to be
    // committed and then we evaluate the change.
    mergeQueue.queue(object : Update("document change") {
      override fun run() {
        documentManager.performLaterWhenAllCommitted {
          mergeQueue.queue(object : Update("handle changes") {
            override fun run() {
              val activeLookup = lookupManager.activeLookup
              activeLookup?.let {
                // Delay the triggering of the update until the completion has finished.
                it.removeLookupListener(lookupListener)
                it.addLookupListener(lookupListener)
              }

              // If there is no lookup or if it has been disposed while we were setting up the listeners,
              // trigger an update.
              if (activeLookup == null || activeLookup != lookupManager.activeLookup) {
                onDocumentChanged(aggregatedEventsLock.withLock {
                  val aggregatedEventsCopy = aggregatedEvents.toSet()
                  aggregatedEvents.clear()

                  aggregatedEventsCopy
                })
              }
            }
          })
        }
      }
    })
  }

  override fun documentChanged(event: DocumentEvent) {
    // On documentChange, we simply save the event to be processed later when onDocumentChanged is called.
    aggregatedEventsLock.withLock {
      aggregatedEvents.add(event)
      lastUpdatedNanos = timeNanosProvider()
    }

    queueDocumentUpdates()
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
  val lookupManager = LookupManager.getInstance(project)
  EditorFactory.getInstance().eventMulticaster.addDocumentListener(
    DocumentChangeListener(onDocumentsUpdated, documentManager, lookupManager, mergeQueue, timeNanosProvider),
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
  val lookupManager = LookupManager.getInstance(project)
  val document = ReadAction.compute<Document, Throwable> { documentManager.getDocument(psiFile)!! }
  document.addDocumentListener(
    DocumentChangeListener({ _, lastUpdatedNanos -> onDocumentUpdated(lastUpdatedNanos) }, documentManager, lookupManager, mergeQueue, timeNanosProvider),
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
  val psiFilePointer = runReadAction { SmartPointerManager.createPointer(psiFile) }
  project.messageBus.connect(parentDisposable).subscribe(FileDocumentManagerListener.TOPIC, object : FileDocumentManagerListener {
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

/**
 * Returns a new flow that gets all the document changes for the given [psiFile]. The flow is cancelled if the [parentDisposable] is disposed.
 * [onReady] will be called when the flow is ready to start processing events from the document.
 */
fun documentChangeFlow(psiFile: PsiFile, parentDisposable: Disposable, log: Logger? = null, onReady: () -> Unit = {}) =
  disposableCallbackFlow<Long>("ChangeListenerFlow", log, parentDisposable) {
    val documentManager = PsiDocumentManager.getInstance(psiFile.project)
    val document = ReadAction.compute<Document, Throwable> { documentManager.getDocument(psiFile)!! }
    document.addDocumentListener(
      object : DocumentListener {
        override fun documentChanged(event: DocumentEvent) {
          trySend(event.document.modificationStamp)
        }
      },
      disposable)
    onReady()
  }