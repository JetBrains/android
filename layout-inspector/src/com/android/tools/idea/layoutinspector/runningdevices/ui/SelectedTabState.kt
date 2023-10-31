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
import com.android.tools.adtui.workbench.WorkBench
import com.android.tools.idea.layoutinspector.LayoutInspector
import com.android.tools.idea.layoutinspector.dataProviderForLayoutInspector
import com.android.tools.idea.layoutinspector.model.InspectorModel
import com.android.tools.idea.layoutinspector.model.SelectionOrigin
import com.android.tools.idea.layoutinspector.model.ViewNode
import com.android.tools.idea.layoutinspector.properties.LayoutInspectorPropertiesPanelDefinition
import com.android.tools.idea.layoutinspector.runningdevices.SPLITTER_KEY
import com.android.tools.idea.layoutinspector.runningdevices.actions.GearAction
import com.android.tools.idea.layoutinspector.runningdevices.actions.ToggleDeepInspectAction
import com.android.tools.idea.layoutinspector.tree.LayoutInspectorTreePanelDefinition
import com.android.tools.idea.layoutinspector.ui.InspectorBanner
import com.android.tools.idea.layoutinspector.ui.toolbar.actions.TargetSelectionActionFactory
import com.android.tools.idea.layoutinspector.ui.toolbar.createEmbeddedLayoutInspectorToolbar
import com.android.tools.idea.streaming.core.DeviceId
import com.google.common.annotations.VisibleForTesting
import com.intellij.ide.DataManager
import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import com.intellij.ui.JBColor
import com.intellij.ui.OnePixelSplitter
import com.intellij.util.concurrency.EdtExecutorService
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.components.BorderLayoutPanel
import java.awt.BorderLayout

private const val WORKBENCH_NAME = "Layout Inspector"

/**
 * Represents the state of the selected tab.
 *
 * @param deviceId The id of selected tab.
 * @param tabComponents The components of the selected tab.
 * @param wrapLogic The logic used to wrap the tab in a workbench.
 */
@UiThread
data class SelectedTabState(
  val project: Project,
  val deviceId: DeviceId,
  val tabComponents: TabComponents,
  val layoutInspector: LayoutInspector,
  val layoutInspectorRenderer: LayoutInspectorRenderer =
    LayoutInspectorRenderer(
      tabComponents,
      layoutInspector.coroutineScope,
      layoutInspector.renderLogic,
      layoutInspector.renderModel,
      layoutInspector.notificationModel,
      { tabComponents.displayView.displayRectangle },
      { tabComponents.displayView.screenScalingFactor },
      {
        calculateRotationCorrection(
          layoutInspector.inspectorModel,
          displayOrientationQuadrant = { tabComponents.displayView.displayOrientationQuadrants },
          displayOrientationQuadrantCorrection = {
            tabComponents.displayView.displayOrientationCorrectionQuadrants
          }
        )
      },
      { layoutInspector.currentClient.stats },
    )
) : Disposable {

  var wrapLogic: WrapLogic? = null

  init {
    Disposer.register(tabComponents, this)
  }

  fun enableLayoutInspector() {
    ApplicationManager.getApplication().assertIsDispatchThread()

    wrapLogic =
      WrapLogic(this, tabComponents.tabContentPanel, tabComponents.tabContentPanelContainer)

    wrapLogic?.wrapComponent { disposable, centerPanel ->
      val inspectorPanel = BorderLayoutPanel()
      val toolsPanel = BorderLayoutPanel()

      val toggleDeepInspectAction =
        ToggleDeepInspectAction(
          { layoutInspectorRenderer.interceptClicks },
          { layoutInspectorRenderer.interceptClicks = it },
          { layoutInspector.currentClient }
        )

      val processPicker =
        TargetSelectionActionFactory.getSingleDeviceProcessPicker(
          layoutInspector,
          deviceId.serialNumber
        )
      val toolbar =
        createEmbeddedLayoutInspectorToolbar(
          toolsPanel,
          layoutInspector,
          processPicker,
          listOf(
            toggleDeepInspectAction,
            // TODO(next CL) add actions
            GearAction()
          )
        )

      val workBench = createLayoutInspectorWorkbench(project, disposable, layoutInspector)
      workBench.isFocusCycleRoot = false

      toolsPanel.add(toolbar, BorderLayout.NORTH)
      toolsPanel.add(workBench, BorderLayout.CENTER)
      workBench.component.border = JBUI.Borders.customLineTop(JBColor.border())

      val layoutInspectorProvider = dataProviderForLayoutInspector(layoutInspector)
      DataManager.registerDataProvider(workBench, layoutInspectorProvider)
      DataManager.registerDataProvider(toolbar, layoutInspectorProvider)

      Disposer.register(disposable) {
        DataManager.removeDataProvider(workBench)
        DataManager.removeDataProvider(toolbar)
      }

      val splitPanel =
        OnePixelSplitter(true, SPLITTER_KEY, 0.65f).apply {
          firstComponent = centerPanel
          secondComponent = toolsPanel
          setBlindZone { JBUI.insets(0, 1) }
        }

      val inspectorBanner = InspectorBanner(disposable, layoutInspector.notificationModel)
      inspectorPanel.add(inspectorBanner, BorderLayout.NORTH)
      inspectorPanel.add(splitPanel, BorderLayout.CENTER)
      inspectorPanel
    }
    tabComponents.displayView.add(layoutInspectorRenderer)

    layoutInspector.inspectorModel.addSelectionListener(selectionChangedListener)
    layoutInspector.processModel?.addSelectedProcessListeners(
      EdtExecutorService.getInstance(),
      selectedProcessListener
    )
  }

  override fun dispose() {
    disableLayoutInspector()
  }

  private fun disableLayoutInspector() {
    ApplicationManager.getApplication().assertIsDispatchThread()

    wrapLogic?.let { Disposer.dispose(it) }
    wrapLogic = null

    tabComponents.displayView.remove(layoutInspectorRenderer)
    layoutInspector.inspectorModel.removeSelectionListener(selectionChangedListener)
    layoutInspector.processModel?.removeSelectedProcessListener(selectedProcessListener)

    tabComponents.tabContentPanelContainer.revalidate()
    tabComponents.tabContentPanelContainer.repaint()
  }

  private val selectionChangedListener:
    (old: ViewNode?, new: ViewNode?, origin: SelectionOrigin) -> Unit =
    { _, _, _ ->
      layoutInspectorRenderer.refresh()
    }

  private val selectedProcessListener = {
    // Sometimes on project close "SelectedTabContent#dispose" can be called after the listeners
    // are invoked.
    if (!project.isDisposed) {
      layoutInspector.inspectorClientSettings.isCapturingModeOn = true
      layoutInspectorRenderer.interceptClicks = false
    }
  }

  private fun createLayoutInspectorWorkbench(
    project: Project,
    parentDisposable: Disposable,
    layoutInspector: LayoutInspector,
  ): WorkBench<LayoutInspector> {
    ApplicationManager.getApplication().assertIsDispatchThread()
    val workbench = WorkBench<LayoutInspector>(project, WORKBENCH_NAME, null, parentDisposable)
    val toolsDefinition =
      listOf(
        LayoutInspectorTreePanelDefinition(showGearAction = false, showHideAction = false),
        LayoutInspectorPropertiesPanelDefinition(showGearAction = false, showHideAction = false)
      )
    workbench.init(layoutInspector, toolsDefinition, false)

    return workbench
  }
}

/**
 * Returns the quadrant in which the rendering of Layout Inspector should be rotated in order to
 * match the rendering from Running Devices. It does this by calculating the rotation difference
 * between the rotation of the device and the rotation of the rendering from Running Devices.
 *
 * Both the rendering from RD and the device can be rotated in all 4 quadrants, independently of
 * each other. We use the diff to reconcile the difference in rotation, as ultimately the rendering
 * from LI should match the rendering of the display from RD.
 *
 * Note that the rendering from Layout Inspector should be rotated only sometimes, to match the
 * rendering from Running Devices. Here are a few examples:
 * * Device is in portrait mode, auto-rotation is off, running devices rendering has no rotation ->
 *   apply no rotation
 * * Device is in landscape mode, auto-rotation is off, running devices rendering has rotation to be
 *   horizontal -> apply rotation, because the app is in portrait mode in the device, so should be
 *   rotated to match rendering from RD.
 * * Device is in landscape mode, auto-rotation is on, running devices rendering has rotation to be
 *   horizontal -> apply no rotation, because the app is already in landscape mode, so no rotation
 *   is needed to match rendering from RD.
 *
 * Note that: when rendering a streamed device (as opposed to an emulator), the Running Devices Tool
 * Window fakes the rotation of the screen (b/273699961). This means that for those cases we can't
 * reliably use the rotation provided by the device to calculate the rotation for the Layout
 * Inspector rendering. In these cases we should use the rotation correction provided by the RD Tool
 * Window. But in the case of emulators, the rotation correction from Running Devices is always 0.
 * In these case we should calculate our own rotation correction.
 */
@VisibleForTesting
fun calculateRotationCorrection(
  layoutInspectorModel: InspectorModel,
  displayOrientationQuadrant: () -> Int,
  displayOrientationQuadrantCorrection: () -> Int
): Int {
  val orientationCorrectionFromRunningDevices = displayOrientationQuadrantCorrection()

  // Correction can be different from 0 only for streamed devices (as opposed to emulators).
  if (orientationCorrectionFromRunningDevices != 0) {
    return -orientationCorrectionFromRunningDevices
  }

  // The rotation of the display rendering coming from Running Devices.
  val displayRectangleOrientationQuadrant = displayOrientationQuadrant()

  // The rotation of the display coming from Layout Inspector.
  val layoutInspectorDisplayOrientationQuadrant =
    when (layoutInspectorModel.resourceLookup.displayOrientation) {
      0 -> 0
      90 -> 1
      180 -> 2
      270 -> 3
      else -> 0
    }

  // The difference in quadrant rotation between Layout Inspector rendering and the Running Devices
  // rendering.
  return (layoutInspectorDisplayOrientationQuadrant - displayRectangleOrientationQuadrant).mod(4)
}
