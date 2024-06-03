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
import com.android.tools.res.CacheableResourceRepository
import com.android.utils.HashCodes
import com.android.utils.isBindingExpression
import com.google.common.collect.ImmutableSet
import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ApplicationManager
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
import com.intellij.util.concurrency.SameThreadExecutor
import com.intellij.util.messages.MessageBusConnection
import java.util.EnumSet
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import org.jetbrains.android.facet.AndroidFacet
import org.jetbrains.android.facet.ResourceFolderManager
import org.jetbrains.android.facet.ResourceFolderManager.Companion.getInstance
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
 * * No locks are held when the listener are notified
 * * All events are delivered on the event dispatch (UI) thread
 * * Add listener or remove listener can be done from any thread
 */
class ResourceNotificationManager
/**
 * Do not instantiate directly; this is a Service and its lifecycle is managed by the IDE; use
 * [.getInstance] instead.
 */
(private val myProject: Project) {
  private val myObserverLock = Any()
  /**
   * Module observers: one per observed module in the project, with potentially multiple listeners.
   */
  @GuardedBy("myObserverLock")
  private val myModuleToObserverMap: MutableMap<Module, ModuleEventObserver> = HashMap()

  /** File observers: one per observed file, with potentially multiple listeners. */
  @GuardedBy("myObserverLock")
  private val myFileToObserverMap: MutableMap<VirtualFile, FileEventObserver> = HashMap()

  /**
   * Configuration observers: one per observed configuration, with potentially multiple listeners.
   */
  @GuardedBy("myObserverLock")
  private val myConfigurationToObserverMap: MutableMap<Configuration, ConfigurationEventObserver> =
    HashMap()

  /** Project wide observer: a single one is sufficient. */
  @GuardedBy("myObserverLock") private var myProjectPsiTreeObserver: ProjectPsiTreeObserver? = null

  private val myProjectBuildObserver = ProjectBuildObserver()

  /** Whether we've already been notified about a change and we'll be firing it shortly. */
  private val myPendingNotify = AtomicBoolean()

  private var myIgnoreChildrenChanged = false

  /**
   * Counter for events other than resource repository, configuration or file events. For example,
   * this counts project builds.
   */
  private var myModificationCount: Long = 0

  /** Set of events we've observed since the last notification. */
  private var myEvents: EnumSet<Reason> = EnumSet.noneOf(Reason::class.java)

  fun getCurrentVersion(
    facet: AndroidFacet,
    file: PsiFile?,
    configuration: Configuration?,
  ): ResourceVersion {
    val repository: CacheableResourceRepository =
      StudioResourceRepositoryManager.getAppResources(facet)
    if (file != null) {
      val fileStamp = file.modificationStamp
      return if (configuration != null) {
        ResourceVersion(
          repository.modificationCount,
          fileStamp,
          configuration.modificationCount,
          configuration.settings.stateVersion.toLong(),
          myModificationCount,
        )
      } else {
        ResourceVersion(repository.modificationCount, fileStamp, 0L, 0L, myModificationCount)
      }
    } else {
      return ResourceVersion(repository.modificationCount, 0L, 0L, 0L, myModificationCount)
    }
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
    val module = facet.module
    synchronized(myObserverLock) {
      var moduleEventObserver = myModuleToObserverMap[module]
      if (moduleEventObserver == null) {
        if (myModuleToObserverMap.isEmpty()) {
          if (myProjectPsiTreeObserver == null) {
            myProjectPsiTreeObserver = ProjectPsiTreeObserver()
          }
          myProjectBuildObserver.startListening()
        }
        moduleEventObserver = ModuleEventObserver(facet)
        myModuleToObserverMap[module] = moduleEventObserver
      }
      moduleEventObserver.addListener(listener)
      if (file != null) {
        var fileEventObserver = myFileToObserverMap[file]
        if (fileEventObserver == null) {
          fileEventObserver = FileEventObserver(module)
          myFileToObserverMap[file] = fileEventObserver
        }
        fileEventObserver.addListener(listener)

        if (configuration != null) {
          var configurationEventObserver = myConfigurationToObserverMap[configuration]
          if (configurationEventObserver == null) {
            configurationEventObserver = ConfigurationEventObserver(configuration)
            myConfigurationToObserverMap[configuration] = configurationEventObserver
          }
          configurationEventObserver.addListener(listener)
        }
      } else {
        assert(configuration == null) { configuration!! }
      }
    }

    return getCurrentVersion(
      facet,
      if (file != null) AndroidPsiUtils.getPsiFileSafely(myProject, file) else null,
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
    synchronized(myObserverLock) {
      if (file != null) {
        if (configuration != null) {
          val configurationEventObserver = myConfigurationToObserverMap[configuration]
          if (configurationEventObserver != null) {
            configurationEventObserver.removeListener(listener)
            if (!configurationEventObserver.hasListeners()) {
              myConfigurationToObserverMap.remove(configuration)
            }
          }
        }
        val fileEventObserver = myFileToObserverMap[file]
        if (fileEventObserver != null) {
          fileEventObserver.removeListener(listener)
          if (!fileEventObserver.hasListeners()) {
            myFileToObserverMap.remove(file)
          }
        }
      } else {
        assert(configuration == null) { configuration!! }
      }
      val module = facet.module
      val moduleEventObserver = myModuleToObserverMap[module]
      if (moduleEventObserver != null) {
        moduleEventObserver.removeListener(listener)
        if (!moduleEventObserver.hasListeners()) {
          Disposer.dispose(moduleEventObserver)
          if (myModuleToObserverMap.isEmpty() && myProjectPsiTreeObserver != null) {
            myProjectBuildObserver.stopListening()
            myProjectPsiTreeObserver = null
          }
        }
      }
    }
  }

  val psiListener: PsiTreeChangeListener?
    /**
     * Returns an implementation of [PsiTreeChangeListener] that is not registered and is used as a
     * delegate (e.g in [AndroidPsiTreeChangeListener]).
     *
     * If no listener has been added to the [ResourceNotificationManager], this method returns null.
     */
    get() {
      synchronized(myObserverLock) {
        return myProjectPsiTreeObserver
      }
    }

  /**
   * Something happened. Either schedule a notification or if one is already pending, do nothing.
   */
  private fun notice(reason: Reason, source: VirtualFile?) {
    myEvents.add(reason)
    if (!myPendingNotify.compareAndSet(false, true)) {
      return
    }

    val application = ApplicationManager.getApplication()
    application.invokeLater {
      if (!myPendingNotify.compareAndSet(true, false)) {
        return@invokeLater
      }
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
    // dispatchToRepositories
    // call returns and all callbacks passed to runAfterPendingUpdatesFinish are called. To avoid
    // calling scheduleFinalNotification prematurely, the initial value of count is set to 1.
    // This guarantees that it stays positive until the dispatchToRepositories method returns.
    val count = AtomicInteger(1)
    val resourceFolderRegistry = ResourceFolderRegistry.getInstance(myProject)
    resourceFolderRegistry.dispatchToRepositories(source) {
      repository: ResourceFolderRepository,
      file: VirtualFile? ->
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
    ApplicationManager.getApplication().invokeLater {
      val reason = ImmutableSet.copyOf(myEvents)
      myEvents = EnumSet.noneOf(Reason::class.java)
      notifyListeners(reason)
      myEvents.clear()
    }
  }

  private fun notifyListeners(reason: ImmutableSet<Reason>) {
    ApplicationManager.getApplication().assertIsDispatchThread()

    var observers: List<ModuleEventObserver>
    synchronized(myObserverLock) { observers = ArrayList(myModuleToObserverMap.values) }
    for (moduleEventObserver in observers) {
      // Not every module may have pending changes; each one will check.
      moduleEventObserver.notifyListeners(reason)
    }
  }

  /**
   * A [ModuleEventObserver] registers listeners for various module-specific events (such as
   * resource folder manager changes) and then notifies [.notice] when it sees an event.
   */
  private inner class ModuleEventObserver(private val myFacet: AndroidFacet) :
    ModificationTracker, ResourceFolderListener, Disposable {
    private var myGeneration: Long
    private val myListenersLock = Any()
    private var myConnection: MessageBusConnection? = null

    @GuardedBy("myListenersLock")
    private val myListeners: MutableList<ResourceChangeListener> = ArrayList(4)

    init {
      myGeneration = appResourcesModificationCount
      Disposer.register(facet, this)
    }

    override fun getModificationCount(): Long {
      return myGeneration
    }

    fun addListener(listener: ResourceChangeListener) {
      synchronized(myListenersLock) {
        if (myListeners.isEmpty()) {
          registerListeners()
        }
        myListeners.add(listener)
      }
    }

    fun removeListener(listener: ResourceChangeListener) {
      synchronized(myListenersLock) {
        myListeners.remove(listener)
        if (myListeners.isEmpty()) {
          unregisterListeners()
        }
      }
    }

    private fun registerListeners() {
      if (AndroidModel.isRequired(myFacet)) {
        // Ensure that project resources have been initialized first, since
        // we want all repos to add their own variant listeners before ours (such that
        // when the variant changes, the project resources get notified and updated
        // before our own update listener attempts to re-render).
        StudioResourceRepositoryManager.getProjectResources(myFacet)

        assert(myConnection == null)
        myConnection = myFacet.module.project.messageBus.connect(myFacet)
        myConnection!!.subscribe(ResourceFolderManager.TOPIC, this)
        getInstance(myFacet) // Make sure ResourceFolderManager is initialized.
      }
    }

    private fun unregisterListeners() {
      if (myConnection != null) {
        myConnection!!.disconnect()
      }
    }

    fun notifyListeners(reason: ImmutableSet<Reason>) {
      if (myFacet.isDisposed) {
        return
      }
      val generation = appResourcesModificationCount
      if (reason.size == 1 && reason.contains(Reason.RESOURCE_EDIT) && generation == myGeneration) {
        // Notified of an edit in some file that could potentially affect the resources, but
        // it didn't cause the modification stamp to increase: ignore. (If there are other reasons,
        // such as a variant change, then notify regardless.)
        return
      }

      myGeneration = generation
      ApplicationManager.getApplication().assertIsDispatchThread()
      var listeners: List<ResourceChangeListener>
      synchronized(myListenersLock) { listeners = ArrayList(myListeners) }
      for (listener in listeners) {
        listener.resourcesChanged(reason)
      }
    }

    private val appResourcesModificationCount: Long
      get() {
        val appResources: CacheableResourceRepository? =
          StudioResourceRepositoryManager.getInstance(myFacet).cachedAppResources
        return appResources?.modificationCount ?: 0
      }

    fun hasListeners(): Boolean {
      synchronized(myListenersLock) {
        return !myListeners.isEmpty()
      }
    }

    // ---- Implements ResourceFolderManager.ResourceFolderListener ----
    override fun foldersChanged(facet: AndroidFacet, folders: List<VirtualFile>) {
      if (facet.module === myFacet.module) {
        myModificationCount++
        notice(Reason.GRADLE_SYNC, null)
      }
    }

    override fun dispose() {
      synchronized(myObserverLock) { myModuleToObserverMap.remove(myFacet.module) }
    }
  }

  private inner class ProjectBuildObserver : ProjectSystemBuildManager.BuildListener {
    private var myAlreadyAddedBuildListener = false
    private var myIgnoreBuildEvents = false

    fun startListening() {
      if (!myAlreadyAddedBuildListener) { // See comment in stopListening.
        myAlreadyAddedBuildListener = true
        myProject.getProjectSystem().getBuildManager().addBuildListener(myProject, this)
      }
      myIgnoreBuildEvents = false
    }

    fun stopListening() {
      // Unfortunately, we can't remove build tasks once they've been added.
      //    https://youtrack.jetbrains.com/issue/IDEA-139893
      // Therefore, we leave the listeners around, and make sure we only add
      // them once -- and we also ignore any build events that appear when we're
      // not intending to be actively listening
      // ProjectBuilder.getInstance(myProject).removeAfterProjectBuildTask(this);
      myIgnoreBuildEvents = true
    }

    override fun buildStarted(mode: ProjectSystemBuildManager.BuildMode) {}

    override fun beforeBuildCompleted(result: ProjectSystemBuildManager.BuildResult) {}

    override fun buildCompleted(result: ProjectSystemBuildManager.BuildResult) {
      if (!myIgnoreBuildEvents) {
        myModificationCount++
        notice(Reason.PROJECT_BUILD, null)
      }
    }
  }

  private inner class ProjectPsiTreeObserver : PsiTreeChangeListener {
    override fun beforeChildAddition(event: PsiTreeChangeEvent) {}

    override fun beforeChildRemoval(event: PsiTreeChangeEvent) {}

    override fun beforeChildReplacement(event: PsiTreeChangeEvent) {}

    override fun beforeChildMovement(event: PsiTreeChangeEvent) {}

    override fun beforeChildrenChange(event: PsiTreeChangeEvent) {
      myIgnoreChildrenChanged = false
    }

    override fun beforePropertyChange(event: PsiTreeChangeEvent) {}

    override fun childAdded(event: PsiTreeChangeEvent) {
      myIgnoreChildrenChanged = true

      if (isIgnorable(event)) {
        return
      }

      val file = getVirtualFile(event)
      if (file != null && isRelevantFile(file)) {
        val child = event.child
        val parent = event.parent

        if (child is XmlAttribute && parent is XmlTag) {
          // Typing in a new attribute. Don't need to do any rendering until there is an actual
          // value.
          if (child.valueElement == null) {
            return
          }
        } else if (parent is XmlAttribute && child is XmlAttributeValue) {
          if (child.value.isEmpty()) {
            // Just added a new blank attribute; nothing to render yet.
            return
          }
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
      } else {
        notice(Reason.RESOURCE_EDIT, file)
      }
    }

    override fun childRemoved(event: PsiTreeChangeEvent) {
      myIgnoreChildrenChanged = true

      if (isIgnorable(event)) {
        return
      }

      val file = getVirtualFile(event)
      if (file != null && isRelevantFile(file)) {
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
      } else {
        notice(Reason.RESOURCE_EDIT, file)
      }
    }

    override fun childReplaced(event: PsiTreeChangeEvent) {
      myIgnoreChildrenChanged = true

      if (isIgnorable(event)) {
        return
      }

      val file = getVirtualFile(event)
      if (file != null && isRelevantFile(file)) {
        val child = event.child
        val parent = event.parent
        if (parent is XmlAttribute && child is XmlToken) {
          // Typing in attribute name. Don't need to do any rendering until there is an actual
          // value.
          val valueElement = parent.valueElement
          if (valueElement == null || valueElement.value.isEmpty()) {
            return
          }
        } else if (parent is XmlAttributeValue && child is XmlToken && event.oldChild != null) {
          val newText = child.getText()
          val prevText = event.oldChild.text
          // See if user is working on an incomplete URL, and is still not complete, e.g. typing in
          // @string/foo manually.
          if (
            newText.startsWith(SdkConstants.PREFIX_RESOURCE_REF) && !isBindingExpression(newText)
          ) {
            var prevUrl = ResourceUrl.parse(prevText)
            var newUrl = ResourceUrl.parse(newText)
            if (prevUrl != null && prevUrl.name.isEmpty()) {
              prevUrl = null
            }
            if (newUrl != null && newUrl.name.isEmpty()) {
              newUrl = null
            }
            if (prevUrl == null && newUrl == null) {
              return
            }
          }
        }
        notice(Reason.EDIT, file)
      } else {
        notice(Reason.RESOURCE_EDIT, file)
      }
    }

    override fun childMoved(event: PsiTreeChangeEvent) {
      check(event)
    }

    override fun childrenChanged(event: PsiTreeChangeEvent) {
      if (myIgnoreChildrenChanged) {
        return
      }

      check(event)
    }

    override fun propertyChanged(event: PsiTreeChangeEvent) {
      // When renaming a PsiFile, if the file extension stays the same (i.e the file type
      // stays the same) the generated PsiTreeChangeEvent will trigger "propertyChanged()" (this
      // method) with
      // the changed file set as the event's element.
      // On the other hand, if the file extension is changed, a new object is created and the event
      // will
      // trigger "childReplaced()" instead, the parent being the file's directory and the renamed
      // file being the new child.
      if (PsiTreeChangeEvent.PROP_FILE_NAME == event.propertyName) {
        val child = event.element
        val file = if (child is PsiFile) child.virtualFile else null
        notice(Reason.RESOURCE_EDIT, file)
      }
    }

    private fun getVirtualFile(event: PsiTreeChangeEvent): VirtualFile? {
      val psiFile = event.file
      return psiFile?.virtualFile
    }

    /** Checks if the file is present in [.myFileToObserverMap]. */
    private fun isRelevantFile(virtualFile: VirtualFile): Boolean {
      synchronized(myObserverLock) {
        return myFileToObserverMap.containsKey(virtualFile)
      }
    }

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
      if (child is PsiErrorElement || parent is XmlComment) {
        return true
      }

      if (
        (child is PsiWhiteSpace || child is XmlText || parent is XmlText) &&
          getFolderType(event.file) != ResourceFolderType.VALUES
      ) {
        // Editing text or whitespace has no effect outside of values files.
        return true
      }

      val file = event.file
      // Spurious events from the IDE doing internal things, such as the formatter using a light
      // virtual
      // filesystem to process text formatting chunks etc.
      return file != null && (file.parent == null || !file.viewProvider.isPhysical)
    }

    private fun check(event: PsiTreeChangeEvent) {
      if (isIgnorable(event)) {
        return
      }

      val file = getVirtualFile(event)
      if (file != null && isRelevantFile(file)) {
        notice(Reason.EDIT, file)
      } else {
        notice(Reason.RESOURCE_EDIT, file)
      }
    }
  }

  private inner class FileEventObserver(private val myModule: Module) : BulkFileListener {
    private val myListeners: MutableList<ResourceChangeListener> = ArrayList(2)
    private var myMessageBusConnection: MessageBusConnection? = null

    fun addListener(listener: ResourceChangeListener) {
      if (myListeners.isEmpty()) {
        registerListeners()
      }
      myListeners.add(listener)
    }

    fun removeListener(listener: ResourceChangeListener) {
      myListeners.remove(listener)

      if (myListeners.isEmpty()) {
        unregisterListeners()
      }
    }

    private fun registerListeners() {
      myMessageBusConnection = ApplicationManager.getApplication().messageBus.connect(myModule)
      myMessageBusConnection!!.subscribe(VirtualFileManager.VFS_CHANGES, this)
    }

    private fun unregisterListeners() {
      myMessageBusConnection!!.disconnect()
    }

    override fun after(events: List<VFileEvent?>) {
      events
        .stream()
        .filter { event: VFileEvent? ->
          val file = event!!.file ?: return@filter false
          val parent = file.parent ?: return@filter false

          val resType = ResourceFolderType.getFolderType(parent.name)
          ResourceFolderType.DRAWABLE == resType || ResourceFolderType.MIPMAP == resType
        }
        .findAny()
        .ifPresent { event: VFileEvent? -> notice(Reason.IMAGE_RESOURCE_CHANGED, event!!.file) }
    }

    fun hasListeners(): Boolean {
      return !myListeners.isEmpty()
    }
  }

  private inner class ConfigurationEventObserver(private val myConfiguration: Configuration) :
    ConfigurationListener {
    private val myListeners: MutableList<ResourceChangeListener> = ArrayList(2)

    fun addListener(listener: ResourceChangeListener) {
      if (myListeners.isEmpty()) {
        registerListeners()
      }
      myListeners.add(listener)
    }

    fun removeListener(listener: ResourceChangeListener) {
      myListeners.remove(listener)

      if (myListeners.isEmpty()) {
        unregisterListeners()
      }
    }

    fun hasListeners(): Boolean {
      return !myListeners.isEmpty()
    }

    private fun registerListeners() {
      myConfiguration.addListener(this)
    }

    private fun unregisterListeners() {
      myConfiguration.removeListener(this)
    }

    // ---- Implements ConfigurationListener ----
    override fun changed(flags: Int): Boolean {
      if ((flags and ConfigurationListener.MASK_RENDERING) != 0) {
        notice(Reason.CONFIGURATION_CHANGED, null)
      }
      return true
    }
  }

  /**
   * Interface that should be implemented by clients interested in resource edits and events that
   * affect resources.
   */
  interface ResourceChangeListener {
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
  class ResourceVersion(
    private val myResourceGeneration: Long,
    private val myFileGeneration: Long,
    private val myConfigurationGeneration: Long,
    private val myProjectConfigurationGeneration: Long,
    private val myOtherGeneration: Long,
  ) {
    override fun equals(o: Any?): Boolean {
      if (this === o) return true
      if (o == null || javaClass != o.javaClass) return false

      val version = o as ResourceVersion

      return myResourceGeneration == version.myResourceGeneration &&
        myFileGeneration == version.myFileGeneration &&
        myConfigurationGeneration == version.myConfigurationGeneration &&
        myProjectConfigurationGeneration == version.myProjectConfigurationGeneration &&
        myOtherGeneration == version.myOtherGeneration
    }

    override fun hashCode(): Int {
      return HashCodes.mix(
        java.lang.Long.hashCode(myResourceGeneration),
        java.lang.Long.hashCode(myFileGeneration),
        java.lang.Long.hashCode(myConfigurationGeneration),
        java.lang.Long.hashCode(myProjectConfigurationGeneration),
        java.lang.Long.hashCode(myOtherGeneration),
      )
    }

    override fun toString(): String {
      return "ResourceVersion{" +
        "resource=" +
        myResourceGeneration +
        ", file=" +
        myFileGeneration +
        ", configuration=" +
        myConfigurationGeneration +
        ", projectConfiguration=" +
        myProjectConfigurationGeneration +
        ", other=" +
        myOtherGeneration +
        '}'
    }
  }

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
    /**
     * Returns the [ResourceNotificationManager] for the given project.
     *
     * @param project the project to return the notification manager for
     * @return a notification manager
     */
    fun getInstance(project: Project): ResourceNotificationManager {
      return project.getService(ResourceNotificationManager::class.java)
    }
  }
}
