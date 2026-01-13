/*
 * Copyright (C) 2023 The Android Open Source Project
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
package com.android.tools.idea.layoutinspector.runningdevices.ui

import com.android.annotations.concurrency.UiThread
import com.android.tools.idea.concurrency.createCoroutineScope
import com.android.tools.idea.layoutinspector.LayoutInspector
import com.android.tools.idea.layoutinspector.properties.DimensionUnitAction
import com.android.tools.idea.layoutinspector.runningdevices.actions.GearAction
import com.android.tools.idea.layoutinspector.runningdevices.actions.HorizontalSplitAction
import com.android.tools.idea.layoutinspector.runningdevices.actions.LeftVerticalSplitAction
import com.android.tools.idea.layoutinspector.runningdevices.actions.RightVerticalSplitAction
import com.android.tools.idea.layoutinspector.runningdevices.actions.SwapHorizontalSplitAction
import com.android.tools.idea.layoutinspector.runningdevices.actions.SwapLeftVerticalSplitAction
import com.android.tools.idea.layoutinspector.runningdevices.actions.SwapRightVerticalSplitAction
import com.android.tools.idea.layoutinspector.runningdevices.actions.SwapVerticalSplitAction
import com.android.tools.idea.layoutinspector.runningdevices.actions.ToggleDeepInspectAction
import com.android.tools.idea.layoutinspector.runningdevices.actions.UiConfig
import com.android.tools.idea.layoutinspector.runningdevices.actions.VerticalSplitAction
import com.android.tools.idea.layoutinspector.runningdevices.ui.rendering.RenderingComponents
import com.android.tools.idea.layoutinspector.runningdevices.ui.rendering.createRenderingComponents
import com.android.tools.idea.layoutinspector.ui.toolbar.actions.TargetSelectionActionFactory
import com.android.tools.idea.streaming.core.DeviceId
import com.google.common.annotations.VisibleForTesting
import com.intellij.ide.util.PropertiesComponent
import com.intellij.openapi.Disposable
import com.intellij.openapi.actionSystem.Separator
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.EDT
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import com.intellij.util.concurrency.EdtExecutorService
import com.intellij.util.ui.components.BorderLayoutPanel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.jetbrains.annotations.TestOnly

@VisibleForTesting
const val UI_CONFIGURATION_KEY =
  "com.android.tools.idea.layoutinspector.runningdevices.ui.uiconfigkey"

private val logger = Logger.getInstance(SelectedTabState::class.java)

/**
 * Represents the state of the selected tab.
 *
 * @param deviceId The id of selected tab.
 * @param tabComponents The components of the selected tab.
 * @param renderingComponents The components required for the rendering of Layout Inspector UI on
 *   the selected tab. It's a list because a tab can have multiple displays, in which case each
 *   display has its on [RenderingComponents].
 */
@UiThread
data class SelectedTabState(
  val disposable: Disposable,
  val project: Project,
  val deviceId: DeviceId,
  val tabComponents: TabComponents,
  val layoutInspector: LayoutInspector,
  val coroutineScope: CoroutineScope = disposable.createCoroutineScope(),
) : Disposable {

  private var uiConfig = UiConfig.HORIZONTAL
  private var wrapLogic: WrapLogic? = null

  /** Indicates if layout inspector is currently enabled for this tab. */
  private var isEnabled: Boolean = false

  private val toolbarState = ToolbarState()

  @VisibleForTesting
  var renderingComponents: List<RenderingComponents> = emptyList()
    set(value) {
      field.forEach { Disposer.dispose(it) }

      field = value

      value.forEach {
        it.model.setInterceptClicks(toolbarState.isDeepInspectEnabled.value)
        it.model.setOverlay(toolbarState.overlayImage.value)
        it.model.setOverlayTransparency(toolbarState.overlayTransparency.value)

        if (isEnabled) {
          it.addRenderer()
        }
      }
    }

  init {
    Disposer.register(disposable, this)

    // Try to restore UI config
    val uiConfigString = PropertiesComponent.getInstance().getValue(UI_CONFIGURATION_KEY)
    uiConfig = uiConfigString?.let { UiConfig.valueOf(uiConfigString) } ?: UiConfig.HORIZONTAL

    coroutineScope.launch(Dispatchers.EDT) {
      tabComponents.displayList.collect { displayViews ->
        val newRenderingComponents =
          createRenderingComponents(
            disposable = this@SelectedTabState,
            displayList = displayViews,
            layoutInspector = layoutInspector,
          )
        renderingComponents = newRenderingComponents
      }
    }

    coroutineScope.launch(Dispatchers.EDT) {
      toolbarState.isDeepInspectEnabled.collect {
        renderingComponents.forEach { comp -> comp.model.setInterceptClicks(it) }
      }
    }

    coroutineScope.launch(Dispatchers.EDT) {
      toolbarState.overlayImage.collect {
        renderingComponents.forEach { comp -> comp.model.setOverlay(it) }
      }
    }

    coroutineScope.launch(Dispatchers.EDT) {
      toolbarState.overlayTransparency.collect {
        renderingComponents.forEach { comp -> comp.model.setOverlayTransparency(it) }
      }
    }
  }

  @TestOnly
  fun enableLayoutInspector(uiConfig: UiConfig) {
    this.uiConfig = uiConfig
    enableLayoutInspector()
  }

  fun enableLayoutInspector() {
    ApplicationManager.getApplication().assertIsDispatchThread()

    isEnabled = true
    wrapUi(uiConfig)
    renderingComponents.forEach { it.addRenderer() }

    layoutInspector.processModel?.addSelectedProcessListeners(
      EdtExecutorService.getInstance(),
      selectedProcessListener,
    )

    logger.debug("Embedded Layout Inspector successfully enabled.")
  }

  /** Wrap the RD tab by injecting Embedded Layout Inspector UI. */
  private fun wrapUi(uiConfig: UiConfig) {
    PropertiesComponent.getInstance().setValue(UI_CONFIGURATION_KEY, uiConfig.name)

    wrapLogic = WrapLogic(parentDisposable = this, content = tabComponents.tabContentPanel)

    wrapLogic?.wrapContent { disposable, component ->
      val processPicker =
        TargetSelectionActionFactory.getSingleDeviceProcessPicker(
          layoutInspector,
          targetDeviceSerialNumber = deviceId.serialNumber,
        )

      val gearAction =
        GearAction(
          HorizontalSplitAction(::uiConfig, ::updateUi),
          SwapHorizontalSplitAction(::uiConfig, ::updateUi),
          VerticalSplitAction(::uiConfig, ::updateUi),
          SwapVerticalSplitAction(::uiConfig, ::updateUi),
          LeftVerticalSplitAction(::uiConfig, ::updateUi),
          SwapLeftVerticalSplitAction(::uiConfig, ::updateUi),
          RightVerticalSplitAction(::uiConfig, ::updateUi),
          SwapRightVerticalSplitAction(::uiConfig, ::updateUi),
          Separator.create(),
          DimensionUnitAction,
        )

      val toggleDeepInspectAction =
        ToggleDeepInspectAction(
          isSelected = { toolbarState.isDeepInspectEnabled.value },
          setSelected = { toolbarState.setDeepInspectEnabled(it) },
          isRendering = { layoutInspector.renderModel.isActive },
          connectedClientProvider = { layoutInspector.currentClient },
        )

      val rootPanel = BorderLayoutPanel()

      val toolbar =
        createToolbarPanel(
          disposable = disposable,
          targetComponent = rootPanel,
          layoutInspector = layoutInspector,
          processPicker = processPicker,
          extraActions = listOf(toggleDeepInspectAction, gearAction),
          toolbarState = toolbarState,
        )
      // We use a wrapper panel as the root so we can pass it as targetComponent to
      // createToolbarPanel. This is needed to make sure that all actions in the toolbar can resolve
      // Layout Inspector from the data context provided by LayoutInspectorRootPanel.
      rootPanel.addToCenter(toolbar)

      createLayoutInspectorPanel(
        project = project,
        disposable = disposable,
        layoutInspector = layoutInspector,
        uiConfig = uiConfig,
        centerPanel = component,
        toolbarPanel = rootPanel,
      )
    }
  }

  /** Unwrap the RD tab by removing Embedded Layout Inspector UI. */
  private fun unwrapUi() {
    wrapLogic?.let { Disposer.dispose(it) }
    wrapLogic = null
  }

  /** Update the UI by rearranging the panels */
  @VisibleForTesting
  fun updateUi(uiConfig: UiConfig) {
    if (this.uiConfig == uiConfig) {
      return
    } else {
      this.uiConfig = uiConfig
      // Unwrap the UI using the old ui config.
      unwrapUi()
      // Re-wrap using the new ui config.
      wrapUi(uiConfig)
    }
  }

  override fun dispose() {
    disableLayoutInspector()
  }

  private fun disableLayoutInspector() {
    ApplicationManager.getApplication().assertIsDispatchThread()

    isEnabled = false

    renderingComponents.forEach { it.removeRenderer() }
    unwrapUi()

    layoutInspector.processModel?.removeSelectedProcessListener(selectedProcessListener)

    tabComponents.tabContentPanel.revalidate()
    tabComponents.tabContentPanel.repaint()
  }

  private val selectedProcessListener = {
    // Sometimes on project close "SelectedTabContent#dispose" can be called after the listeners
    // are invoked.
    if (!project.isDisposed) {
      layoutInspector.inspectorClientSettings.inLiveMode = true
      toolbarState.setDeepInspectEnabled(false)
    }
  }
}
