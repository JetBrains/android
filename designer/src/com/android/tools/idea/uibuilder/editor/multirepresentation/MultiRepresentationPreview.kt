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
package com.android.tools.idea.uibuilder.editor.multirepresentation

import com.android.annotations.concurrency.GuardedBy
import com.android.tools.adtui.actions.DropDownAction
import com.android.tools.adtui.common.AdtPrimaryPanel
import com.android.tools.adtui.util.ActionToolbarUtil
import com.android.tools.idea.common.editor.DesignFileEditor
import com.android.tools.idea.concurrency.AndroidCoroutinesAware
import com.android.tools.idea.concurrency.AndroidDispatchers.uiThread
import com.android.tools.idea.concurrency.AndroidDispatchers.workerThread
import com.intellij.openapi.actionSystem.ActionGroup
import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.DefaultActionGroup
import com.intellij.openapi.actionSystem.PlatformCoreDataKeys.FILE_EDITOR
import com.intellij.openapi.actionSystem.impl.ActionToolbarImpl
import com.intellij.openapi.actionSystem.toolbarLayout.ToolbarLayoutStrategy
import com.intellij.openapi.application.invokeAndWaitIfNeeded
import com.intellij.openapi.application.readAction
import com.intellij.openapi.application.runReadAction
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.diagnostic.debug
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.event.CaretEvent
import com.intellij.openapi.editor.event.CaretListener
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.fileEditor.FileEditorState
import com.intellij.openapi.fileEditor.FileEditorStateLevel
import com.intellij.openapi.fileEditor.TextEditorWithPreview
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.util.ThrowableComputable
import com.intellij.psi.PsiFile
import com.intellij.psi.SmartPointerManager
import com.intellij.util.SlowOperations
import com.intellij.util.xmlb.annotations.Attribute
import com.intellij.util.xmlb.annotations.MapAnnotation
import com.intellij.util.xmlb.annotations.Tag
import com.jetbrains.rd.util.first
import icons.StudioIcons
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import org.jetbrains.annotations.TestOnly
import org.jetbrains.annotations.VisibleForTesting
import java.awt.BorderLayout
import java.util.concurrent.CancellationException
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.locks.ReentrantLock
import javax.swing.BorderFactory
import javax.swing.JComponent
import kotlin.concurrent.withLock

/** Tag name used to persist the multi preview state. */
internal const val MULTI_PREVIEW_STATE_TAG = "multi-preview-state"

/** Type for [PreviewRepresentation]s to store their settings as key/value pairs. */
typealias PreviewRepresentationState = Map<String, String>

/** Bean for saving representation states. */
@Tag("representation")
data class Representation(
  @Attribute("name") var key: RepresentationName = "",
  @Tag("settings")
  @MapAnnotation(
    entryTagName = "setting",
    keyAttributeName = "name",
    valueAttributeName = "value",
    surroundWithTag = false,
  )
  var settings: PreviewRepresentationState = mutableMapOf(),
)

/**
 * [FileEditorState] for [MultiRepresentationPreview]. It saves the state of the individual
 * [PreviewRepresentation]s and restore the state of each one.
 */
@Tag(MULTI_PREVIEW_STATE_TAG)
data class MultiRepresentationPreviewFileEditorState(
  @Attribute("selected") var selectedRepresentationName: RepresentationName = "",
  @Tag("representations") var representations: Collection<Representation> = mutableListOf(),
) : FileEditorState {
  override fun canBeMergedWith(otherState: FileEditorState, level: FileEditorStateLevel): Boolean =
    otherState is MultiRepresentationPreviewFileEditorState && this == otherState

  companion object {
    val INSTANCE = MultiRepresentationPreviewFileEditorState("", listOf())
  }
}

/**
 * A generic preview [com.intellij.openapi.fileEditor.FileEditor] that allows you to switch between
 * different [PreviewRepresentation]s.
 *
 * @param psiFile the file being edited by this editor.
 * @param editor the text [Editor] for the file.
 * @param providers list of [PreviewRepresentationProvider] for this file type.
 */
open class MultiRepresentationPreview(
  psiFile: PsiFile,
  private val editor: Editor,
  private val providers: Collection<PreviewRepresentationProvider>,
) : PreviewRepresentationManager, DesignFileEditor(psiFile.virtualFile!!), AndroidCoroutinesAware {

  private val LOG = Logger.getInstance(MultiRepresentationPreview::class.java)
  /** Id identifying this MultiRepresentationPreview to be used in logging */
  private val instanceId = psiFile.virtualFile.presentableName

  private val project = psiFile.project
  private val psiFilePointer = runReadAction { SmartPointerManager.createPointer(psiFile) }
  private var shortcutsApplicableComponent: JComponent? = null

  private var representationNeverShown = true

  /** [MultiRepresentationPreviewFileEditorState] deserialized from disk, if any. */
  private var stateFromDisk: MultiRepresentationPreviewFileEditorState? = null

  private val representations: MutableMap<RepresentationName, PreviewRepresentation> =
    mutableMapOf()

  override val currentRepresentation: PreviewRepresentation?
    get() = synchronized(representations) { representations[currentRepresentationName] }

  /** true if the [currentRepresentation] has been activated. */
  private val currentRepresentationIsActive = AtomicBoolean(false)

  override val representationNames: List<RepresentationName>
    get() = synchronized(representations) { representations.keys.sorted() }

  // It is a client's responsibility to set a correct (valid) value of the currentRepresentationName
  override var currentRepresentationName: RepresentationName = ""
    set(value) {
      if (field != value) {
        if (currentRepresentationIsActive.get()) {
          currentRepresentation?.onDeactivate()
        }
        field = value

        onRepresentationChanged()
        if (isActive.get()) {
          LOG.debug { "[$instanceId] Activating '$value'" }
          currentRepresentation?.onActivate()
          currentRepresentationIsActive.set(true)
        } else {
          // The preview is not active so mark the current representation as not-active
          currentRepresentationIsActive.set(false)
          LOG.debug {
            "[$instanceId] Did not activate '$value' since the MultiRepresentationPreview is not active."
          }
        }
      }
    }

  /**
   * [AtomicBoolean] to track activations. Indicates whether the current preview is active. If
   * false, the preview might be hidden or in the background.
   */
  private val isActive = AtomicBoolean(false)

  private val caretListener =
    object : CaretListener {
      /**
       * This tracks the last time the file was modified. This allows us to infer if the caret moved
       * because the user was typing or just moving around.
       */
      var lastEditorModificationStamp = -1L

      override fun caretPositionChanged(event: CaretEvent) {
        val newStamp = event.editor.document.modificationStamp

        val isModificationTriggered =
          if (newStamp != -1L && lastEditorModificationStamp != newStamp) {
            lastEditorModificationStamp = newStamp
            true
          } else false

        currentRepresentation?.onCaretPositionChanged(event, isModificationTriggered)
      }
    }

  private val representationSelectionToolbar: JComponent by lazy {
    AdtPrimaryPanel(BorderLayout()).apply {
      border = BorderFactory.createMatteBorder(0, 0, 1, 0, com.android.tools.adtui.common.border)

      isVisible = false
      add(createActionToolbar(createActionGroup()))
    }
  }

  private class UpdateRepresentationsRequest

  private val updateRepresentationsFlow: MutableStateFlow<UpdateRepresentationsRequest?> =
    MutableStateFlow(null)

  private val updateCallbacksLock = ReentrantLock()
  @GuardedBy("updateCallbacksLock") private var allowNewUpdateCallbacks = true
  @GuardedBy("updateCallbacksLock")
  private val updateCallbacks: MutableSet<CompletableDeferred<Unit>> = mutableSetOf()
  // Indicates that representation is potentially being updated. Used for tracking end of processing
  // in tests.
  private val isUpdating = AtomicBoolean(false)

  init {
    launch(workerThread) {
      updateRepresentationsFlow.collect { request ->
        if (request == null) return@collect
        isUpdating.set(true)
        val callbacks = updateCallbacksLock.withLock { updateCallbacks.toSet() }
        try {
          updateRepresentationsImpl()
          updateCallbacksLock.withLock { updateCallbacks.removeAll(callbacks) }
          callbacks.forEach { it.complete(Unit) }
        } catch (ex: CancellationException) {
          throw ex
        } catch (t: Throwable) {
          LOG.error("Unexpected error while updating representations", t)
        } finally {
          isUpdating.set(false)
        }
      }
    }
  }

  private fun onRepresentationChanged() = invokeAndWaitIfNeeded {
    component.removeAll()

    component.add(representationSelectionToolbar, BorderLayout.NORTH)

    currentRepresentation?.let { component.add(it.component, BorderLayout.CENTER) }

    representationNeverShown = false
  }

  private fun updateCurrentRepresentationName() {
    synchronized(representations) {
      if (representations.isEmpty()) {
        currentRepresentationName = ""
        return@synchronized
      }
      val representationsWithPreviews = representations.filter { it.value.hasPreviewsCached() }
      if (currentRepresentationName !in representationsWithPreviews.keys) {
        // Prefer selecting a representation with previews.
        currentRepresentationName =
          if (representationsWithPreviews.isNotEmpty()) {
            representationsWithPreviews.first().key
          } else {
            representationNames.minOf { it }
          }
      }
    }
    if (representationNeverShown) {
      onRepresentationChanged()
    }
  }

  /** Updates the current representations and ensures the current selected one is valid. */
  private suspend fun updateRepresentationsImpl() {
    if (Disposer.isDisposed(this@MultiRepresentationPreview)) return
    val file = readAction { psiFilePointer.element?.takeIf { it.isValid } } ?: return

    val providers = providers.filter { it.accept(project, file) }.toList()
    val currentRepresentationsNames = synchronized(representations) { representations.keys.toSet() }
    val newRepresentations = mutableMapOf<RepresentationName, PreviewRepresentation>()
    // Calculated new representations
    for (provider in providers.filter { it.displayName !in currentRepresentationsNames }) {
      val representation = provider.createRepresentation(file)
      if (!Disposer.tryRegister(this@MultiRepresentationPreview, representation)) {
        Disposer.dispose(representation)
        return
      }
      shortcutsApplicableComponent?.let {
        launch(uiThread) {
          SlowOperations.allowSlowOperations(
            ThrowableComputable {
              if (!Disposer.isDisposed(representation)) representation.registerShortcuts(it)
            }
          )
        }
      }
      newRepresentations[provider.displayName] = representation

      // Restore the state of the representation
      stateFromDisk
        ?.representations
        ?.find { it.key == provider.displayName }
        ?.let { representation.setState(it.settings) }
    }

    val providerNames = providers.map { it.displayName }.toSet()
    val hadAnyRepresentationsInitialized: Boolean
    val toDispose = mutableListOf<PreviewRepresentation>()
    synchronized(representations) {
      // Remove unaccepted
      (representations.keys - (providerNames - newRepresentations.keys)).forEach { name ->
        representations.remove(name)?.let { toDispose.add(it) }
      }
      hadAnyRepresentationsInitialized = representations.isNotEmpty()
      representations.putAll(newRepresentations)
    }
    toDispose.forEach { representation ->
      withContext(uiThread) { Disposer.dispose(representation) }
    }

    if (!hadAnyRepresentationsInitialized) {
      // The first time we load one representation, we try to set it to the one we had saved on disk
      // when saving the state.
      stateFromDisk?.let { currentRepresentationName = it.selectedRepresentationName }
    }
    // Updates the cached value of hasPreviews for each representation
    withContext(workerThread) { representations.values.forEach { it.hasPreviews() } }

    withContext(uiThread) {
      // update current if it was deleted
      updateCurrentRepresentationName()

      // Only show the bar if there is more than one representation with previews to be shown.
      representationSelectionToolbar.isVisible =
        representations.filter { it.value.hasPreviewsCached() }.size > 1
      onRepresentationsUpdated?.invoke()
    }
  }

  /**
   * Updates the representations and returns a [Deferred] that will be completed when the update is
   * done. To be used in the derived classes.
   */
  protected fun updateRepresentationsAsync(): Deferred<Unit> {
    val promise = CompletableDeferred<Unit>()
    updateCallbacksLock.withLock {
      if (allowNewUpdateCallbacks) {
        updateCallbacks.add(promise)
      } else {
        return CompletableDeferred(Unit)
      }
    }
    runBlocking { updateRepresentationsFlow.emit(UpdateRepresentationsRequest()) }
    return promise
  }

  /** Waits for the current ongoing representations update to complete. */
  @TestOnly
  suspend fun awaitForRepresentationsUpdated() {
    while (updateCallbacksLock.withLock { updateCallbacks.isNotEmpty() } || isUpdating.get()) {
      delay(100)
    }
  }

  var onRepresentationsUpdated: (() -> Unit)? = null

  override fun updateNotifications() {
    synchronized(representations) { representations.values.toList() }
      .forEach { it.updateNotifications(this) }
  }

  fun registerShortcuts(appliedTo: JComponent) {
    shortcutsApplicableComponent = appliedTo
    synchronized(representations) { representations.values.toList() }
      .forEach { it.registerShortcuts(appliedTo) }
  }

  @VisibleForTesting
  suspend fun setStateAndUpdateRepresentations(state: MultiRepresentationPreviewFileEditorState) {
    if (stateFromDisk != null) return
    stateFromDisk = state

    // For any already loaded representations, restore the state.
    synchronized(representations) {
      representations.forEach { (representationName, representation) ->
        state.representations
          .find { it.key == representationName }
          ?.let { representation.setState(it.settings) }
      }
    }

    updateRepresentationsAsync().await()

    // If the representation is available, restore
    if (representations.containsKey(state.selectedRepresentationName)) {
      currentRepresentationName = state.selectedRepresentationName
    }
  }

  override fun setState(state: FileEditorState) {
    (state as? MultiRepresentationPreviewFileEditorState?)?.let {
      launch { setStateAndUpdateRepresentations(it) }
    }
  }

  override fun getState(level: FileEditorStateLevel): MultiRepresentationPreviewFileEditorState {
    val representationStates =
      representations
        .mapNotNull { (name, representation) ->
          if (name.isEmpty()) return@mapNotNull null
          val settings = representation.getState() ?: return@mapNotNull null
          Representation(name, settings)
        }
        .toList()

    return MultiRepresentationPreviewFileEditorState(
      currentRepresentationName,
      representationStates,
    )
  }

  /*
   * The code below is responsible for creating a toolbar for selecting current [PreviewRepresentation]. The toolbar appears only if there
   * are more than 1 [PreviewRepresentation] is available (accepted) for the current file.
   */
  private fun findAllPreviews() =
    FileEditorManager.getInstance(project)
      .getAllEditors(file)
      .asIterable()
      .filterIsInstance<TextEditorWithMultiRepresentationPreview<out MultiRepresentationPreview>>()
      .map { it.preview }
      .distinct()

  private fun createActionToolbar(group: ActionGroup): ActionToolbarImpl {
    val toolbar = ActionManager.getInstance().createActionToolbar("top", group, true)
    toolbar.targetComponent = component
    toolbar.layoutStrategy = ToolbarLayoutStrategy.WRAP_STRATEGY
    if (group === ActionGroup.EMPTY_GROUP) {
      toolbar.component.isVisible = false
    }
    ActionToolbarUtil.makeToolbarNavigable(toolbar)
    return toolbar as ActionToolbarImpl
  }

  private class RepresentationOption(
    val representationName: String,
    val parent: MultiRepresentationPreview,
  ) : AnAction(representationName) {
    override fun actionPerformed(e: AnActionEvent) {
      // Here we iterate over all editors as change in selection (write) should trigger updates in
      // all of them
      parent.findAllPreviews().forEach { it.currentRepresentationName = representationName }
    }
  }

  private class RepresentationsSelector :
    DropDownAction(null, "Representations", StudioIcons.LayoutEditor.Palette.LIST_VIEW) {
    override fun update(e: AnActionEvent) {
      super.update(e)
      removeAll()
      val previewEditor =
        (e.getData(FILE_EDITOR) as? TextEditorWithPreview)?.previewEditor
          as? MultiRepresentationPreview ?: return

      // We need just a single previewEditor here (any) to retrieve (read) the states and currently
      // selected state
      synchronized(previewEditor.representations) { previewEditor.representations }
        .forEach {
          if (it.value.hasPreviewsCached()) {
            add(RepresentationOption(it.key, previewEditor))
          }
        }
      e.presentation.setText(previewEditor.currentRepresentationName, false)
    }

    override fun displayTextInToolbar() = true

    override fun getActionUpdateThread() = ActionUpdateThread.BGT
  }

  private fun createActionGroup(): ActionGroup {
    val actionGroup = DefaultActionGroup()
    val representationsSelector = RepresentationsSelector()
    actionGroup.add(representationsSelector)
    return actionGroup
  }

  /**
   * Method called before [onActivate] to initialize the representations. This method will only be
   * called once while [onActivate] and [onDeactivate] might be called multiple times.
   */
  suspend fun onInit() {
    updateRepresentationsAsync().await()
  }

  /** Method called when this preview becomes active. */
  fun onActivate() {
    if (isActive.getAndSet(true)) return

    if (!currentRepresentationIsActive.getAndSet(true)) {
      LOG.debug { "[$instanceId] Activating '$currentRepresentationName'" }
      currentRepresentation?.onActivate()
    }

    caretListener.lastEditorModificationStamp = editor.document.modificationStamp
    editor.caretModel.addCaretListener(caretListener, this)
  }

  /**
   * Method called when this preview becomes deactivated. Updates will not be processed un the next
   * [onActivate].
   */
  fun onDeactivate() {
    if (!isActive.getAndSet(false)) return
    currentRepresentationIsActive.set(false)
    LOG.debug { "[$instanceId] Deactivating '$currentRepresentationName'" }
    editor.caretModel.removeCaretListener(caretListener)
    currentRepresentation?.onDeactivate()
  }

  override fun dispose() {
    updateCallbacksLock.withLock {
      allowNewUpdateCallbacks = false
      updateCallbacks.forEach { it.complete(Unit) }
      updateCallbacks.clear()
    }
    onDeactivate()
    synchronized(representations) { representations.clear() }
  }
}
