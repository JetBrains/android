/*
 * Copyright (C) 2017 The Android Open Source Project
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
package com.android.tools.idea.uibuilder.property

import com.android.SdkConstants.ANDROID_URI
import com.android.SdkConstants.ATTR_LAYOUT_MARGIN_END
import com.android.SdkConstants.ATTR_LAYOUT_MARGIN_LEFT
import com.android.SdkConstants.ATTR_LAYOUT_MARGIN_RIGHT
import com.android.SdkConstants.ATTR_LAYOUT_MARGIN_START
import com.android.SdkConstants.ATTR_PARENT_TAG
import com.android.SdkConstants.TOOLS_URI
import com.android.tools.idea.common.command.NlWriteCommandActionUtil
import com.android.tools.idea.common.model.ModelListener
import com.android.tools.idea.common.model.NlComponent
import com.android.tools.idea.common.model.NlModel
import com.android.tools.idea.common.surface.DesignSurface
import com.android.tools.idea.common.surface.DesignSurfaceListener
import com.android.tools.idea.common.surface.SceneView
import com.android.tools.idea.model.StudioAndroidModuleInfo
import com.android.tools.idea.refactoring.rtl.RtlSupportProcessor
import com.android.tools.idea.res.psi.ResourceRepositoryToPsiResolver
import com.android.tools.idea.uibuilder.analytics.NlUsageTracker
import com.android.tools.idea.uibuilder.api.AccessoryPanelInterface
import com.android.tools.idea.uibuilder.api.AccessorySelectionListener
import com.android.tools.idea.uibuilder.scene.RenderListener
import com.android.tools.idea.uibuilder.surface.AccessoryPanelListener
import com.android.tools.idea.uibuilder.surface.NlDesignSurface
import com.android.tools.idea.uibuilder.surface.ScreenView
import com.android.tools.property.panel.api.PropertiesModel
import com.android.tools.property.panel.api.PropertiesModelListener
import com.android.tools.property.panel.api.PropertiesTable
import com.google.common.annotations.VisibleForTesting
import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.ModalityState
import com.intellij.openapi.application.ReadAction
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import com.intellij.pom.Navigatable
import com.intellij.psi.xml.XmlTag
import com.intellij.util.Alarm
import com.intellij.util.concurrency.AppExecutorUtil
import com.intellij.util.ui.update.MergingUpdateQueue
import com.intellij.util.ui.update.Update
import org.jetbrains.android.facet.AndroidFacet
import org.jetbrains.annotations.TestOnly
import java.util.Collections
import java.util.concurrent.Callable
import java.util.function.Consumer
import javax.swing.event.ChangeListener

private const val UPDATE_QUEUE_NAME = "propertysheet"
private const val UPDATE_DELAY_MILLI_SECONDS = 250

/**
 * [PropertiesModel] for Nele design surface properties.
 */
open class NlPropertiesModel(
  parentDisposable: Disposable,
  val provider: PropertiesProvider,
  val facet: AndroidFacet,
  @get:VisibleForTesting
  val updateQueue: MergingUpdateQueue,
  private val updateOnComponentSelectionChanges: Boolean
) : PropertiesModel<NlPropertyItem>, Disposable {
  val project: Project = facet.module.project

  private val updateIdentity = Any()
  private val listeners: MutableList<PropertiesModelListener<NlPropertyItem>> = mutableListOf()
  private val designSurfaceListener = PropertiesDesignSurfaceListener()
  private val modelListener = NlModelListener()
  private val accessoryPanelListener = AccessoryPanelListener { panel: AccessoryPanelInterface? -> usePanel(panel) }
  private val accessorySelectionListener = AccessorySelectionListener { panel, type, accessory, selection ->
    handlePanelSelectionUpdate(panel, type, accessory, selection)
  }
  private val renderListener = RenderListener { handleRenderingCompleted() }
  private var activeSurface: DesignSurface<*>? = null
  private var activeSceneView: SceneView? = null
  private var activePanel: AccessoryPanelInterface? = null
  protected var defaultValueProvider: DefaultPropertyValueProvider? = null
  private val liveComponents = mutableListOf<NlComponent>()
  private val liveChangeListener: ChangeListener = ChangeListener { firePropertyValueChangeIfNeeded() }

  constructor(parentDisposable: Disposable, facet: AndroidFacet, updateQueue: MergingUpdateQueue) :
    this(parentDisposable, NlPropertiesProvider(facet), facet, updateQueue, true)

  constructor(parentDisposable: Disposable, facet: AndroidFacet) :
    this(parentDisposable, facet, MergingUpdateQueue(UPDATE_QUEUE_NAME, UPDATE_DELAY_MILLI_SECONDS, true, null, parentDisposable, null,
                                                     Alarm.ThreadToUse.SWING_THREAD))

  var surface: DesignSurface<*>?
    get() = activeSurface
    set(value) = useDesignSurface(value)

  /**
   * If true the value in an editor should show the resolved value of a property.
   */
  var showResolvedValues = false
    set (value) {
      field = value
      firePropertyValueChangeIfNeeded()
    }

  @VisibleForTesting
  var updateCount = 0
    protected set

  @VisibleForTesting
  var lastUpdateCompleted: Boolean = true
    protected set

  init {
    @Suppress("LeakingThis")
    Disposer.register(parentDisposable, this)
  }

  override fun dispose() {
    properties = PropertiesTable.emptyTable()
    useDesignSurface(null)
  }

  override fun deactivate() {
    properties = PropertiesTable.emptyTable()
  }

  override fun addListener(listener: PropertiesModelListener<NlPropertyItem>) {
    listeners.add(listener)
  }

  override fun removeListener(listener: PropertiesModelListener<NlPropertyItem>) {
    listeners.remove(listener)
  }

  override var properties: PropertiesTable<NlPropertyItem> = PropertiesTable.emptyTable()
    protected set

  @TestOnly
  fun setPropertiesInTest(testProperties: PropertiesTable<NlPropertyItem>) {
    properties = testProperties
  }

  private fun logPropertyValueChanged(property: NlPropertyItem) {
    NlUsageTracker.getInstance(activeSurface).logPropertyChange(property, -1)
  }

  fun provideDefaultValue(property: NlPropertyItem): String? {
    return defaultValueProvider?.provideDefaultValue(property)
  }

  open fun getPropertyTag(property: NlPropertyItem): XmlTag? = property.firstTag

  open fun getPropertyValue(property: NlPropertyItem): String? {
    ApplicationManager.getApplication().assertReadAccessAllowed()
    var prev: String? = null
    for (component in property.components) {
      val value = component.getLiveAttribute(property.namespace, property.name) ?: return null
      prev = prev ?: value
      if (value != prev) return null
    }
    return prev
  }

  open fun setPropertyValue(property: NlPropertyItem, newValue: String?) {
    assert(ApplicationManager.getApplication().isDispatchThread)
    if (property.project.isDisposed || property.components.isEmpty()) {
      return
    }
    val componentName = if (property.components.size == 1) property.components[0].tagName else "Multiple"

    @Suppress("DEPRECATION")
    property.components.forEach { it.snapshot?.setAttribute(property.name, property.namespace, null, newValue) }


    ApplicationManager.getApplication().invokeLater({
      NlWriteCommandActionUtil.run(property.components, "Set $componentName.${property.name} to $newValue") {
        property.components.forEach { it.setAttribute(property.namespace, property.name, newValue) }
        val compatibleAttribute = compatibleMarginAttribute(property)
        if (compatibleAttribute != null) {
          property.components.forEach { it.setAttribute(property.namespace, compatibleAttribute, newValue) }
        }
        logPropertyValueChanged(property)
        if (property.namespace == TOOLS_URI) {
          if (newValue != null) {
            // A tools property may not be in the current set of possible properties. So add it now:
            if (properties.isEmpty) {
              properties = provider.createEmptyTable()
            }
            properties.put(property)
          }

          if (property.name == ATTR_PARENT_TAG) {
            // When the "parentTag" attribute is set on a <merge> tag,
            // we may have a different set of available properties available,
            // since the attributes of the "parentTag" are included if set.
            firePropertiesGenerated()
          }
        }
      }
    }, { Disposer.isDisposed(this) })
  }

  private fun compatibleMarginAttribute(property: NlPropertyItem): String? {
    if (property.namespace != ANDROID_URI ||
        StudioAndroidModuleInfo.getInstance(facet).minSdkVersion.apiLevel >= RtlSupportProcessor.RTL_TARGET_SDK_START) {
      return null
    }
    return when (property.name) {
      ATTR_LAYOUT_MARGIN_LEFT -> if (isRTL) ATTR_LAYOUT_MARGIN_END else ATTR_LAYOUT_MARGIN_START
      ATTR_LAYOUT_MARGIN_RIGHT -> if (isRTL) ATTR_LAYOUT_MARGIN_START else ATTR_LAYOUT_MARGIN_END
      else -> null
    }
  }

  private val isRTL: Boolean
    get() = activeSceneView?.scene?.isInRTL ?: false

  private fun useDesignSurface(surface: DesignSurface<*>?) {
    if (surface != activeSurface) {
      updateDesignSurface(activeSurface, surface)
      activeSurface = surface
      (activeSceneView as? ScreenView)?.sceneManager?.removeRenderListener(renderListener)
      activeSceneView = surface?.focusedSceneView
      (activeSceneView as? ScreenView)?.sceneManager?.addRenderListener(renderListener)
    }
    makeInitialSelection(surface, activePanel)
  }

  private fun makeInitialSelection(surface: DesignSurface<*>?, panel: AccessoryPanelInterface?) {
    if (panel != null) {
      panel.requestSelection()
    }
    else if (surface != null) {
      val newSelection: List<NlComponent> = activeSceneView?.selectionModel?.selection ?: emptyList()
      designSurfaceListener.componentSelectionChanged(surface, newSelection)
    }
  }

  private fun updateDesignSurface(old: DesignSurface<*>?, new: DesignSurface<*>?) {
    old?.model?.removeListener(modelListener)
    new?.model?.addListener(modelListener)
    if (updateOnComponentSelectionChanges) {
      old?.removeListener(designSurfaceListener)
      new?.addListener(designSurfaceListener)
    }
    (old as? NlDesignSurface)?.accessoryPanel?.removeAccessoryPanelListener(accessoryPanelListener)
    (new as? NlDesignSurface)?.accessoryPanel?.addAccessoryPanelListener(accessoryPanelListener)
    useCurrentPanel(new)
  }

  private fun useCurrentPanel(surface: DesignSurface<*>?) {
    usePanel((surface as? NlDesignSurface)?.accessoryPanel?.currentPanel)
  }

  private fun usePanel(panel: AccessoryPanelInterface?) {
    if (panel != activePanel) {
      setAccessorySelectionListener(activePanel, panel)
      activePanel = panel
    }
  }

  private fun setAccessorySelectionListener(old: AccessoryPanelInterface?, new: AccessoryPanelInterface?) {
    old?.removeListener(accessorySelectionListener)
    new?.addListener(accessorySelectionListener)
  }

  private fun scheduleSelectionUpdate(
    surface: DesignSurface<*>?,
    panel: AccessoryPanelInterface?,
    type: Any?,
    accessory: Any?,
    components: List<NlComponent>
  ) {
    updateLiveListeners(Collections.emptyList())
    updateQueue.queue(object : Update(updateIdentity) {
      override fun run() {
        handleSelectionUpdate(surface, panel, type, accessory, components)
      }
    })
  }

  private fun getRootComponent(surface: DesignSurface<*>?): List<NlComponent> {
    return surface?.models?.singleOrNull()?.components?.singleOrNull()?.let {listOf(it)} ?: return emptyList()
  }

  protected open fun wantSelectionUpdate(
    surface: DesignSurface<*>?,
    activeSurface: DesignSurface<*>?,
    accessoryPanel: AccessoryPanelInterface?,
    activePanel: AccessoryPanelInterface?,
    selectedAccessoryType: Any?,
    selectedAccessory: Any?
  ): Boolean {
    return surface != null &&
           surface == activeSurface &&
           accessoryPanel == accessoryPanel &&
           selectedAccessoryType == null &&
           selectedAccessory == null &&
           !facet.isDisposed
  }

  private fun handleSelectionUpdate(
    surface: DesignSurface<*>?,
    panel: AccessoryPanelInterface?,
    type: Any?,
    accessory: Any?,
    components: List<NlComponent>
  ) {
    // Obtaining the properties, especially the first time around on a big project
    // can take close to a second, so we do it on a separate thread..
    val wantUpdate = { wantSelectionUpdate(surface, activeSurface, panel, activePanel, type, accessory) }
    loadProperties(type, accessory, components, wantUpdate)
  }

  protected fun updateLiveListeners(components: List<NlComponent>) {
    synchronized(liveComponents) {
      liveComponents.forEach { it.removeLiveChangeListener(liveChangeListener) }
      liveComponents.clear()
      liveComponents.addAll(components)
      liveComponents.forEach { it.addLiveChangeListener(liveChangeListener) }
    }
  }

  private fun sameAsTheCurrentLiveListeners(components: List<NlComponent>): Boolean {
    synchronized(liveComponents) {
      return components == liveComponents
    }
  }

  private fun handlePanelSelectionUpdate(
    panel: AccessoryPanelInterface,
    selectedAccessoryType: Any?,
    selectedAccessory: Any?,
    components: List<NlComponent>
  ) {
    if (wantSelectionUpdate(activeSurface, activeSurface, panel, activePanel, selectedAccessoryType, selectedAccessory)) {
      scheduleSelectionUpdate(activeSurface, panel, selectedAccessoryType, selectedAccessory, components)
    }
  }

  protected open fun loadProperties(type: Any?, accessory: Any?, components: List<NlComponent>, wantUpdate: () -> Boolean) {
    if (!wantUpdate()) {
      return
    }
    lastUpdateCompleted = false
    val getProperties = Callable {
      val newProperties = provider.getProperties(this@NlPropertiesModel, accessory, components)
      defaultValueProvider?.clearCache()
      newProperties
    }
    val notifyUI = Consumer<PropertiesTable<NlPropertyItem>> { newProperties ->
      try {
        if (wantUpdate()) {
          updateLiveListeners(components)
          properties = newProperties
          defaultValueProvider = createDefaultPropertyValueProvider()
          firePropertiesGenerated()
        }
      }
      finally {
        updateCount++
        lastUpdateCompleted = true
      }
    }
    ReadAction
      .nonBlocking(getProperties)
      .inSmartMode(project)
      .expireWith(this)
      .finishOnUiThread(ModalityState.defaultModalityState(), notifyUI)
      .submit(AppExecutorUtil.getAppExecutorService())
  }

  private fun handleRenderingCompleted() {
    if (defaultValueProvider?.hasDefaultValuesChanged() == true) {
      ApplicationManager.getApplication().invokeLater { firePropertyValueChangeIfNeeded() }
    }
  }

  fun firePropertiesGenerated() {
    listeners.toTypedArray().forEach { it.propertiesGenerated(this) }
  }

  fun firePropertyValueChanged() {
    listeners.toTypedArray().forEach { it.propertyValuesChanged(this) }
  }

  fun firePropertyValueChangeIfNeeded() {
    val components = activeSurface?.selectionModel?.selection ?: return
    if (components.isEmpty() || !sameAsTheCurrentLiveListeners(components)) {
      // If there are no components currently selected, there is nothing to update.
      // If the currently selected components are different from the components being shown,
      // there must be a pending selection update and therefore no need to update the property
      // values.
      return
    }
    firePropertyValueChanged()
  }

  private fun createDefaultPropertyValueProvider(): DefaultPropertyValueProvider? {
    val view = activeSceneView ?: return null
    return NlDefaultPropertyValueProvider(view.sceneManager)
  }

  open fun browseToValue(property: NlPropertyItem) {
    val tag = property.firstTag ?: return
    val resourceReference = property.resolveValueAsReference(property.value) ?: return
    val folderConfiguration = property.getFolderConfiguration() ?: return
    val targetElement = ResourceRepositoryToPsiResolver.getBestGotoDeclarationTarget(resourceReference, tag, folderConfiguration) ?: return
    if (targetElement is Navigatable) {
      targetElement.navigate(true)
    }
  }

  private inner class PropertiesDesignSurfaceListener : DesignSurfaceListener {
    override fun componentSelectionChanged(surface: DesignSurface<*>, newSelection: List<NlComponent>) {
      val displayedComponents = if (newSelection.isNotEmpty()) newSelection else getRootComponent(surface)
      if (activePanel == null && !sameAsTheCurrentLiveListeners(displayedComponents)) {
        scheduleSelectionUpdate(surface, null, null, null, displayedComponents)
      }
    }
  }

  private inner class NlModelListener : ModelListener {
    override fun modelDerivedDataChanged(model: NlModel) {
      // Move the handling onto the event dispatch thread in case this notification is sent from a different thread:
      ApplicationManager.getApplication().invokeLater { firePropertyValueChangeIfNeeded() }
    }

    override fun modelLiveUpdate(model: NlModel, animate: Boolean) {
      // Move the handling onto the event dispatch thread in case this notification is sent from a different thread:
      ApplicationManager.getApplication().invokeLater { firePropertyValueChangeIfNeeded() }
    }
  }
}
