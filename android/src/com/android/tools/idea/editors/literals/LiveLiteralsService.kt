package com.android.tools.idea.editors.literals

import com.android.annotations.concurrency.GuardedBy
import com.android.annotations.concurrency.UiThread
import com.android.tools.idea.AndroidPsiUtils
import com.android.tools.idea.concurrency.AndroidCoroutineScope
import com.android.tools.idea.concurrency.AndroidDispatchers.uiThread
import com.android.tools.idea.concurrency.AndroidDispatchers.workerThread
import com.android.tools.idea.editors.literals.internal.LiveLiteralsDeploymentReportService
import com.android.tools.idea.editors.liveedit.LiveEditApplicationConfiguration
import com.android.tools.idea.editors.powersave.PreviewPowerSaveManager.isInPowerSaveMode
import com.android.tools.idea.editors.setupChangeListener
import com.android.tools.idea.flags.StudioFlags
import com.android.tools.idea.projectsystem.BuildListener
import com.android.tools.idea.projectsystem.setupBuildListener
import com.android.tools.idea.rendering.classloading.ProjectConstantRemapper
import com.android.tools.idea.util.ListenerCollection
import com.android.utils.reflection.qualifiedName
import com.intellij.codeInsight.highlighting.HighlightManager
import com.intellij.openapi.Disposable
import com.intellij.openapi.components.Service
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.editor.Document
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.EditorFactory
import com.intellij.openapi.editor.colors.TextAttributesKey
import com.intellij.openapi.editor.event.EditorMouseEvent
import com.intellij.openapi.editor.event.EditorMouseListener
import com.intellij.openapi.editor.markup.RangeHighlighter
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.fileEditor.FileEditorManagerEvent
import com.intellij.openapi.fileEditor.FileEditorManagerListener
import com.intellij.openapi.fileEditor.FileEditorManagerListener.FILE_EDITOR_MANAGER
import com.intellij.openapi.fileEditor.TextEditor
import com.intellij.openapi.progress.ProcessCanceledException
import com.intellij.openapi.project.DumbService
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.util.Key
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.util.concurrency.AppExecutorUtil
import com.intellij.util.containers.WeakList
import com.intellij.util.messages.Topic
import com.intellij.util.ui.UIUtil
import com.intellij.util.ui.update.MergingUpdateQueue
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.jetbrains.annotations.TestOnly
import java.awt.GraphicsEnvironment
import java.lang.ref.WeakReference
import java.util.concurrent.Executor
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.withLock

val LITERAL_TEXT_ATTRIBUTE_KEY = TextAttributesKey.createTextAttributesKey("LiveLiteralsHighlightAttribute")

/**
 * Time used to coalesce multiple changes without triggering onLiteralsHaveChanged calls.
 */
private val DOCUMENT_CHANGE_COALESCE_TIME_MS = StudioFlags.COMPOSE_LIVE_LITERALS_UPDATE_RATE

/**
 * Interface implementing by services handling live literals.
 */
interface LiveLiteralsMonitorHandler {
  /**
   * Type of device being used.
   */
  enum class DeviceType {
    UNKNOWN,

    /** Not a real device. Studio Compose Preview. */
    PREVIEW,
    /** An emulator. */
    EMULATOR,
    /** A device connected to Studio. */
    PHYSICAL
  }

  /**
   * Describes a problem found during deployment.
   * @param severity Severity of the problem.
   * @param content Description of the problem.
   */
  data class Problem(val severity: Severity, val content: String) {
    enum class Severity {
      INFO,
      WARNING,
      ERROR
    }

    companion object {
      fun info(content: String) = Problem(Severity.INFO, content)
      fun warn(content: String) = Problem(Severity.WARNING, content)
      fun error(content: String) = Problem(Severity.ERROR, content)
    }
  }

  /**
   * Call this method when the deployment for [deviceId] has started. This will clear all current registered
   * [Problem]s for that device.
   */
  fun liveLiteralsMonitorStarted(deviceId: String, deviceType: DeviceType)

  /**
   * Call this method when the monitoring for [deviceId] has stopped. For example, if the application has stopped.
   */
  fun liveLiteralsMonitorStopped(deviceId: String)

  /**
   * Call this method when the deployment of live literals has started. The pushId allows to correlate the start with the end of a push.
   */
  fun liveLiteralPushStarted(deviceId: String, pushId: String)

  /**
   * Call this method when the deployment for [deviceId] has finished. [problems] includes a list
   * of the problems found while deploying literals. The pushId allows to correlate the start with the end of a push.
   */
  fun liveLiteralPushed(deviceId: String, pushId: String, problems: Collection<Problem> = listOf())
}

/**
 * Project service to track live literals. The service, when [isAvailable] is true, will listen for changes of constants
 * and will notify listeners.
 *
 * @param project the project this service is attached to.
 * @param listenerExecutor executor to run the listener calls on.
 */
@Service
class LiveLiteralsService private constructor(private val project: Project,
                                              listenerExecutor: Executor,
                                              val deploymentReportService: LiveLiteralsDeploymentReportService) : LiveLiteralsMonitorHandler, Disposable {
  init {
    deploymentReportService.subscribe(this@LiveLiteralsService, object : LiveLiteralsDeploymentReportService.Listener {
      override fun onMonitorStarted(deviceId: String) {
        onAvailabilityChange()
      }

      override fun onMonitorStopped(deviceId: String) {
        onAvailabilityChange()
      }

      override fun onLiveLiteralsPushed(deviceId: String) {}
    })
  }

  constructor(project: Project) : this(project, AppExecutorUtil.createBoundedApplicationPoolExecutor("Document changed listeners executor", 1),
                                       LiveLiteralsDeploymentReportService.getInstance(project))

  /**
   * Class that groups all the highlighters for a given file/editor combination. This allows enabling/disabling them.
   */
  @UiThread
  private inner class HighlightTracker(
    file: PsiFile,
    editor: Editor,
    private val fileSnapshot: LiteralReferenceSnapshot) : Disposable {
    private val project = file.project
    private var showingHighlights = false
    private val outHighlighters = mutableSetOf<RangeHighlighter>()

    private val editorRef = WeakReference(editor)

    private val _isDisposed = AtomicBoolean(false)
    val isDisposed: Boolean
      get() = _isDisposed.get()

    private fun clearAll() {
      if (project.isDisposed) return
      val highlightManager = HighlightManager.getInstance(project)
      val highlightersToRemove = outHighlighters.toSet()
      outHighlighters.clear()
      editorRef.get()?.let { editor ->
        UIUtil.invokeLaterIfNeeded {
          highlightersToRemove.forEach { highlightManager.removeSegmentHighlighter(editor, it) }
        }
      }
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
        editorRef.get()?.let { editor ->
          fileSnapshot.highlightSnapshotInEditor(project, editor, LITERAL_TEXT_ATTRIBUTE_KEY, outHighlighters) {
            it.containingFile.hasCompilerLiveLiteral(it.initialTextRange.startOffset)
          }
        }

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
      _isDisposed.set(true)
      hideHighlights()
    }
  }

  /**
   * Listener that gets notified if there's been a change in which elements are now handled by this service for a given file.
   * This allows to refresh any user of the [isElementManaged] method to let them know there might have been a change.
   */
  fun interface ManagedElementsUpdatedListener {
    fun onChange(file: PsiFile)
  }

  /**
   * Listener that gets notified when a document begins being tracked.
   */
  fun interface DocumentsUpdatedListener {
    fun onAdded(document: Document)
  }

  companion object {
    private val MANAGED_ELEMENTS_UPDATED_TOPIC: Topic<ManagedElementsUpdatedListener> = Topic.create("Managed elements updated",
                                                                                                     ManagedElementsUpdatedListener::class.java)
    private val DOCUMENTS_UPDATED_TOPIC: Topic<DocumentsUpdatedListener> = Topic.create("Documents updated",
                                                                                        DocumentsUpdatedListener::class.java)
    private val COMPILER_LITERALS_FINDER: Key<CompilerLiveLiteralsManager.Finder> = Key.create(
      Companion::COMPILER_LITERALS_FINDER.qualifiedName)
    private val DOCUMENT_SNAPSHOT_KEY: Key<LiteralReferenceSnapshot> = Key.create(Companion::DOCUMENT_SNAPSHOT_KEY.qualifiedName)

    private fun Document.getCachedDocumentSnapshot() = getUserData(DOCUMENT_SNAPSHOT_KEY)
    private fun Document.putCachedDocumentSnapshot(snapshot: LiteralReferenceSnapshot) = putUserData(DOCUMENT_SNAPSHOT_KEY, snapshot)
    private fun Document.clearCachedDocumentSnapshot() = putUserData(DOCUMENT_SNAPSHOT_KEY, null)

    @JvmStatic
    fun getInstance(project: Project): LiveLiteralsService = project.getService(LiveLiteralsService::class.java)

    private fun PsiFile?.hasCompilerLiveLiteral(offset: Int) =
      this?.getUserData(COMPILER_LITERALS_FINDER)?.hasCompilerLiveLiteral(this, offset) ?: true
  }

  private val log = Logger.getInstance(LiveLiteralsService::class.java)

  /**
   * If true, the highlights will be shown. This must be only changed from the UI thread.
   */
  @set:UiThread
  @get:UiThread
  var showLiveLiteralsHighlights = false
    set(value) {
      field = value
      refreshHighlightTrackersVisibility()
    }

  /**
   * Link to all instantiated [HighlightTracker]s. This allows to switch them on/off via the [ToggleLiveLiteralsHighlightsAction].
   * This is a [WeakList] since the trackers will be mainly held by the mouse listener created in [addDocumentTracking].
   */
  private val trackers = WeakList<HighlightTracker>()

  /**
   * [ListenerCollection] for all the listeners that need to be notified when any live literal has changed value.
   */
  private val onLiteralsChangedListeners = ListenerCollection.createWithExecutor<(List<LiteralReference>) -> Unit>(listenerExecutor)

  private val literalsManager = LiteralsManager()
  /** Lock that guards the activation/deactivation of this service. */
  private val serviceStateLock = ReentrantLock()

  /**
   * [Disposable] that tracks the current activation. If the service is deactivated, this [Disposable] will be disposed.
   * It can be used to register anything that should be disposed when the service is not running.
   */
  @GuardedBy("serviceStateLock")
  private var activationDisposable: Disposable? = null

  private val updateMergingQueue = MergingUpdateQueue("Live literals change queue",
                                                      DOCUMENT_CHANGE_COALESCE_TIME_MS.get(),
                                                      true,
                                                      null,
                                                      this,
                                                      null,
                                                      false).setRestartTimerOnAdd(true)

  /**
   * True if Live Literals should be enabled for this project.
   */
  val isEnabled
    get() = LiveEditApplicationConfiguration.getInstance().isLiveLiterals

  /**
   * Controls when the live literals tracking is available for the current project. The feature might be enable but not available if the
   * current project has not any Live Literals yet.
   */
  val isAvailable: Boolean
    get() = isEnabled && deploymentReportService.hasActiveDevices

  /**
   * Returns all the [Editor]s that have a [Document] with a cached [LiteralReferenceSnapshot].
   */
  private val editorWithCachedSnapshot: List<Editor>
    get() = EditorFactory.getInstance().allEditors
      .filter { it.project == project && it.document.getCachedDocumentSnapshot() != null }

  @TestOnly
  fun allConstants(): Collection<LiteralReference> =
    editorWithCachedSnapshot
      .mapNotNull { it.document.getCachedDocumentSnapshot() }
      .flatMap { it.all }

  @TestOnly
  fun allTrackers(): Int = serviceStateLock.withLock {
    trackers.filter { !it.isDisposed }.toList().size
  }

  /**
   * Method called to notify the listeners than a constant has changed.
   */
  private fun fireOnLiteralsChanged(changed: List<LiteralReference>) = onLiteralsChangedListeners.forEach {
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

  /**
   * Called when the availability might have changed, for example, when devices start/stop or when
   * the settings are updated.
   */
  internal fun onAvailabilityChange() {
    if (isAvailable) {
      // Activation must only run when smart
      DumbService.getInstance(project).runWhenSmart {
        activateTracking()
      }
    }
    else {
      deactivateTracking()
    }
  }

  private fun onDocumentsUpdated(document: Collection<Document>, @Suppress("UNUSED_PARAMETER") lastUpdateNanos: Long) {
    if (!isEnabled) {
      log.warn("onDocumentUpdated called for disabled LiveLiteralsService")
      return
    }

    if (isInPowerSaveMode) return

    val updateList = ArrayList<LiteralReference>()
    document.flatMap {
      it.getCachedDocumentSnapshot()?.modified ?: emptyList()
    }.forEach {
      val constantValue = it.constantValue ?: return@forEach
      it.usages.forEach { elementPath ->
        val constantModified = ProjectConstantRemapper.getInstance(project).addConstant(
          null, elementPath, it.initialConstantValue, constantValue)
        log.debug("[${it.uniqueId}] Constant updated to ${it.text} path=${elementPath}")
        if (constantModified) {
          updateList.add(it)
        }
      }
    }

    if (updateList.isNotEmpty()) {
      fireOnLiteralsChanged(updateList)
    }
  }

  private suspend fun newFileSnapshotForDocument(file: PsiFile, document: Document): LiteralReferenceSnapshot = withContext(workerThread) {
    try {
      when(val result = literalsManager.findLiterals(file)) {
        is LiteralsManager.FindResult.Snapshot -> {
          if (result.snapshot.all.isNotEmpty()) {
            document.putCachedDocumentSnapshot(result.snapshot)
            return@withContext result.snapshot
          }
        }
        is LiteralsManager.FindResult.IndexNotReady -> log.debug("Not in smart mode")
        is LiteralsManager.FindResult.Unsupported -> log.debug("File not supported")
      }
    } catch (_: ProcessCanceledException) {
      // After 222.2889.14 the visitor can throw ProcessCanceledException instead of IndexNotReadyException if in dumb mode.
      log.debug("newFileSnapshotForDocument failed with ProcessCanceledException")
    }

    return@withContext EmptyLiteralReferenceSnapshot
  }

  /**
   * Adds a new document to the tracking. The document will be observed for changes.
   */
  private fun addDocumentTracking(activationDisposable: Disposable, textEditor: TextEditor) {
    val editor = textEditor.editor
    if (editor.project != project) return
    if (editor.isViewer) {
      log.info("Editor is view only, no literal tracking will be used.")
      return
    }

    // Create a new Disposable that will dispose if literals are deactivated or if the TextEditor is disposed
    val parentDisposable = Disposer.newDisposable()
    Disposer.register(textEditor) {
      Disposer.dispose(parentDisposable)
    }
    Disposer.register(activationDisposable) {
      Disposer.dispose(parentDisposable)
    }

    val document = textEditor.editor.document
    val file = AndroidPsiUtils.getPsiFileSafely(project, document) ?: return
    AndroidCoroutineScope(parentDisposable).launch(uiThread) {
      val cachedSnapshot: LiteralReferenceSnapshot = document.getCachedDocumentSnapshot() ?: newFileSnapshotForDocument(file, document)
      if (editor.isDisposed || !isActive) return@launch
      val tracker = HighlightTracker(file, editor, cachedSnapshot)

      CompilerLiveLiteralsManager.getInstance().find(file).also {
        file.putUserData(COMPILER_LITERALS_FINDER, it)
        withContext(workerThread) {
          project.messageBus.syncPublisher(MANAGED_ELEMENTS_UPDATED_TOPIC).onChange(file)
        }
      }

      trackers.add(tracker)
      Disposer.register(parentDisposable) {
        Disposer.dispose(tracker)
      }
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

      withContext(workerThread) {
        project.messageBus.syncPublisher(DOCUMENTS_UPDATED_TOPIC).onAdded(document)
      }

      // If the mouse is already within the editor hover area, activate the highlights
      if (GraphicsEnvironment.isHeadless() || editor.component.mousePosition != null) {
        UIUtil.invokeLaterIfNeeded {
          if (showLiveLiteralsHighlights) {
            tracker.showHighlights()
          }
        }
      }
    }
  }

  private fun refreshHighlightTrackersVisibility() = UIUtil.invokeLaterIfNeeded {
    trackers.forEach {
      if (showLiveLiteralsHighlights) {
        it.showHighlights()
      }
      else {
        it.hideHighlights()
      }
    }
  }

  private fun activateTracking() {
    if (Disposer.isDisposed(this) || !isEnabled) return
    log.debug("activateTracking")

    val newActivationDisposable = Disposer.newDisposable()

    // Find all the active editors
    val fileEditorManager = FileEditorManager.getInstance(project)
    fileEditorManager.selectedEditors
      .filterIsInstance<TextEditor>()
      .forEach { textEditor ->
        addDocumentTracking(newActivationDisposable, textEditor)
      }

    project.messageBus.connect(newActivationDisposable).subscribe(FILE_EDITOR_MANAGER, object: FileEditorManagerListener {
      override fun selectionChanged(event: FileEditorManagerEvent) {
        (event.newEditor as? TextEditor)?.let { textEditor ->
          addDocumentTracking(newActivationDisposable, textEditor)
        }
      }
    })

    setupChangeListener(project, ::onDocumentsUpdated, newActivationDisposable, updateMergingQueue)
    setupBuildListener(project, object : BuildListener {
      private var buildStarted = false

      override fun buildSucceeded() {
        if (buildStarted) {
          // The project has built successfully so we can drop the constants that we were keeping.
          ProjectConstantRemapper.getInstance(project).clearConstants(null)
          buildStarted = false
        }
      }

      override fun buildFailed() {
        buildStarted = false
      }

      override fun buildStarted() {
        buildStarted = true
        // Stop the literals listening while the build happens
        deactivateTracking()
      }
    }, newActivationDisposable)

    if (Disposer.tryRegister(this, newActivationDisposable)) {
      serviceStateLock.withLock {
        val previousActivationDisposable = activationDisposable
        activationDisposable = newActivationDisposable

        previousActivationDisposable
      }?.let {
        // Disposes the current activation if already exists
        Disposer.dispose(it)
      }
    }
    else {
      Disposer.dispose(newActivationDisposable)
    }
  }

  private fun deactivateTracking() {
    log.debug("deactivateTracking")

    // Clear all snapshots
    editorWithCachedSnapshot.forEach {
      it.document.clearCachedDocumentSnapshot()
    }

    serviceStateLock.withLock {
      trackers.clear()
      val previousActivationDisposable = activationDisposable
      activationDisposable = null

      previousActivationDisposable
    }?.let {
      // Dispose the previous activation outside of the lock
      Disposer.dispose(it)
    }
  }

  override fun liveLiteralsMonitorStarted(deviceId: String, deviceType: LiveLiteralsMonitorHandler.DeviceType) =
    deploymentReportService.liveLiteralsMonitorStarted(deviceId, deviceType)

  override fun liveLiteralsMonitorStopped(deviceId: String) =
    deploymentReportService.liveLiteralsMonitorStopped(deviceId)

  override fun liveLiteralPushStarted(deviceId: String, pushId: String) =
    deploymentReportService.liveLiteralPushStarted(deviceId, pushId)

  override fun liveLiteralPushed(deviceId: String, pushId: String, problems: Collection<LiveLiteralsMonitorHandler.Problem>) =
    deploymentReportService.liveLiteralPushed(deviceId, pushId, problems)

  fun addOnManagedElementsUpdatedListener(parentDisposable: Disposable, listener: ManagedElementsUpdatedListener) {
    project.messageBus.connect(parentDisposable).subscribe(MANAGED_ELEMENTS_UPDATED_TOPIC, listener)
  }

  /**
   * Adds a new [DocumentsUpdatedListener] that gets notified when the tracked documents are updated.
   */
  @TestOnly
  fun addOnDocumentsUpdatedListener(parentDisposable: Disposable, listener: DocumentsUpdatedListener) {
    project.messageBus.connect(parentDisposable).subscribe(DOCUMENTS_UPDATED_TOPIC, listener)
  }

  /**
   * Returns whether the given [PsiElement] is handled by this service. This will be the case when the element is a constant
   * and the Live Literals are available and the compiler provides metadata for it. See [CompilerLiveLiteralsManager].
   */
  fun isElementManaged(element: PsiElement): Boolean {
    if (!isAvailable) return false

    // Some elements, like directories do not have a containingFile and are safe to ignore.
    val containingFile = element.containingFile ?: return false
    val literalReference = LiteralsManager.getLiteralReference(element) ?: return false
    @Suppress("USELESS_ELVIS") // initialTextRange can be null under certain conditions
    val initialTextRange = literalReference.initialTextRange ?: return false
    return containingFile.hasCompilerLiveLiteral(initialTextRange.startOffset)
  }

  override fun dispose() {
    deactivateTracking()
  }
}