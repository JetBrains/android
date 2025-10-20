/*
 * Copyright (C) 2025 The Android Open Source Project
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
package com.android.tools.idea.layoutinspector.runningdevices.ui.rendering

import com.android.sdklib.deviceprovisioner.DeviceType
import com.android.tools.idea.flags.StudioFlags
import com.android.tools.idea.layoutinspector.LayoutInspector
import com.android.tools.idea.layoutinspector.metrics.statistics.SessionStatistics
import com.android.tools.idea.layoutinspector.resource.data.Display
import com.android.tools.idea.layoutinspector.runningdevices.navigateToSelectedViewFromRendererDoubleClick
import com.android.tools.idea.streaming.core.AbstractDisplayView
import com.google.common.annotations.VisibleForTesting
import com.intellij.openapi.Disposable
import com.intellij.openapi.util.Disposer

/**
 * Groups together the components required to render Layout Inspector UI on-top of running devices
 */
class RenderingComponents(
  disposable: Disposable,
  layoutInspector: LayoutInspector,
  val renderer: LayoutInspectorRenderer,
  val model: EmbeddedRendererModel,
  private val displayView: AbstractDisplayView,
) : Disposable {
  init {
    Disposer.register(disposable, this)
  }

  fun addRenderer() {
    displayView.add(renderer)
  }

  fun removeRenderer() {
    displayView.remove(renderer)
  }

  override fun dispose() {
    removeRenderer()
  }
}

/** Creates a [RenderingComponents] for each [AbstractDisplayView] passed as input. */
fun createRenderingComponents(
  disposable: Disposable,
  displayList: List<AbstractDisplayView>,
  layoutInspector: LayoutInspector,
  statsProvider: () -> SessionStatistics = { layoutInspector.currentClient.stats },
): List<RenderingComponents> {
  val isXrDevice = displayList.any { it.deviceType == DeviceType.XR_HEADSET }
  val useOnDeviceRendering =
    isXrDevice || StudioFlags.DYNAMIC_LAYOUT_INSPECTOR_ON_DEVICE_RENDERING.get()

  statsProvider().setOnDeviceRendering(useOnDeviceRendering)

  return if (useOnDeviceRendering) {
    val mainDisplayView = displayList.find { it.displayId == Display.MAIN_DISPLAY_ID }
    checkNotNull(mainDisplayView) { "Main display is missing" }

    // Rendering components are tied to the lifecycle of the tab - if the tab goes away they should
    // be disposed. But they are also tied to the lifecycle of the display - if the display goes
    // away they should be disposed.
    val combinedDisposable = combine(disposable, mainDisplayView)

    // For on-device rendering we want to always keep a single model shared by the renderers, having
    // multiple models for the same device would cause duplicated rendering instructions to be sent
    // to the device.
    val renderModel =
      EmbeddedRendererModel(
        parentDisposable = combinedDisposable,
        // In on-device rendering we don't want to filter nodes by display id. There is no concept
        // of display there, since everything is rendered on-top of the views.
        displayId = null,
        inspectorModel = layoutInspector.inspectorModel,
        treeSettings = layoutInspector.treeSettings,
        renderSettings = layoutInspector.renderSettings,
        navigateToSelectedViewOnDoubleClick = {
          layoutInspector.navigateToSelectedViewFromRendererDoubleClick()
        },
      )

    val onDeviceRendererModel =
      OnDeviceRendererModel(
        disposable = combinedDisposable,
        scope = layoutInspector.coroutineScope,
        renderModel = renderModel,
      )

    displayList.map { displayView ->
      val renderer =
        OnDeviceRendererPanel(
          disposable = combinedDisposable,
          scope = layoutInspector.coroutineScope,
          model = onDeviceRendererModel,
          enableSendRightClicksToDevice = { enable ->
            displayView.rightClicksAreSentToDevice = enable
          },
        )

      RenderingComponents(
        disposable = combinedDisposable,
        layoutInspector = layoutInspector,
        displayView = displayView,
        renderer = renderer,
        model = renderModel,
      )
    }
  } else {
    displayList.map { displayView ->
      // Rendering components are tied to the lifecycle of the tab - if the tab goes away they
      // should be disposed. But they are also tied to the lifecycle of the display - if the display
      // goes away they should be disposed.
      val combinedDisposable = combine(disposable, displayView)

      val renderModel =
        EmbeddedRendererModel(
          parentDisposable = combinedDisposable,
          displayId = displayView.displayId,
          inspectorModel = layoutInspector.inspectorModel,
          treeSettings = layoutInspector.treeSettings,
          renderSettings = layoutInspector.renderSettings,
          navigateToSelectedViewOnDoubleClick = {
            layoutInspector.navigateToSelectedViewFromRendererDoubleClick()
          },
        )

      val renderer =
        StudioRendererPanel(
          disposable = combinedDisposable,
          scope = layoutInspector.coroutineScope,
          renderModel = renderModel,
          displayRectangleProvider = { displayView.displayRectangle },
          screenScaleProvider = { displayView.screenScalingFactor },
          deviceDisplayDimensionProvider = {
            renderModel.inspectorModel.getDisplayDimension(displayView.displayId)
          },
          orientationQuadrantProvider = {
            calculateRotationCorrection(
              displayProvider = {
                layoutInspector.inspectorModel.resourceLookup.displays.find {
                  it.id == displayView.displayId
                }
              },
              displayOrientationQuadrant = { displayView.displayOrientationQuadrants },
              displayOrientationQuadrantCorrection = {
                displayView.displayOrientationCorrectionQuadrants
              },
            )
          },
        )
      RenderingComponents(
        disposable = combinedDisposable,
        layoutInspector = layoutInspector,
        displayView = displayView,
        renderer = renderer,
        model = renderModel,
      )
    }
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
  displayProvider: () -> Display?,
  displayOrientationQuadrant: () -> Int,
  displayOrientationQuadrantCorrection: () -> Int,
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
    when (displayProvider()?.orientation) {
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

private fun LayoutInspector.navigateToSelectedViewFromRendererDoubleClick() {
  navigateToSelectedViewFromRendererDoubleClick(
    scope = coroutineScope,
    inspectorModel = inspectorModel,
    client = currentClient,
    notificationModel = notificationModel,
  )
}

/** Returns a disposable that is disposed when the first of the two parents is disposed */
private fun combine(parentDisposable1: Disposable, parentDisposable2: Disposable): Disposable {
  val newDisposable = Disposer.newDisposable()
  Disposer.register(parentDisposable1) { Disposer.dispose(newDisposable) }
  Disposer.register(parentDisposable2) { Disposer.dispose(newDisposable) }
  return newDisposable
}
