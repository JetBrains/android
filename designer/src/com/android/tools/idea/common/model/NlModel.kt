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
package com.android.tools.idea.common.model

import com.android.annotations.concurrency.Slow
import com.android.ide.common.rendering.api.ResourceNamespace
import com.android.ide.common.rendering.api.ResourceReference
import com.android.ide.common.rendering.api.ViewInfo
import com.android.ide.common.resources.ResourceRepository
import com.android.ide.common.resources.ResourceResolver
import com.android.resources.ResourceType
import com.android.resources.ResourceUrl
import com.android.tools.configurations.Configuration
import com.android.tools.idea.common.api.DragType
import com.android.tools.idea.common.api.InsertType
import com.android.tools.idea.common.command.NlWriteCommandActionUtil
import com.android.tools.idea.common.lint.LintAnnotationsModel
import com.android.tools.idea.common.model.NlDependencyManager.Companion.getInstance
import com.android.tools.idea.common.surface.organization.OrganizationGroup
import com.android.tools.idea.common.type.DesignerEditorFileType
import com.android.tools.idea.common.type.typeOf
import com.android.tools.idea.common.util.XmlTagUtil
import com.android.tools.idea.res.ResourceNotificationManager
import com.android.tools.idea.res.StudioResourceRepositoryManager
import com.android.tools.idea.util.ListenerCollection.Companion.createWithDirectExecutor
import com.google.common.annotations.VisibleForTesting
import com.google.common.collect.ImmutableList
import com.google.common.collect.Sets
import com.intellij.openapi.Disposable
import com.intellij.openapi.actionSystem.DataContext
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.ReadAction
import com.intellij.openapi.application.WriteAction
import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.module.Module
import com.intellij.openapi.project.DumbService
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.util.ModificationTracker
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.xml.XmlFile
import com.intellij.psi.xml.XmlTag
import com.intellij.util.Alarm
import com.intellij.util.concurrency.AppExecutorUtil
import com.intellij.util.ui.update.MergingUpdateQueue
import com.intellij.util.ui.update.Update
import java.util.Collections
import java.util.WeakHashMap
import java.util.concurrent.Callable
import java.util.concurrent.atomic.AtomicLong
import java.util.concurrent.atomic.AtomicReference
import java.util.function.BiFunction
import java.util.function.Consumer
import org.jetbrains.android.facet.AndroidFacet
import org.jetbrains.annotations.TestOnly

/**
 * Model for an XML file
 *
 * @param myComponentRegistrar Returns the responsible for registering an [NlComponent] to enhance
 *   it with layout-specific properties and methods.
 * @param myXmlFileProvider [LayoutlibSceneManager] requires the file from model to be an [XmlFile]
 *   to be able to render it. This is true in case of layout file and some others as well. However,
 *   we want to use model to render other file types (e.g. Java and Kotlin source files that contain
 *   custom Android [View]s)that do not have explicit conversion to [XmlFile] (but might have
 *   implicit). This provider should provide us with [XmlFile] representation of the VirtualFile fed
 *   to the model.
 * @param dataContext Returns the [DataContext] associated to this model. The [DataContext] allows
 *   storing information that is specific to this model but is not part of it. For example, context
 *   information about how the model should be represented in a specific surface. The [DataContext]
 *   might change at any point so make sure you always call this method to obtain the latest data.
 */
open class NlModel
@VisibleForTesting
protected constructor(
  parent: Disposable,
  val facet: AndroidFacet,
  val virtualFile: VirtualFile,
  open val configuration: Configuration,
  private val myComponentRegistrar: Consumer<NlComponent>,
  private val myXmlFileProvider: BiFunction<Project, VirtualFile, XmlFile>,
  modelUpdater: NlModelUpdaterInterface?,
  override var dataContext: DataContext,
) : ModificationTracker, DataContextHolder {
  val pendingIds: MutableSet<String> = Sets.newHashSet()

  private val myListeners = createWithDirectExecutor<ModelListener>()

  /** Model name. This can be used when multiple models are displayed at the same time */
  var modelDisplayName: String? = null

  /** Text to display when displaying a tooltip related to this model */
  var modelTooltip: String? = null
    private set

  // Deliberately not rev'ing the model version and firing changes here;
  // we know only the warnings layer cares about this change and can be
  // updated by a single repaint
  var lintAnnotationsModel: LintAnnotationsModel? = null
  val id: Long = System.nanoTime() xor file.name.hashCode().toLong()
  val type: DesignerEditorFileType = file.typeOf()

  private val activations: MutableSet<Any> = Collections.newSetFromMap(WeakHashMap())
  private val modelVersion = ModelVersion()
  private var myConfigurationModificationCount: Long = configuration.modificationCount

  @get:VisibleForTesting
  val updateQueue =
    MergingUpdateQueue(
        "android.layout.preview.edit",
        DELAY_AFTER_TYPING_MS,
        true,
        null,
        this,
        null,
        Alarm.ThreadToUse.SWING_THREAD,
      )
      .also { queue ->
        queue.setRestartTimerOnAdd(true)
        // Suspend events until there is an activation.
        queue.suspend()
      }

  /** Variable to track what triggered the latest render (if known). */
  var lastChangeType: ChangeType? = null
    private set

  /** Executor used for asynchronous updates. */
  private val myUpdateExecutor = AppExecutorUtil.createBoundedApplicationPoolExecutor("NlModel", 1)

  private val myThemeUpdateComputation = AtomicReference<Disposable?>()
  var isDisposed: Boolean = false
    private set

  /**
   * Adds information to the model from a render result. A given model can use different updaters
   * depending on what its usage requires. E.g. interactive preview may need less information from
   * an [NlModel] than a standard preview, so different updaters can be used in those cases.
   */
  private var myModelUpdater: NlModelUpdaterInterface = modelUpdater ?: DefaultModelUpdater()

  /**
   * Returns the latest calculated [ResourceResolver]. This is just to be used from those context
   * where obtaining the resource resolver can not be done like the UI thread. The cached resource
   * resolver is updated after every model update, including theme changes.
   */
  @get:Deprecated("Call Configuration.getResourceResolver from a background context")
  var cachedResourceResolver: ResourceResolver
    private set

  /**
   * Indicate which group this NlModel belongs. This can be used to categorize the NlModel when
   * rendering or layouting.
   */
  var organizationGroup: OrganizationGroup? = null

  val treeReader = NlTreeReader { file }

  init {
    Disposer.register(parent, this)
    cachedResourceResolver = configuration.resourceResolver
  }

  /** Returns if this model is currently active. */
  private val isActive: Boolean
    get() {
      synchronized(activations) {
        return activations.isNotEmpty()
      }
    }

  /**
   * Notify model that it's active. A model is active by default.
   *
   * @param source caller used to keep track of the references to this model. See [.deactivate]
   * @return true if the model was not active before and was activated.
   */
  fun activate(source: Any): Boolean {
    if (facet.isDisposed) {
      return false
    }

    // TODO: Tracking the source is just a workaround for the model being shared so the activations
    // and deactivations are
    // handled correctly. This should be solved by moving the removing this responsibility from the
    // model. The model shouldn't
    // need to keep track of activations/deactivation and they should be handled by the caller.
    var wasActive: Boolean
    synchronized(activations) {
      wasActive = activations.isNotEmpty()
      activations.add(source)
    }
    if (!wasActive) {
      // This was the first activation so enable listeners

      // If the resources have changed or the configuration has been modified, request a model
      // update

      if (configuration.modificationCount != myConfigurationModificationCount) {
        updateTheme()
      }
      myListeners.forEach { listener: ModelListener -> listener.modelActivated(this) }
      updateQueue.resume()
      return true
    } else {
      return false
    }
  }

  fun updateTheme() {
    val computationToken = Disposer.newDisposable()
    Disposer.register(this, computationToken)
    val oldComputation = myThemeUpdateComputation.getAndSet(computationToken)
    if (oldComputation != null) {
      Disposer.dispose(oldComputation)
    }
    ReadAction.nonBlocking(
        Callable<Void?> {
          if (myThemeUpdateComputation.get() !== computationToken) {
            return@Callable null // A new update has already been scheduled.
          }
          val themeUrl = ResourceUrl.parse(configuration.theme)
          if (themeUrl != null && themeUrl.type == ResourceType.STYLE) {
            updateTheme(themeUrl, computationToken)
          }
          null
        }
      )
      .expireWith(computationToken)
      .submit(myUpdateExecutor)
  }

  @Slow
  private fun updateTheme(themeUrl: ResourceUrl, computationToken: Disposable) {
    if (myThemeUpdateComputation.get() !== computationToken) {
      return // A new update has already been scheduled.
    }
    try {
      val resolver = configuration.resourceResolver
      val themeReference =
        ResourceReference.style(
          if (themeUrl.isFramework) ResourceNamespace.ANDROID else ResourceNamespace.RES_AUTO,
          themeUrl.name,
        )
      if (resolver.getStyle(themeReference) == null) {
        val theme = configuration.preferredTheme
        if (myThemeUpdateComputation.get() !== computationToken) {
          return // A new update has already been scheduled.
        }
        configuration.setTheme(theme)
        cachedResourceResolver = configuration.resourceResolver
      }
    } finally {
      if (myThemeUpdateComputation.compareAndSet(computationToken, null)) {
        Disposer.dispose(computationToken)
      }
    }
  }

  private fun deactivate() {
    myConfigurationModificationCount = configuration.modificationCount
    updateQueue.suspend()
  }

  /**
   * Notify model that it's not active. This means it can stop watching for events etc. It may be
   * activated again in the future.
   *
   * @param source the source is used to keep track of the references that are using this model.
   *   Only when all the sources have called deactivate(Object), the model will be really
   *   deactivated.
   * @return true if the model was active before and was deactivated.
   */
  fun deactivate(source: Any): Boolean {
    var shouldDeactivate: Boolean
    synchronized(activations) {
      val removed = activations.remove(source)
      // If there are no more activations, call the private #deactivate()
      shouldDeactivate = removed && activations.isEmpty()
    }
    if (shouldDeactivate) {
      deactivate()
      return true
    } else {
      return false
    }
  }

  val file: XmlFile
    get() = myXmlFileProvider.apply(project, virtualFile)

  fun syncWithPsi(newRoot: XmlTag, roots: List<TagSnapshotTreeNode>) {
    myModelUpdater.updateFromTagSnapshot(this, newRoot, roots)
  }

  fun updateAccessibility(viewInfos: List<ViewInfo>) {
    myModelUpdater.updateFromViewInfo(this, viewInfos)
  }

  /**
   * Adds a new [ModelListener]. If the listener already exists, this method will make sure that the
   * listener is only added once.
   */
  fun addListener(listener: ModelListener) {
    myListeners.add(listener)
  }

  fun removeListener(listener: ModelListener) {
    myListeners.remove(listener)
  }

  /**
   * Calls all the listeners [ModelListener.modelDerivedDataChanged] method.
   *
   * TODO: move this mechanism to [LayoutlibSceneManager], or, ideally, remove the need for it
   *   entirely by moving all the derived data into the Scene.
   */
  fun notifyListenersModelDerivedDataChanged() {
    myListeners.forEach { listener: ModelListener -> listener.modelDerivedDataChanged(this) }
  }

  /**
   * Calls all the listeners [ModelListener.modelChangedOnLayout] method.
   *
   * @param animate if true, warns the listeners to animate the layout update
   *
   * TODO: move these listeners out of [NlModel], since the model shouldn't care about being laid
   *   out.
   */
  fun notifyListenersModelChangedOnLayout(animate: Boolean) {
    myListeners.forEach { listener: ModelListener -> listener.modelChangedOnLayout(this, animate) }
  }

  val module: Module
    get() = facet.module

  val project: Project
    get() = module.project

  /**
   * This will warn model listeners that the model has been changed "live", without the attributes
   * of components being actually committed. Listeners such as Scene Managers will likely want for
   * example to schedule a layout pass in reaction to that callback.
   *
   * @param animate should the changes be animated or not.
   */
  fun notifyLiveUpdate(animate: Boolean) {
    myListeners.forEach { listener -> listener.modelLiveUpdate(this, animate) }
  }

  fun delete(components: Collection<NlComponent?>) {
    // Group by parent and ask each one to participate
    WriteCommandAction.runWriteCommandAction(
      project,
      "Delete Component",
      null,
      { handleDeletion(components) },
      file,
    )
    notifyModified(ChangeType.DELETE)
  }

  /**
   * Creates a new component of the given type. It will optionally insert it as a child of the given
   * parent (and optionally right before the given sibling or null to append at the end.)
   *
   * Note: This operation can only be called when the caller is already holding a write lock. This
   * will be the case from [ViewHandler] callbacks such as [ViewHandler.onCreate] and
   * [DragHandler.commit].
   *
   * Note: The caller is responsible for calling [.notifyModified] if the creation completes
   * successfully.
   *
   * @param tag The XmlTag for the component.
   * @param parent The parent to add this component to.
   * @param before The sibling to insert immediately before, or null to append
   * @param insertType The reason for this creation.
   */
  fun createComponent(
    tag: XmlTag,
    parent: NlComponent?,
    before: NlComponent?,
    insertType: InsertType,
  ): NlComponent? {
    var addedTag = tag
    if (parent != null) {
      // Creating a component intended to be inserted into an existing layout
      val parentTag = parent.tagDeprecated
      addedTag =
        WriteAction.compute<XmlTag, RuntimeException> {
          if (before != null) {
            return@compute parentTag.addBefore(tag, before.tagDeprecated) as XmlTag
          }
          parentTag.addSubTag(tag, false)
        }
    }

    val child = createComponent(addedTag)

    parent?.addChild(child, before)
    if (child.postCreate(insertType)) {
      return child
    }
    return null
  }

  /** Simply create a component. In most cases you probably want [createComponent]. */
  fun createComponent(tag: XmlTag): NlComponent {
    val component = NlComponent(this, tag)
    myComponentRegistrar.accept(component)
    return component
  }

  fun createComponents(item: DnDTransferItem, insertType: InsertType): List<NlComponent> {
    val components: MutableList<NlComponent> = ArrayList(item.components.size)
    for (dndComponent in item.components) {
      val tag = XmlTagUtil.createTag(project, dndComponent.representation)
      val component =
        createComponent(tag, null, null, insertType)
          ?: // User may have cancelled
          return emptyList()
      component.postCreateFromTransferrable(dndComponent)
      components.add(component)
    }
    return components
  }

  /** Returns true if the specified components can be added to the specified receiver. */
  @JvmOverloads
  fun canAddComponents(
    toAdd: List<NlComponent>,
    receiver: NlComponent,
    before: NlComponent?,
    ignoreMissingDependencies: Boolean = false,
  ): Boolean {
    if (before != null && before.parent !== receiver) {
      return false
    }
    if (toAdd.isEmpty()) {
      return false
    }
    if (toAdd.stream().anyMatch { c: NlComponent -> !c.canAddTo(receiver) }) {
      return false
    }

    // If the receiver is a (possibly indirect) child of any of the dragged components, then reject
    // the operation
    if (NlComponentUtil.isDescendant(receiver, toAdd)) {
      return false
    }

    return ignoreMissingDependencies || checkIfUserWantsToAddDependencies(toAdd)
  }

  private fun checkIfUserWantsToAddDependencies(toAdd: List<NlComponent>): Boolean {
    // May bring up a dialog such that the user can confirm the addition of the new dependencies:
    return getInstance().checkIfUserWantsToAddDependencies(toAdd, facet)
  }

  /**
   * Adds components to the specified receiver before the given sibling. If insertType is a move the
   * components specified should be components from this model. The callback function
   * {@param #onComponentAdded} gives a chance to do additional task when components are added.
   */
  fun addComponents(
    toAdd: List<NlComponent>,
    receiver: NlComponent,
    before: NlComponent?,
    insertType: InsertType,
    onComponentAdded: Runnable?,
  ) {
    addComponents(toAdd, receiver, before, insertType, onComponentAdded, null)
  }

  /**
   * Adds components to the specified receiver before the given sibling. If insertType is a move the
   * components specified should be components from this model. The callback function
   * [onComponentAdded] gives a chance to do additional task when components are added.
   */
  @JvmOverloads
  fun addComponents(
    componentToAdd: List<NlComponent>,
    receiver: NlComponent,
    before: NlComponent?,
    insertType: InsertType,
    onComponentAdded: Runnable?,
    attributeUpdatingTask: Runnable?,
    groupId: String? = null,
  ) {
    // Fix for b/124381110
    // The components may be added by addComponentInWriteCommand after this method returns.
    // Make a copy of the components such that the caller can change the list without causing
    // problems.
    val toAdd = ImmutableList.copyOf(componentToAdd)

    // Note: we don't really need to check for dependencies if all we do is moving existing
    // components.
    if (!canAddComponents(toAdd, receiver, before, insertType == InsertType.MOVE)) {
      return
    }

    val callback = Runnable {
      addComponentInWriteCommand(
        toAdd,
        receiver,
        before,
        insertType,
        onComponentAdded,
        attributeUpdatingTask,
        groupId,
      )
    }
    if (insertType == InsertType.MOVE) {
      // The components are just moved, so there are no new dependencies.
      callback.run()
      return
    }

    ApplicationManager.getApplication().invokeLater {
      getInstance().addDependencies(toAdd, facet, false, callback)
    }
  }

  private fun addComponentInWriteCommand(
    toAdd: List<NlComponent>,
    receiver: NlComponent,
    before: NlComponent?,
    insertType: InsertType,
    onComponentAdded: Runnable?,
    attributeUpdatingTask: Runnable?,
    groupId: String?,
  ) {
    DumbService.getInstance(project).runWhenSmart {
      NlWriteCommandActionUtil.run(
        toAdd,
        generateAddComponentsDescription(toAdd, insertType),
        groupId,
      ) {
        // Update the attribute before adding components, if need.
        attributeUpdatingTask?.run()
        handleAddition(toAdd, receiver, before, insertType)
      }
      notifyModified(ChangeType.ADD_COMPONENTS)
      onComponentAdded?.run()
    }
  }

  /** Add tags component to the specified receiver before the given sibling. */
  fun addTags(
    added: List<NlComponent>,
    receiver: NlComponent,
    before: NlComponent?,
    insertType: InsertType,
  ) {
    NlWriteCommandActionUtil.run(added, generateAddComponentsDescription(added, insertType)) {
      for (component in added) {
        component.addTags(receiver, before, insertType)
      }
    }

    notifyModified(ChangeType.ADD_COMPONENTS)
  }

  /** Looks up the existing set of id's reachable from this model. */
  val ids: Set<String>
    get() {
      val resources: ResourceRepository = StudioResourceRepositoryManager.getAppResources(facet)
      var ids: Set<String> =
        HashSet(resources.getResources(ResourceNamespace.TODO(), ResourceType.ID).keySet())
      val pendingIds = pendingIds
      if (pendingIds.isNotEmpty()) {
        val all: MutableSet<String> = HashSet(pendingIds.size + ids.size)
        all.addAll(ids)
        all.addAll(pendingIds)
        ids = all
      }
      return ids
    }

  private fun handleAddition(
    added: List<NlComponent>,
    receiver: NlComponent,
    before: NlComponent?,
    insertType: InsertType,
  ) {
    for (component in added) {
      component.moveTo(receiver, before, insertType, ids)
    }
  }

  fun determineInsertType(
    dragType: DragType,
    item: DnDTransferItem?,
    asPreview: Boolean,
    generateIds: Boolean,
  ): InsertType {
    if (item != null && item.isFromPalette) {
      return if (asPreview) InsertType.CREATE_PREVIEW else InsertType.CREATE
    }
    return when (dragType) {
      DragType.CREATE -> if (asPreview) InsertType.CREATE_PREVIEW else InsertType.CREATE
      DragType.MOVE -> if (item != null && id != item.modelId) InsertType.COPY else InsertType.MOVE
      DragType.COPY -> InsertType.COPY
      else -> if (generateIds) InsertType.PASTE_GENERATE_NEW_IDS else InsertType.PASTE
    }
  }

  fun setTooltip(tooltip: String?) {
    modelTooltip = tooltip
  }

  override fun dispose() {
    isDisposed = true
    var shouldDeactivate: Boolean
    lintAnnotationsModel = null
    synchronized(activations) {
      // If there are no activations left, make sure we deactivate the model correctly
      shouldDeactivate = activations.isNotEmpty()
      activations.clear()
    }
    if (shouldDeactivate) {
      deactivate() // ensure listeners are unregistered if necessary
    }

    myListeners.clear()
  }

  override fun toString(): String {
    return NlModel::class.java.simpleName + " for " + virtualFile
  }

  // ---- Implements ModificationTracker ----
  /** Maintains multiple counter depending on what did change in the model */
  internal class ModelVersion {
    private val _version = AtomicLong()

    @Suppress("unused") private var lastReason: ChangeType? = null

    fun increase(reason: ChangeType?) {
      _version.incrementAndGet()
      lastReason = reason
    }

    val version: Long
      get() = _version.get()
  }

  override fun getModificationCount(): Long {
    return modelVersion.version
  }

  private fun fireNotifyModified(reason: ChangeType) {
    modelVersion.increase(reason)
    updateTheme()
    lastChangeType = reason
    myListeners.forEach { listener: ModelListener -> listener.modelChanged(this) }
  }

  fun notifyModified(reason: ChangeType) {
    if (!isActive) {
      // The model is not currently active so delay the notification. The queue will deliver this
      // notification
      // when it is active again.
      notifyModifiedViaUpdateQueue(ChangeType.MODEL_ACTIVATION)
    } else {
      fireNotifyModified(reason)
    }
  }

  /**
   * Schedules [notifyModified] to be called via an [MergingUpdateQueue], so once user activity
   * (typing) has stopped. [notifyModified] gets called on the EDT, just like the "original"
   * callback from [ResourceNotificationManager].
   */
  fun notifyModifiedViaUpdateQueue(reason: ChangeType) {
    updateQueue.queue(
      object : Update("edit") {
        override fun run() {
          fireNotifyModified(reason)
        }

        override fun canEat(update: Update): Boolean {
          return true
        }
      }
    )
  }

  /**
   * Flushes any pending updates created by [.notifyModifiedViaUpdateQueue]. After this method
   * returns, all pending updates will have been processed.
   */
  @TestOnly
  fun flushPendingUpdates() {
    updateQueue.run()
  }

  fun resetLastChange() {
    lastChangeType = null
  }

  fun setModelUpdater(modelUpdater: NlModelUpdaterInterface) {
    myModelUpdater = modelUpdater
  }

  companion object {
    const val DELAY_AFTER_TYPING_MS: Int = 250

    @JvmStatic
    fun builder(
      parent: Disposable,
      facet: AndroidFacet,
      file: VirtualFile,
      configuration: Configuration,
    ): NlModelBuilder {
      return NlModelBuilder(parent, facet, file, configuration)
    }

    /**
     * Method called by the [NlModelBuilder] to instantiate a new NlModel. Should only be called by
     * [NlModelBuilder].
     */
    @Slow
    internal fun create(
      parent: Disposable,
      facet: AndroidFacet,
      file: VirtualFile,
      configuration: Configuration,
      componentRegistrar: Consumer<NlComponent>,
      xmlFileProvider: BiFunction<Project, VirtualFile, XmlFile>,
      modelUpdater: NlModelUpdaterInterface?,
      dataContext: DataContext,
    ): NlModel {
      return NlModel(
        parent,
        facet,
        file,
        configuration,
        componentRegistrar,
        xmlFileProvider,
        modelUpdater,
        dataContext,
      )
    }

    private fun handleDeletion(components: Collection<NlComponent?>) {
      // Segment the deleted components into lists of siblings
      val siblingLists = NlComponentUtil.groupSiblings(components)

      // Notify parent components about children getting deleted
      for (parent in siblingLists.keySet()) {
        if (parent == null) {
          continue
        }

        val children = siblingLists[parent]

        if (!parent.mixin!!.maybeHandleDeletion(children)) {
          for (component in children) {
            val p = component.parent
            p?.removeChild(component)

            val tag = component.tagDeprecated
            if (tag.isValid) {
              val parentTag = tag.parent
              tag.delete()
              if (parentTag is XmlTag) {
                parentTag.collapseIfEmpty()
              }
            }
          }
        }
      }
    }

    private fun generateAddComponentsDescription(
      toAdd: List<NlComponent>,
      insertType: InsertType,
    ): String {
      val dragType = insertType.dragType
      var componentType = ""
      if (toAdd.size == 1) {
        val tagName = toAdd[0].tagName
        componentType = tagName.substring(tagName.lastIndexOf('.') + 1)
      }
      return dragType.getDescription(componentType)
    }
  }
}
