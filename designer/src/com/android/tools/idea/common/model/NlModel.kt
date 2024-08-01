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
import com.android.ide.common.rendering.api.ViewInfo
import com.android.tools.configurations.Configuration
import com.android.tools.idea.AndroidPsiUtils
import com.android.tools.idea.common.lint.LintAnnotationsModel
import com.android.tools.idea.common.surface.organization.OrganizationGroup
import com.android.tools.idea.common.type.DesignerEditorFileType
import com.android.tools.idea.common.type.typeOf
import com.android.tools.idea.rendering.BuildTargetReference
import com.android.tools.idea.res.ResourceNotificationManager
import com.android.tools.idea.util.ListenerCollection.Companion.createWithDirectExecutor
import com.google.common.annotations.VisibleForTesting
import com.intellij.openapi.Disposable
import com.intellij.openapi.actionSystem.DataContext
import com.intellij.openapi.module.Module
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.util.ModificationTracker
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.xml.XmlFile
import com.intellij.psi.xml.XmlTag
import com.intellij.util.Alarm
import com.intellij.util.ui.update.MergingUpdateQueue
import com.intellij.util.ui.update.Update
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.jetbrains.android.facet.AndroidFacet
import org.jetbrains.annotations.TestOnly
import java.util.Collections
import java.util.WeakHashMap
import java.util.concurrent.atomic.AtomicLong
import java.util.function.BiFunction
import java.util.function.Consumer

/**
 * Model for an XML file
 *
 * @param componentRegistrar Returns the responsible for registering an [NlComponent] to enhance it
 *   with layout-specific properties and methods.
 * @param xmlFileProvider [LayoutlibSceneManager] requires the file from model to be an [XmlFile] to
 *   be able to render it. This is true in case of layout file and some others as well. However, we
 *   want to use model to render other file types (e.g. Java and Kotlin source files that contain
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
  val buildTarget: BuildTargetReference,
  val virtualFile: VirtualFile,
  open val configuration: Configuration,
  private val componentRegistrar: Consumer<NlComponent>,
  private val xmlFileProvider: BiFunction<Project, VirtualFile, XmlFile>,
  // TODO must not be a DataContext, convert to UiDataProvider or avoid altogether.
  //   A data-context must not be queried during another data-context creation.
  override var dataContext: DataContext,
) : ModificationTracker, DataContextHolder {

  val treeWriter =
    NlTreeWriter(buildTarget.facet, { file }, ::notifyModified, { createComponent(it) })
  val treeReader = NlTreeReader { file }
  val themeUpdater = NlThemeUpdater({ configuration }, this)

  /**
   * Adds information to the model from a render result. A given model can use different updaters
   * depending on what its usage requires. E.g. interactive preview may need less information from
   * an [NlModel] than a standard preview, so different updaters can be used in those cases.
   */
  private var modelUpdater: NlModelUpdaterInterface = DefaultModelUpdater()

  private val listeners = createWithDirectExecutor<ModelListener>()

  private val _displayName = MutableStateFlow<String?>(null)

  /** Model name. This can be used when multiple models are displayed at the same time */
  val modelDisplayName: StateFlow<String?> = _displayName.asStateFlow()

  fun setDisplayName(value: String?) {
    _displayName.value = value
  }

  private val _tooltip = MutableStateFlow<String?>(null)

  /** Text to display when displaying a tooltip related to this model */
  val tooltip: StateFlow<String?> = _tooltip.asStateFlow()

  fun setTooltip(value: String?) {
    _tooltip.value = value
  }

  // Deliberately not rev'ing the model version and firing changes here;
  // we know only the warnings layer cares about this change and can be
  // updated by a single repaint
  var lintAnnotationsModel: LintAnnotationsModel? = null
  val type: DesignerEditorFileType = file.typeOf()

  private val activations: MutableSet<Any> = Collections.newSetFromMap(WeakHashMap())
  private val modelVersion = ModelVersion()
  private var configurationModificationCount: Long = configuration.modificationCount

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

  var isDisposed: Boolean = false
    private set

  /**
   * Indicate which group this NlModel belongs. This can be used to categorize the NlModel when
   * rendering or layouting.
   */
  var organizationGroup: OrganizationGroup? = null

  init {
    Disposer.register(parent, this)
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
    if (buildTarget.facet.isDisposed) {
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

      if (configuration.modificationCount != configurationModificationCount) {
        themeUpdater.updateTheme()
      }
      listeners.forEach { listener: ModelListener -> listener.modelActivated(this) }
      updateQueue.resume()
      return true
    } else {
      return false
    }
  }

  private fun deactivate() {
    configurationModificationCount = configuration.modificationCount
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
    get() = xmlFileProvider.apply(project, virtualFile)

  fun syncWithPsi(newRoot: XmlTag, roots: List<TagSnapshotTreeNode>) {
    modelUpdater.updateFromTagSnapshot(this, newRoot, roots)
  }

  fun updateAccessibility(viewInfos: List<ViewInfo>) {
    modelUpdater.updateFromViewInfo(this, viewInfos)
  }

  /**
   * Adds a new [ModelListener]. If the listener already exists, this method will make sure that the
   * listener is only added once.
   */
  fun addListener(listener: ModelListener) {
    listeners.add(listener)
  }

  fun removeListener(listener: ModelListener) {
    listeners.remove(listener)
  }

  /**
   * Calls all the listeners [ModelListener.modelDerivedDataChanged] method.
   *
   * TODO: move this mechanism to [LayoutlibSceneManager], or, ideally, remove the need for it
   *   entirely by moving all the derived data into the Scene.
   */
  fun notifyListenersModelDerivedDataChanged() {
    listeners.forEach { listener: ModelListener -> listener.modelDerivedDataChanged(this) }
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
    listeners.forEach { listener: ModelListener -> listener.modelChangedOnLayout(this, animate) }
  }

  val facet: AndroidFacet
    get() = buildTarget.facet

  val module: Module
    get() = buildTarget.module

  val project: Project
    get() = buildTarget.project

  /**
   * This will warn model listeners that the model has been changed "live", without the attributes
   * of components being actually committed. Listeners such as Scene Managers will likely want for
   * example to schedule a layout pass in reaction to that callback.
   *
   * @param animate should the changes be animated or not.
   */
  fun notifyLiveUpdate(animate: Boolean) {
    listeners.forEach { listener -> listener.modelLiveUpdate(this, animate) }
  }

  /** Simply create a component. In most cases you probably want [NlTreeWriter.createComponent]. */
  fun createComponent(tag: XmlTag): NlComponent {
    val component = NlComponent(this, tag)
    componentRegistrar.accept(component)
    return component
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

    listeners.clear()
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
    themeUpdater.updateTheme()
    lastChangeType = reason
    listeners.forEach { listener: ModelListener -> listener.modelChanged(this) }
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
    updateQueue.flush()
  }

  fun resetLastChange() {
    lastChangeType = null
  }

  fun setModelUpdater(modelUpdater: NlModelUpdaterInterface) {
    this.modelUpdater = modelUpdater
  }

  companion object {
    const val DELAY_AFTER_TYPING_MS: Int = 250

    fun getDefaultFile(project: Project, virtualFile: VirtualFile) =
      AndroidPsiUtils.getPsiFileSafely(project, virtualFile) as XmlFile
  }

  /** An [NlModel] builder */
  class Builder(
    val parentDisposable: Disposable,
    val buildTarget: BuildTargetReference,
    val file: VirtualFile,
    val configuration: Configuration,
  ) {
    private var componentRegistrar: Consumer<NlComponent> = Consumer {}
    private var xmlFileProvider: BiFunction<Project, VirtualFile, XmlFile> =
      BiFunction { project, virtualFile ->
        getDefaultFile(project, virtualFile)
      }
    private var dataContext: DataContext = DataContext.EMPTY_CONTEXT

    fun withComponentRegistrar(componentRegistrar: Consumer<NlComponent>): Builder = also {
      this.componentRegistrar = componentRegistrar
    }

    fun withXmlProvider(xmlFileProvider: BiFunction<Project, VirtualFile, XmlFile>): Builder =
      also {
        this.xmlFileProvider = xmlFileProvider
      }

    fun withDataContext(dataContext: DataContext): Builder = also { this.dataContext = dataContext }

    /** Instantiate a new [NlModel]. */
    @Slow
    fun build(): NlModel =
      NlModel(
        parentDisposable,
        buildTarget,
        file,
        configuration,
        componentRegistrar,
        xmlFileProvider,
        dataContext,
      )
  }
}
