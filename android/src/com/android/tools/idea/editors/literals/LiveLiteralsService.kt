package com.android.tools.idea.editors.literals

import com.android.tools.idea.AndroidPsiUtils
import com.android.tools.idea.editors.setupChangeListener
import com.android.tools.idea.flags.StudioFlags
import com.android.tools.idea.gradle.util.BuildListener
import com.android.tools.idea.gradle.util.setupBuildListener
import com.android.tools.idea.rendering.classloading.ConstantRemapperManager
import com.android.tools.idea.util.ListenerCollection
import com.intellij.codeInsight.highlighting.HighlightManager
import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ReadAction
import com.intellij.openapi.components.Service
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.editor.Document
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.EditorFactory
import com.intellij.openapi.editor.event.EditorFactoryEvent
import com.intellij.openapi.editor.event.EditorFactoryListener
import com.intellij.openapi.editor.markup.EffectType
import com.intellij.openapi.editor.markup.RangeHighlighter
import com.intellij.openapi.editor.markup.TextAttributes
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import com.intellij.psi.PsiDocumentManager
import com.intellij.psi.PsiFile
import com.intellij.ui.AppUIUtil
import com.intellij.util.concurrency.AppExecutorUtil
import com.intellij.util.ui.UIUtil
import com.intellij.util.ui.update.MergingUpdateQueue
import org.jetbrains.annotations.TestOnly
import java.awt.Font
import java.lang.ref.WeakReference

private val LITERAL_TEXT_ATTRIBUTE = TextAttributes(UIUtil.getActiveTextColor(),
                                                    null,
                                                    UIUtil.getInactiveTextColor(),
                                                    EffectType.ROUNDED_BOX,
                                                    Font.BOLD)

/**
 * Time used to coalesce multiple changes without triggering onLiteralsHaveChanged calls.
 */
private const val DOCUMENT_CHANGE_COALESCE_TIME_MS = 200

/**
 * Project service to track live literals. The service, when [isEnabled] is true, will listen for changes of constants
 * and will notify listeners.
 */
@Service
class LiveLiteralsService(private val project: Project) : Disposable {
  companion object {
    fun getInstance(project: Project): LiveLiteralsService = project.getService(LiveLiteralsService::class.java)
  }

  private val log = Logger.getInstance(LiveLiteralsService::class.java)

  /**
   * [ListenerCollection] for all the listeners that need to be notified when any live literal has changed value.
   */
  private val onLiteralsChangedListeners = ListenerCollection.createWithExecutor<Runnable>(
    AppExecutorUtil.createBoundedApplicationPoolExecutor("Document changed listeners executor", 1))

  private val literalsManager = LiteralsManager()
  private val documentSnapshots = mutableMapOf<Document, LiteralReferenceSnapshot>()

  /**
   * [Disposable] that tracks the current activation. If the service is deactivated, this [Disposable] will be disposed.
   * It can be used to register anything that should be disposed when the service is not running.
   */
  private var activationDisposable: Disposable? = null

  private val updateMergingQueue = MergingUpdateQueue("Live literals change queue",
                                                      DOCUMENT_CHANGE_COALESCE_TIME_MS,
                                                      true,
                                                      null,
                                                      this,
                                                      null,
                                                      false).setRestartTimerOnAdd(true)

  /**
   * Controls when the live literals tracking is enabled.
   */
  var isEnabled = false
    set(value) {
      if (value != field) {
        field = value
        if (value) activate() else deactivate()
      }
    }

  @TestOnly
  fun allConstants(): Collection<LiteralReference> = documentSnapshots.flatMap { (_, snapshot) -> snapshot.all }

  /**
   * Method called to notify the listeners than a constant has changed.
   */
  private fun fireOnLiteralsChanged() = onLiteralsChangedListeners.forEach {
    it.run()
  }

  /**
   * Adds a new listener to be notified when the literals change.
   *
   * @param parentDisposable [Disposable] to control the lifespan of the listener. If the parentDisposable is disposed
   *  the listener will automatically be unregistered.
   * @param listener the code to be called when the literals change. This will run in a background thread.
   */
  fun addOnLiteralsChangedListener(parentDisposable: Disposable, listener: () -> Unit) {
    onLiteralsChangedListeners.add(listener = listener)
    val listenerWeakRef = WeakReference(listener)
    Disposer.register(parentDisposable) {
      onLiteralsChangedListeners.remove(listenerWeakRef.get() ?: return@register)
    }
  }

  @Synchronized
  private fun onDocumentsUpdated(document: Collection<Document>, @Suppress("UNUSED_PARAMETER") lastUpdateNanos: Long) {
    var requestRefresh = false
    document.flatMap {
      documentSnapshots[it]?.modified ?: emptyList()
    }.forEach {
      val constantValue = it.constantValue ?: return@forEach
      it.usages.forEach { elementPath ->
        val constantModified = ConstantRemapperManager.getConstantRemapper().addConstant(
          null, elementPath, it.initialConstantValue, constantValue)
        log.debug("[${it.uniqueId}] Constant updated to ${it.text} path=${elementPath}")

        requestRefresh = requestRefresh || constantModified
      }
    }

    if (requestRefresh) {
      fireOnLiteralsChanged()
    }
  }

  /**
   * Adds a new document to the tracking. The document will be observed for changes.
   */
  private fun addDocumentTracking(parentDisposable: Disposable, editor: Editor, document: Document) {
    val file = AndroidPsiUtils.getPsiFileSafely(project, document) ?: return

    // Take a snapshot
    val fileSnapshot = literalsManager.findLiterals(file)
    if (log.isDebugEnabled) {
      fileSnapshot.all.forEach {
        val elementPathString = it.usages.joinToString("\n") { element -> element.toString() }
        log.debug("[${it.uniqueId}] Found constant ${it.text} \n$elementPathString\n\n")
      }
    }

    if (fileSnapshot.all.isNotEmpty()) {
      documentSnapshots[document] = fileSnapshot

      // When using "always-on" live literals, we don't show highlights.
      if (!StudioFlags.COMPOSE_ALWAYS_ON_LIVE_LITERALS.get()) {
        UIUtil.invokeAndWaitIfNeeded(Runnable {
          val highlightManager = HighlightManager.getInstance(project)
          val outHighlighters = mutableSetOf<RangeHighlighter>()
          fileSnapshot.highlightSnapshotInEditor(project, editor, LITERAL_TEXT_ATTRIBUTE, outHighlighters)
          if (outHighlighters.isNotEmpty()) {
            // Remove the highlights if the manager is deactivated
            Disposer.register(parentDisposable, {
              outHighlighters.forEach { highlightManager.removeSegmentHighlighter(editor, it) }
            })
          }
        })
      }
    }
  }

  @Synchronized
  private fun activate() {
    if (Disposer.isDisposed(this)) return
    val newActivationDisposable = Disposer.newDisposable()

    // Find all the active editors
    EditorFactory.getInstance().allEditors.forEach {
      addDocumentTracking(newActivationDisposable, it, it.document)
    }

    // Listen for all new editors opening
    EditorFactory.getInstance().addEditorFactoryListener(object : EditorFactoryListener {
      override fun editorCreated(event: EditorFactoryEvent) {
        addDocumentTracking(newActivationDisposable, event.editor, event.editor.document)
      }

      override fun editorReleased(event: EditorFactoryEvent) {
        documentSnapshots.remove(event.editor.document)
      }
    }, newActivationDisposable)


    setupChangeListener(project, ::onDocumentsUpdated, newActivationDisposable, updateMergingQueue)
    setupBuildListener(project, object : BuildListener {
      override fun buildSucceeded() {
        ConstantRemapperManager.getConstantRemapper().clearConstants(null)
      }

      override fun buildFailed() {
        ConstantRemapperManager.getConstantRemapper().clearConstants(null)
      }

      override fun buildStarted() {
        // Stop the literals listening while the build happens
        deactivate()
      }
    }, newActivationDisposable)

    Disposer.register(this, newActivationDisposable)
    activationDisposable = newActivationDisposable
  }

  @Synchronized
  private fun deactivate(fireChangeListeners: Boolean = true) {
    ConstantRemapperManager.getConstantRemapper().clearConstants(null)
    activationDisposable?.let {
      Disposer.dispose(it)
    }
    activationDisposable = null
    if (fireChangeListeners) {
      fireOnLiteralsChanged()
    }
  }

  override fun dispose() {
    isEnabled = false
    deactivate(false)
  }
}