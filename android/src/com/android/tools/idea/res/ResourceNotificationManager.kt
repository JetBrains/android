/*
 * Copyright (C) 2016 The Android Open Source Project
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
package com.android.tools.idea.res

import com.android.SdkConstants
import com.android.annotations.concurrency.GuardedBy
import com.android.resources.ResourceFolderType
import com.android.resources.ResourceUrl
import com.android.tools.configurations.Configuration
import com.android.tools.configurations.ConfigurationListener
import com.android.tools.idea.AndroidPsiUtils
import com.android.tools.idea.model.AndroidModel
import com.android.tools.idea.projectsystem.ProjectSystemBuildManager
import com.android.tools.idea.projectsystem.getProjectSystem
import com.android.tools.idea.res.ResourceNotificationManager.Reason
import com.android.tools.idea.res.ResourceNotificationManager.ResourceChangeListener
import com.android.utils.isBindingExpression
import com.google.common.collect.ImmutableSet
import com.intellij.openapi.Disposable
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.module.Module
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.util.ModificationTracker
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.vfs.VirtualFileManager
import com.intellij.openapi.vfs.newvfs.BulkFileListener
import com.intellij.openapi.vfs.newvfs.events.VFileEvent
import com.intellij.psi.PsiErrorElement
import com.intellij.psi.PsiFile
import com.intellij.psi.PsiTreeChangeEvent
import com.intellij.psi.PsiTreeChangeListener
import com.intellij.psi.PsiWhiteSpace
import com.intellij.psi.xml.XmlAttribute
import com.intellij.psi.xml.XmlAttributeValue
import com.intellij.psi.xml.XmlComment
import com.intellij.psi.xml.XmlTag
import com.intellij.psi.xml.XmlText
import com.intellij.psi.xml.XmlToken
import com.intellij.util.application
import com.intellij.util.concurrency.SameThreadExecutor
import com.intellij.util.concurrency.annotations.RequiresEdt
import com.intellij.util.messages.MessageBusConnection
import java.util.EnumSet
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicLong
import java.util.concurrent.atomic.AtomicReference
import org.jetbrains.android.facet.AndroidFacet
import org.jetbrains.android.facet.ResourceFolderManager
import org.jetbrains.android.facet.ResourceFolderManager.ResourceFolderListener

/**
 * The [ResourceNotificationManager] provides notifications to editors that are displaying Android
 * resources, and need to update themselves when some operation in the IDE edits a resource.
 *
 * **NOTE**: Editors should **only** listen for resource notifications while they are actively
 * showing. They should **not** simply add a listener when created and then continue to listen in
 * the background. A core value of the IDE is to offer a quick editing experience, and if all the
 * potential editors start listening globally for events, this will slow everything down to a crawl.
 *
 * Clients should start listening for resource events when the editor is made visible, and stop
 * listening when the editor is made invisible. Furthermore, when editors are made visible a second
 * time, they can consult a modification stamp to see whether anything changed while they were in
 * the background, and if so update themselves only if that's the case.
 *
 * Also note that
 * * Resource editing events are not delivered synchronously
 * * No locks are held when the listeners are notified
 * * All events are delivered on the event dispatch (UI) thread
 * * Add listener or remove listener can be done from any thread
 */
@Service(Service.Level.PROJECT)
class ResourceNotificationManager private constructor(private val project: Project) :
  Disposable.Default {

  private val observerLock = Any()

  /**
   * Module observers: one per observed module in the project, with potentially multiple listeners.
   */
  @GuardedBy("observerLock")
  private val moduleToObserverMap: MutableMap<Module, ModuleEventObserver> = mutableMapOf()

  /** File observers: one per observed file, with potentially multiple listeners. */
  @GuardedBy("observerLock")
  private val fileToObserverMap: MutableMap<VirtualFile, FileEventObserver> = mutableMapOf()

  /**
   * Configuration observers: one per observed configuration, with potentially multiple listeners.
   */
  @GuardedBy("observerLock")
  private val configurationToObserverMap: MutableMap<Configuration, ConfigurationEventObserver> =
    mutableMapOf()

  /** Whether we've already been notified about a change and we'll be firing it shortly. */
  private val pendingNotify = AtomicBoolean()

  /**
   * Counter for events other than resource repository, configuration or file events. For example,
   * this counts project builds.
   */
  private val modificationCount = AtomicLong()

  private val projectBuildObserver: AtomicReference<ProjectBuildObserver?> = AtomicReference()

  /** Set of events we've observed since the last notification. */
  private var events: EnumSet<Reason> = EnumSet.noneOf(Reason::class.java)

  fun getCurrentVersion(
    facet: AndroidFacet,
    file: PsiFile?,
    configuration: Configuration?,
  ): ResourceVersion {
    val repository = StudioResourceRepositoryManager.getAppResources(facet)
    if (file == null)
      return ResourceVersion(repository.modificationCount, 0L, 0L, 0L, modificationCount.get())

    val fileStamp = file.modificationStamp
    if (configuration == null)
      return ResourceVersion(
        repository.modificationCount,
        fileStamp,
        0L,
        0L,
        modificationCount.get(),
      )

    return ResourceVersion(
      repository.modificationCount,
      fileStamp,
      configuration.modificationCount,
      configuration.settings.stateVersion.toLong(),
      modificationCount.get(),
    )
  }

  /**
   * Registers an interest in resources accessible from the given module.
   *
   * @param listener the listener to notify when there is a resource change
   * @param facet the facet for the Android module whose resources the listener is interested in
   * @param file an optional file to observe for any edits. Note that this is not currently limited
   *   to this listener; all listeners in the module will be notified when there is an edit of this
   *   file.
   * @param configuration if file is not null, this is an optional configuration you can listen for
   *   changes in (must be a configuration corresponding to the file)
   * @return the current resource modification stamp of the given module
   */
  fun addListener(
    listener: ResourceChangeListener,
    facet: AndroidFacet,
    file: VirtualFile?,
    configuration: Configuration?,
  ): ResourceVersion {
    require(configuration == null || file != null) {
      "If configuration is specified, file must be as well. $configuration $file"
    }

    val module = facet.module

    // Whenever we're adding a listener, we want to ensure we're getting build events. Doing this
    // outside the lock below helps prevent any potential deadlocks.
    requireNotNull(
        projectBuildObserver.updateAndGet {
          it ?: ProjectBuildObserver(project, this, modificationCount::incrementAndGet, ::notice)
        }
      )
      .ensureListening()

    val isAndroidFacet = AndroidModel.isRequired(facet)
    if (isAndroidFacet) {
      // Ensure that project resources have been initialized first, since
      // we want all repos to add their own variant listeners before ours (such that
      // when the variant changes, the project resources get notified and updated
      // before our own update listener attempts to re-render).
      StudioResourceRepositoryManager.getProjectResources(facet)
    }

    synchronized(observerLock) {
      val moduleEventObserver =
        moduleToObserverMap.computeIfAbsent(module) { createModuleEventObserver(facet) }
      moduleEventObserver.addListener(listener)

      if (file != null) {
        val fileEventObserver =
          fileToObserverMap.computeIfAbsent(file) { FileEventObserver(module, ::notice) }
        fileEventObserver.addListener(listener)
      }

      if (configuration != null) {
        val configurationEventObserver =
          configurationToObserverMap.computeIfAbsent(configuration) {
            ConfigurationEventObserver(configuration, ::notice)
          }
        configurationEventObserver.addListener(listener)
      }
    }

    if (isAndroidFacet) {
      ResourceFolderManager.getInstance(facet) // Make sure ResourceFolderManager is initialized.
    }

    return getCurrentVersion(
      facet,
      file?.let { AndroidPsiUtils.getPsiFileSafely(project, it) },
      configuration,
    )
  }

  /**
   * Registers an interest in resources accessible from the given module.
   *
   * @param listener the listener to notify when there is a resource change
   * @param facet the facet for the Android module whose resources the listener is interested in
   * @param file the file passed in to the corresponding [.addListener] call
   * @param configuration the configuration passed in to the corresponding [.addListener] call
   */
  fun removeListener(
    listener: ResourceChangeListener,
    facet: AndroidFacet,
    file: VirtualFile?,
    configuration: Configuration?,
  ) {
    require(configuration == null || file != null) {
      "If configuration is specified, file must be as well. $configuration $file"
    }

    val toDispose: MutableList<Disposable> = mutableListOf()

    synchronized(observerLock) {
      if (configuration != null) {
        val configurationEventObserver = configurationToObserverMap[configuration]
        if (configurationEventObserver != null) {
          configurationEventObserver.removeListener(listener)
          if (!configurationEventObserver.hasListeners()) {
            configurationToObserverMap.remove(configuration)
          }
        }
      }

      if (file != null) {
        val fileEventObserver = fileToObserverMap[file]
        if (fileEventObserver != null) {
          fileEventObserver.removeListener(listener)
          if (!fileEventObserver.hasListeners()) {
            fileToObserverMap.remove(file)
          }
        }
      }

      val moduleEventObserver = moduleToObserverMap[facet.module]
      if (moduleEventObserver != null) {
        moduleEventObserver.removeListener(listener)
        if (!moduleEventObserver.hasListeners()) {
          Disposer.dispose(moduleEventObserver)
          if (moduleToObserverMap.isEmpty()) {
            val oldProjectBuildObserver = projectBuildObserver.getAndSet(null)
            if (oldProjectBuildObserver != null) {
              oldProjectBuildObserver.stopListening()
              toDispose.add(oldProjectBuildObserver)
            }
          }
        }
      }
    }

    for (disposable in toDispose) {
      Disposer.dispose(disposable)
    }
  }

  val psiListener: PsiTreeChangeListener? = ProjectPsiTreeObserver(::notice, ::isRelevantFile)
    /**
     * Returns an implementation of [PsiTreeChangeListener] that is not registered and is used as a
     * delegate (e.g in [AndroidPsiTreeChangeListener]).
     *
     * If no listener has been added to the [ResourceNotificationManager], this method returns null.
     */
    get() = field.takeIf { synchronized(observerLock) { moduleToObserverMap.isNotEmpty() } }

  /**
   * Something happened. Either schedule a notification or if one is already pending, do nothing.
   */
  private fun notice(reason: Reason, source: VirtualFile?) {
    events.add(reason)
    if (!pendingNotify.compareAndSet(false, true)) return

    application.invokeLater {
      if (!pendingNotify.compareAndSet(true, false)) return@invokeLater

      if (source == null) {
        // Ensure that the notification happens after all pending Swing Runnables
        // have been processed, including any created *after* the initial notice()
        // call which scheduled this runnable, since they could for example be
        // ResourceFolderRepository.rescan() Runnables, and we want those to finish
        // before the final notify. Notice how we clear the pending notify flag
        // above though, such that if another event appears between the first
        // invoke later and the second, it will schedule another complete notification
        // event.
        scheduleFinalNotification()
      } else {
        application.runWriteAction {
          scheduleFinalNotificationAfterRepositoriesHaveBeenUpdated(source)
        }
      }
    }
  }

  private fun scheduleFinalNotificationAfterRepositoriesHaveBeenUpdated(source: VirtualFile) {
    // The following code calls scheduleFinalNotification exactly once after the
    // dispatchToRepositories call returns and all callbacks passed to runAfterPendingUpdatesFinish
    // are called. To avoid calling scheduleFinalNotification prematurely, the initial value of
    // count is set to 1. This guarantees that it stays positive until the dispatchToRepositories
    // method returns.
    val count = AtomicInteger(1)
    ResourceFolderRegistry.getInstance(project).dispatchToRepositories(source) { repository, _ ->
      count.incrementAndGet()
      repository.invokeAfterPendingUpdatesFinish(SameThreadExecutor.INSTANCE) {
        if (count.decrementAndGet() == 0) {
          scheduleFinalNotification()
        }
      }
    }
    if (count.decrementAndGet() == 0) {
      scheduleFinalNotification()
    }
  }

  private fun scheduleFinalNotification() {
    application.invokeLater {
      val reason = ImmutableSet.copyOf(events)
      events = EnumSet.noneOf(Reason::class.java)
      notifyListeners(reason)
      events.clear()
    }
  }

  @RequiresEdt
  private fun notifyListeners(reason: ImmutableSet<Reason>) {
    application.assertIsDispatchThread()

    val observers = synchronized(observerLock) { ArrayList(moduleToObserverMap.values) }

    // Not every module may have pending changes; each one will check.
    for (observer in observers) observer.notifyListeners(reason)
  }

  private fun createModuleEventObserver(facet: AndroidFacet) =
    ModuleEventObserver(facet, modificationCount::incrementAndGet, ::notice).also { observer ->
      Disposer.register(observer) {
        synchronized(observerLock) { moduleToObserverMap.remove(facet.module) }
      }
    }

  /** Checks if the file is present in [fileToObserverMap]. */
  private fun isRelevantFile(virtualFile: VirtualFile?) =
    virtualFile != null && synchronized(observerLock) { fileToObserverMap.containsKey(virtualFile) }

  /**
   * Interface that should be implemented by clients interested in resource edits and events that
   * affect resources.
   */
  fun interface ResourceChangeListener {
    /**
     * One or more resources have changed.
     *
     * @param reason the set of reasons that the resources have changed since the last notification
     */
    fun resourcesChanged(reason: ImmutableSet<Reason>)
  }

  /**
   * A version timestamp of the resources. This snapshot version is immutable, so you can hold on to
   * it and compare it with your most recent version.
   */
  data class ResourceVersion(
    private val resourceGeneration: Long,
    private val fileGeneration: Long,
    private val configurationGeneration: Long,
    private val projectConfigurationGeneration: Long,
    private val otherGeneration: Long,
  )

  /** The reason the resources have changed. */
  enum class Reason {
    /**
     * An edit which affects the resource repository was performed (e.g. changing the value of a
     * string is a resource edit, but editing the layout parameters of a widget in a layout file is
     * not).
     */
    RESOURCE_EDIT,

    /**
     * Edit of a file that is being observed (if you're for example watching a menu file, this will
     * include edits in whitespace etc.
     */
    EDIT,

    /** The configuration changed (for example, the locale may have changed). */
    CONFIGURATION_CHANGED,

    /** The module SDK changed. */
    SDK_CHANGED,

    /** The active variant changed, which affects available resource sets and values. */
    VARIANT_CHANGED,

    /** A sync happened. This can change dynamically generated resources for example. */
    GRADLE_SYNC,

    /**
     * Project build. Not a direct resource edit, but for example when a custom view is compiled it
     * can affect how a resource like layouts should be rendered.
     */
    PROJECT_BUILD,

    /** Image changed. This might be needed to invalidate layoutlib drawable caches. */
    IMAGE_RESOURCE_CHANGED,
  }

  companion object {
    @JvmStatic fun getInstance(project: Project) = project.service<ResourceNotificationManager>()
  }
}

/**
 * A [ModuleEventObserver] registers listeners for various module-specific events (such as resource
 * folder manager changes) and then notifies [notice] when it sees an event.
 */
private class ModuleEventObserver(
  private val facet: AndroidFacet,
  private val incrementModificationCount: () -> Unit,
  private val notice: (Reason, VirtualFile?) -> Unit,
) : ModificationTracker, ResourceFolderListener, Disposable.Default {
  private var generation = appResourcesModificationCount
  private val listenersLock = Any()
  private var connection: MessageBusConnection? = null

  @GuardedBy("listenersLock")
  private val listeners: MutableList<ResourceChangeListener> = ArrayList(4)

  init {
    Disposer.register(facet, this)
  }

  override fun getModificationCount() = generation

  fun addListener(listener: ResourceChangeListener) {
    synchronized(listenersLock) {
      if (listeners.isEmpty()) registerListeners()
      listeners.add(listener)
    }
  }

  fun removeListener(listener: ResourceChangeListener) {
    synchronized(listenersLock) {
      listeners.remove(listener)
      if (listeners.isEmpty()) unregisterListeners()
    }
  }

  private fun registerListeners() {
    if (AndroidModel.isRequired(facet)) {
      require(connection == null)
      connection =
        requireNotNull(facet.module.project.messageBus.connect(facet)).apply {
          subscribe(ResourceFolderManager.TOPIC, this@ModuleEventObserver)
        }
    }
  }

  private fun unregisterListeners() {
    connection?.disconnect()
    connection = null
  }

  @RequiresEdt
  fun notifyListeners(reason: ImmutableSet<Reason>) {
    if (facet.isDisposed) return

    val generation = appResourcesModificationCount
    if (reason.singleOrNull() == Reason.RESOURCE_EDIT && generation == this.generation) {
      // Notified of an edit in some file that could potentially affect the resources, but
      // it didn't cause the modification stamp to increase: ignore. (If there are other reasons,
      // such as a variant change, then notify regardless.)
      return
    }

    this.generation = generation
    val listeners = synchronized(listenersLock) { ArrayList(this.listeners) }
    for (listener in listeners) listener.resourcesChanged(reason)
  }

  private val appResourcesModificationCount
    get() =
      StudioResourceRepositoryManager.getInstance(facet).cachedAppResources?.modificationCount ?: 0L

  fun hasListeners() = synchronized(listenersLock) { listeners.isNotEmpty() }

  // ---- Implements ResourceFolderManager.ResourceFolderListener ----
  override fun foldersChanged(facet: AndroidFacet, folders: List<VirtualFile>) {
    if (facet.module === this.facet.module) {
      incrementModificationCount()
      notice(Reason.GRADLE_SYNC, null)
    }
  }
}

private class ProjectBuildObserver(
  private val project: Project,
  parentDisposable: Disposable,
  private val incrementModificationCount: () -> Unit,
  private val notice: (Reason, VirtualFile?) -> Unit,
) : Disposable.Default {

  private val isListening = AtomicBoolean(false)
  private val hasStopped = AtomicBoolean(false)

  init {
    Disposer.register(parentDisposable, this)
  }

  fun ensureListening() {
    if (!isListening.getAndSet(true) && !hasStopped.get()) {
      project.getProjectSystem().getBuildManager().addBuildListener(this, BuildListener())
    }
  }

  fun stopListening() {
    hasStopped.set(true)
  }

  override fun dispose() {
    stopListening()
  }

  inner class BuildListener : ProjectSystemBuildManager.BuildListener {
    override fun buildCompleted(result: ProjectSystemBuildManager.BuildResult) {
      if (!hasStopped.get()) {
        incrementModificationCount()
        notice(Reason.PROJECT_BUILD, null)
      }
    }
  }
}

private class ProjectPsiTreeObserver(
  private val notice: (Reason, VirtualFile?) -> Unit,
  private val isRelevantFile: (VirtualFile?) -> Boolean,
) : PsiTreeChangeListener {
  private var ignoreChildrenChanged = false

  override fun beforeChildAddition(event: PsiTreeChangeEvent) {}

  override fun beforeChildRemoval(event: PsiTreeChangeEvent) {}

  override fun beforeChildReplacement(event: PsiTreeChangeEvent) {}

  override fun beforeChildMovement(event: PsiTreeChangeEvent) {}

  override fun beforeChildrenChange(event: PsiTreeChangeEvent) {
    ignoreChildrenChanged = false
  }

  override fun beforePropertyChange(event: PsiTreeChangeEvent) {}

  override fun childAdded(event: PsiTreeChangeEvent) {
    ignoreChildrenChanged = true

    if (isIgnorable(event)) return

    val file = getVirtualFile(event)
    if (!isRelevantFile(file)) {
      notice(Reason.RESOURCE_EDIT, file)
      return
    }

    val child = event.child
    val parent = event.parent

    if (child is XmlAttribute && parent is XmlTag) {
      // Typing in a new attribute. Don't need to do any rendering until there is an actual value.
      if (child.valueElement == null) return
    } else if (parent is XmlAttribute && child is XmlAttributeValue) {
      if (child.value.isEmpty()) return // Just added a new blank attribute; nothing to render yet.
    } else if (parent is XmlAttributeValue && child is XmlToken && event.oldChild == null) {
      // Just added attribute value
      val text = child.getText()
      // Check if this is an attribute that takes a resource.
      if (text.startsWith(SdkConstants.PREFIX_RESOURCE_REF) && !isBindingExpression(text)) {
        if (text == SdkConstants.PREFIX_RESOURCE_REF || text == SdkConstants.ANDROID_PREFIX) {
          // Using code completion to insert resource reference; not yet done.
          return
        }
        val url = ResourceUrl.parse(text)
        if (url != null && url.name.isEmpty()) {
          // Using code completion to insert resource reference; not yet done.
          return
        }
      }
    }
    notice(Reason.EDIT, file)
  }

  override fun childRemoved(event: PsiTreeChangeEvent) {
    ignoreChildrenChanged = true

    if (isIgnorable(event)) return

    val file = getVirtualFile(event)
    if (!isRelevantFile(file)) {
      notice(Reason.RESOURCE_EDIT, file)
      return
    }

    val child = event.child
    val parent = event.parent
    if (parent is XmlAttribute && child is XmlToken) {
      // Typing in attribute name. Don't need to do any rendering until there is an actual
      // value.
      val valueElement = parent.valueElement
      if (valueElement == null || valueElement.value.isEmpty()) {
        return
      }
    }

    notice(Reason.EDIT, file)
  }

  override fun childReplaced(event: PsiTreeChangeEvent) {
    ignoreChildrenChanged = true

    if (isIgnorable(event)) return

    val file = getVirtualFile(event)
    if (!isRelevantFile(file)) {
      notice(Reason.RESOURCE_EDIT, file)
      return
    }

    val child = event.child
    val parent = event.parent
    if (parent is XmlAttribute && child is XmlToken) {
      // Typing in attribute name. Don't need to do any rendering until there is an actual value.
      val valueElement = parent.valueElement
      if (valueElement == null || valueElement.value.isEmpty()) return
    } else if (parent is XmlAttributeValue && child is XmlToken && event.oldChild != null) {
      val newText = child.getText()
      val prevText = event.oldChild.text
      // See if user is working on an incomplete URL, and is still not complete, e.g. typing in
      // @string/foo manually.
      if (newText.startsWith(SdkConstants.PREFIX_RESOURCE_REF) && !isBindingExpression(newText)) {
        val prevUrl = ResourceUrl.parse(prevText)
        val newUrl = ResourceUrl.parse(newText)
        if (prevUrl?.name.isNullOrEmpty() && newUrl?.name.isNullOrEmpty()) return
      }
    }
    notice(Reason.EDIT, file)
  }

  override fun childMoved(event: PsiTreeChangeEvent) {
    check(event)
  }

  override fun childrenChanged(event: PsiTreeChangeEvent) {
    if (ignoreChildrenChanged) return

    check(event)
  }

  override fun propertyChanged(event: PsiTreeChangeEvent) {
    // When renaming a PsiFile, if the file extension stays the same (i.e the file type stays the
    // same) the generated PsiTreeChangeEvent will trigger "propertyChanged()" (this method) with
    // the changed file set as the event's element. On the other hand, if the file extension is
    // changed, a new object is created and the event will trigger "childReplaced()" instead, the
    // parent being the file's directory and the renamed file being the new child.
    if (PsiTreeChangeEvent.PROP_FILE_NAME != event.propertyName) return

    val file = (event.element as? PsiFile)?.virtualFile
    notice(Reason.RESOURCE_EDIT, file)
  }

  private fun getVirtualFile(event: PsiTreeChangeEvent) = event.file?.virtualFile

  private fun isIgnorable(event: PsiTreeChangeEvent): Boolean {
    // We can ignore edits in whitespace, XML error nodes, and modification in comments.
    // (Note that editing text in an attribute value, including whitespace characters,
    // is not a PsiWhiteSpace element; it's an XmlToken of token type XML_ATTRIBUTE_VALUE_TOKEN
    // Moreover, We only ignore the modification of commented texts (in such case the type of
    // parent is XmlComment), because the user may *mark* some components/attributes as comments
    // for debugging purpose. In that case the child is instance of XmlComment but parent isn't,
    // so we will NOT ignore the event.
    val child = event.child
    val parent = event.parent
    if (child is PsiErrorElement || parent is XmlComment) return true

    if (
      (child is PsiWhiteSpace || child is XmlText || parent is XmlText) &&
        getFolderType(event.file) != ResourceFolderType.VALUES
    ) {
      // Editing text or whitespace has no effect outside of values files.
      return true
    }

    val file = event.file
    // Spurious events from the IDE doing internal things, such as the formatter using a light
    // virtual filesystem to process text formatting chunks etc.
    return file != null && (file.parent == null || !file.viewProvider.isPhysical)
  }

  private fun check(event: PsiTreeChangeEvent) {
    if (isIgnorable(event)) return

    val file = getVirtualFile(event)
    if (isRelevantFile(file)) {
      notice(Reason.EDIT, file)
    } else {
      notice(Reason.RESOURCE_EDIT, file)
    }
  }
}

private class FileEventObserver(
  private val module: Module,
  private val notice: (Reason, VirtualFile?) -> Unit,
) : BulkFileListener {
  private val listeners: MutableList<ResourceChangeListener> = ArrayList(2)
  private var messageBusConnection: MessageBusConnection? = null

  fun addListener(listener: ResourceChangeListener) {
    if (listeners.isEmpty()) registerListeners()
    listeners.add(listener)
  }

  fun removeListener(listener: ResourceChangeListener) {
    listeners.remove(listener)
    if (listeners.isEmpty()) unregisterListeners()
  }

  private fun registerListeners() {
    messageBusConnection =
      requireNotNull(application.messageBus.connect(module)).apply {
        subscribe(VirtualFileManager.VFS_CHANGES, this@FileEventObserver)
      }
  }

  private fun unregisterListeners() {
    requireNotNull(messageBusConnection).disconnect()
  }

  override fun after(events: List<VFileEvent>) {
    events
      .firstOrNull { event ->
        val parent = event.file?.parent ?: return@firstOrNull false

        val resType = ResourceFolderType.getFolderType(parent.name)
        ResourceFolderType.DRAWABLE == resType || ResourceFolderType.MIPMAP == resType
      }
      ?.let { notice(Reason.IMAGE_RESOURCE_CHANGED, it.file) }
  }

  fun hasListeners() = listeners.isNotEmpty()
}

private class ConfigurationEventObserver(
  private val configuration: Configuration,
  private val notice: (Reason, VirtualFile?) -> Unit,
) : ConfigurationListener {
  private val listeners: MutableList<ResourceChangeListener> = ArrayList(2)

  fun addListener(listener: ResourceChangeListener) {
    if (listeners.isEmpty()) registerListeners()
    listeners.add(listener)
  }

  fun removeListener(listener: ResourceChangeListener) {
    listeners.remove(listener)
    if (listeners.isEmpty()) unregisterListeners()
  }

  fun hasListeners() = listeners.isNotEmpty()

  private fun registerListeners() {
    configuration.addListener(this)
  }

  private fun unregisterListeners() {
    configuration.removeListener(this)
  }

  // ---- Implements ConfigurationListener ----
  override fun changed(flags: Int): Boolean {
    if ((flags and ConfigurationListener.MASK_RENDERING) != 0)
      notice(Reason.CONFIGURATION_CHANGED, null)

    return true
  }
}
