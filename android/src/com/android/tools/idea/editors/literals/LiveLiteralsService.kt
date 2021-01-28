package com.android.tools.idea.editors.literals

import com.android.annotations.concurrency.UiThread
import com.android.tools.idea.AndroidPsiUtils
import com.android.tools.idea.editors.setupChangeListener
import com.android.tools.idea.flags.StudioFlags
import com.android.tools.idea.projectsystem.BuildListener
import com.android.tools.idea.projectsystem.setupBuildListener
import com.android.tools.idea.rendering.classloading.ConstantRemapperManager
import com.android.tools.idea.util.ListenerCollection
import com.intellij.codeInsight.highlighting.HighlightManager
import com.intellij.openapi.Disposable
import com.intellij.openapi.components.Service
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.editor.Document
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.EditorFactory
import com.intellij.openapi.editor.event.EditorFactoryEvent
import com.intellij.openapi.editor.event.EditorFactoryListener
import com.intellij.openapi.editor.event.EditorMouseEvent
import com.intellij.openapi.editor.event.EditorMouseListener
import com.intellij.openapi.editor.markup.EffectType
import com.intellij.openapi.editor.markup.RangeHighlighter
import com.intellij.openapi.editor.markup.TextAttributes
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import com.intellij.psi.PsiFile
import com.intellij.util.concurrency.AppExecutorUtil
import com.intellij.util.containers.WeakList
import com.intellij.util.ui.UIUtil
import com.intellij.util.ui.update.MergingUpdateQueue
import org.jetbrains.annotations.TestOnly
import java.awt.Font
import java.awt.GraphicsEnvironment
import java.lang.ref.WeakReference

private val LITERAL_TEXT_ATTRIBUTE = TextAttributes(UIUtil.getActiveTextColor(),
                                                    null,
                                                    UIUtil.getInactiveTextColor(),
                                                    EffectType.ROUNDED_BOX,
                                                    Font.BOLD)

/**
 * Time used to coalesce multiple changes without triggering onLiteralsHaveChanged calls.
 */
private val DOCUMENT_CHANGE_COALESCE_TIME_MS = StudioFlags.COMPOSE_LIVE_LITERALS_UPDATE_RATE

/**
 * Project service to track live literals. The service, when [isEnabled] is true, will listen for changes of constants
 * and will notify listeners.
 */
@Service
class LiveLiteralsService(private val project: Project) : Disposable {
  /**
   * Class that groups all the highlighters for a given file/editor combination. This allows enabling/disabling them.
   */
  @UiThread
  private inner class HighlightTracker(
    file: PsiFile,
    private val editor: Editor,
    private val fileSnapshot: LiteralReferenceSnapshot) : Disposable {
    private val project = file.project
    private var showingHighlights = false
    private val outHighlighters = mutableSetOf<RangeHighlighter>()

    @Suppress("IncorrectParentDisposable")
    private fun clearAll() {
      if (Disposer.isDisposed(project)) return
      val highlightManager = HighlightManager.getInstance(project)
      outHighlighters.forEach { highlightManager.removeSegmentHighlighter(editor, it) }
      outHighlighters.clear()
    }

    fun showHighlights() {
      if (showingHighlights) return
      showingHighlights = true

      // Take a snapshot
      if (log.isDebugEnabled) {
        fileSnapshot.all.forEach {
          val elementPathString = it.usages.joinToString("\n") { element -> element.toString() }
          log.debug("[${it.uniqueId}] Found constant ${it.text} \n$elementPathString\n\n")
        }
      }

      if (fileSnapshot.all.isNotEmpty()) {
        fileSnapshot.highlightSnapshotInEditor(project, editor, LITERAL_TEXT_ATTRIBUTE, outHighlighters)

        if (outHighlighters.isNotEmpty()) {
          // Remove the highlights if the manager is deactivated
          Disposer.register(this, this::hideHighlights)
        }
      }
    }

    fun hideHighlights() {
      clearAll()
      showingHighlights = false
    }

    override fun dispose() {
      hideHighlights()
    }
  }

  companion object {
    fun getInstance(project: Project): LiveLiteralsService = project.getService(LiveLiteralsService::class.java)
  }

  private val log = Logger.getInstance(LiveLiteralsService::class.java)

  /**
   * If true, the highlights will be shown.
   */
  var showLiveLiteralsHighlights = false
    set(value) {
      field = value
      refreshHighlightTrackersVisibility()
    }
  /**
   * Link to all instantiated [HighlightTracker]s. This allows to switch them on/off via the [ToggleLiveLiteralsHighlightAction].
   * This is a [WeakList] since the trackers will be mainly held by the mouse listener created in [addDocumentTracking].
   */
  private val trackers = WeakList<HighlightTracker>()

  /**
   * [ListenerCollection] for all the listeners that need to be notified when any live literal has changed value.
   */
  private val onLiteralsChangedListeners = ListenerCollection.createWithExecutor<(List<LiteralReference>) -> Unit>(
    AppExecutorUtil.createBoundedApplicationPoolExecutor("Document changed listeners executor", 1))

  private val literalsManager = LiteralsManager()
  private val documentSnapshots = mutableMapOf<Document, LiteralReferenceSnapshot>()

  /**
   * [Disposable] that tracks the current activation. If the service is deactivated, this [Disposable] will be disposed.
   * It can be used to register anything that should be disposed when the service is not running.
   */
  private var activationDisposable: Disposable? = null

  private val updateMergingQueue = MergingUpdateQueue("Live literals change queue",
                                                      DOCUMENT_CHANGE_COALESCE_TIME_MS.get(),
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
  private fun fireOnLiteralsChanged(changed : List<LiteralReference>) = onLiteralsChangedListeners.forEach {
    it(changed)
  }

  /**
   * Adds a new listener to be notified when the literals change.
   *
   * @param parentDisposable [Disposable] to control the lifespan of the listener. If the parentDisposable is disposed
   *  the listener will automatically be unregistered.
   * @param listener the code to be called when the literals change. This will run in a background thread.
   */
  fun addOnLiteralsChangedListener(parentDisposable: Disposable, listener: (List<LiteralReference>) -> Unit) {
    onLiteralsChangedListeners.add(listener = listener)
    val listenerWeakRef = WeakReference(listener)
    Disposer.register(parentDisposable) {
      onLiteralsChangedListeners.remove(listenerWeakRef.get() ?: return@register)
    }
  }

  @Synchronized
  private fun onDocumentsUpdated(document: Collection<Document>, @Suppress("UNUSED_PARAMETER") lastUpdateNanos: Long) {
    val updateList = ArrayList<LiteralReference>()
    document.flatMap {
      documentSnapshots[it]?.modified ?: emptyList()
    }.forEach {
      val constantValue = it.constantValue ?: return@forEach
      it.usages.forEach { elementPath ->
        val constantModified = ConstantRemapperManager.getConstantRemapper().addConstant(
          null, elementPath, it.initialConstantValue, constantValue)
        log.debug("[${it.uniqueId}] Constant updated to ${it.text} path=${elementPath}")
        if (constantModified) {
          updateList.add(it)
        }
      }
    }

    if (!updateList.isEmpty()) {
      fireOnLiteralsChanged(updateList)
    }
  }

  /**
   * Adds a new document to the tracking. The document will be observed for changes.
   */
  private fun addDocumentTracking(parentDisposable: Disposable, editor: Editor, document: Document) {
    val file = AndroidPsiUtils.getPsiFileSafely(project, document) ?: return
    val fileSnapshot = literalsManager.findLiterals(file)

    if (fileSnapshot.all.isNotEmpty()) {
      documentSnapshots[document] = fileSnapshot

      Disposer.register(parentDisposable) {
        documentSnapshots.remove(document)
      }
    }

    val tracker = HighlightTracker(file, editor, fileSnapshot)
    trackers.add(tracker)
    Disposer.register(parentDisposable, tracker)
    editor.addEditorMouseListener(object : EditorMouseListener {
      override fun mouseEntered(event: EditorMouseEvent) {
        if (showLiveLiteralsHighlights) {
          tracker.showHighlights()
        }
      }

      override fun mouseExited(event: EditorMouseEvent) {
        tracker.hideHighlights()
      }
    }, parentDisposable)

    // If the mouse is already within the editor hover area, activate the highlights
    if (GraphicsEnvironment.isHeadless() || editor.component.mousePosition != null) {
      UIUtil.invokeAndWaitIfNeeded(Runnable {
        if (showLiveLiteralsHighlights) {
          tracker.showHighlights()
        }
      })
    }
  }

  private fun refreshHighlightTrackersVisibility() = UIUtil.invokeAndWaitIfNeeded(Runnable {
    trackers.forEach {
      if (showLiveLiteralsHighlights) {
        it.showHighlights()
      }
      else {
        it.hideHighlights()
      }
    }
  })

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
  private fun deactivate() {
    trackers.clear()
    ConstantRemapperManager.getConstantRemapper().clearConstants(null)
    activationDisposable?.let {
      Disposer.dispose(it)
    }
    activationDisposable = null
    fireOnLiteralsChanged(emptyList())
  }

  override fun dispose() {
    isEnabled = false
    deactivate()
  }
}