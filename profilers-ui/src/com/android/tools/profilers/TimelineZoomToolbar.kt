/*
 * Copyright (C) 2026 The Android Open Source Project
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
package com.android.tools.profilers

import com.android.tools.adtui.model.AspectObserver
import com.android.tools.adtui.model.Range
import com.android.tools.adtui.model.Timeline
import com.android.tools.adtui.stdui.CommonButton
import com.android.tools.adtui.stdui.DefaultContextMenuItem
import com.android.tools.profilers.analytics.FeatureTracker
import com.intellij.icons.AllIcons
import com.intellij.openapi.Disposable
import com.intellij.openapi.client.ClientSystemInfo
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.util.IconLoader
import icons.StudioIcons
import java.awt.event.InputEvent
import java.awt.event.KeyEvent
import javax.swing.JComponent
import javax.swing.JPanel
import javax.swing.KeyStroke

/** A reusable toolbar containing timeline zoom controls (Zoom In, Zoom Out, Reset Zoom, Zoom to Selection). */
class TimelineZoomToolbar(
  private val timelineSupplier: () -> Timeline?,
  private val featureTracker: FeatureTracker,
  containerComponent: JComponent,
  parentDisposable: Disposable? = null,
) : AspectObserver() {

  val component: JPanel = JPanel(ProfilerLayout.createToolbarLayout())

  val zoomOutButton: CommonButton =
    CommonButton(AllIcons.General.ZoomOut).apply {
      disabledIcon = IconLoader.getDisabledIcon(AllIcons.General.ZoomOut)
      addActionListener {
        timelineSupplier()?.zoomOut()
        featureTracker.trackZoomOut()
      }
    }

  val zoomInButton: CommonButton =
    CommonButton(AllIcons.General.ZoomIn).apply {
      disabledIcon = IconLoader.getDisabledIcon(AllIcons.General.ZoomIn)
      addActionListener {
        timelineSupplier()?.zoomIn()
        featureTracker.trackZoomIn()
      }
    }

  val resetZoomButton: CommonButton =
    CommonButton(StudioIcons.Common.RESET_ZOOM).apply {
      disabledIcon = IconLoader.getDisabledIcon(StudioIcons.Common.RESET_ZOOM)
      addActionListener {
        timelineSupplier()?.resetZoom()
        featureTracker.trackResetZoom()
      }
    }

  val zoomToSelectionButton: CommonButton =
    CommonButton(StudioIcons.Common.ZOOM_SELECT).apply {
      disabledIcon = IconLoader.getDisabledIcon(StudioIcons.Common.ZOOM_SELECT)
      addActionListener {
        timelineSupplier()?.let { timeline ->
          timeline.frameViewToRange(timeline.selectionRange)
          featureTracker.trackZoomToSelection()
        }
      }
    }

  val zoomOutAction: DefaultContextMenuItem
  val zoomInAction: DefaultContextMenuItem
  val resetZoomAction: DefaultContextMenuItem
  val zoomToSelectionAction: DefaultContextMenuItem

  private var activeTimeline: Timeline? = null

  init {
    zoomOutAction =
      DefaultContextMenuItem.Builder(ZOOM_OUT)
        .setContainerComponent(containerComponent)
        .setActionRunnable { zoomOutButton.doClick(0) }
        .setKeyStrokes(
          KeyStroke.getKeyStroke(KeyEvent.VK_MINUS, SHORTCUT_MODIFIER_MASK_NUMBER),
          KeyStroke.getKeyStroke(KeyEvent.VK_SUBTRACT, SHORTCUT_MODIFIER_MASK_NUMBER),
        )
        .build()
    zoomOutButton.toolTipText = zoomOutAction.defaultToolTipText
    component.add(zoomOutButton)

    zoomInAction =
      DefaultContextMenuItem.Builder(ZOOM_IN)
        .setContainerComponent(containerComponent)
        .setActionRunnable { zoomInButton.doClick(0) }
        .setKeyStrokes(
          KeyStroke.getKeyStroke(KeyEvent.VK_PLUS, SHORTCUT_MODIFIER_MASK_NUMBER),
          KeyStroke.getKeyStroke(KeyEvent.VK_EQUALS, SHORTCUT_MODIFIER_MASK_NUMBER),
          KeyStroke.getKeyStroke(KeyEvent.VK_ADD, SHORTCUT_MODIFIER_MASK_NUMBER),
        )
        .build()
    zoomInButton.toolTipText = zoomInAction.defaultToolTipText
    component.add(zoomInButton)

    resetZoomAction =
      DefaultContextMenuItem.Builder(RESET_ZOOM)
        .setContainerComponent(containerComponent)
        .setActionRunnable { resetZoomButton.doClick(0) }
        .setKeyStrokes(KeyStroke.getKeyStroke(KeyEvent.VK_NUMPAD0, 0), KeyStroke.getKeyStroke(KeyEvent.VK_0, 0))
        .build()
    resetZoomButton.toolTipText = resetZoomAction.defaultToolTipText
    component.add(resetZoomButton)

    zoomToSelectionAction =
      DefaultContextMenuItem.Builder(ZOOM_TO_SELECTION)
        .setContainerComponent(containerComponent)
        .setActionRunnable { zoomToSelectionButton.doClick(0) }
        .setEnableBooleanSupplier {
          val timeline = timelineSupplier()
          timeline != null && !timeline.selectionRange.isEmpty
        }
        .setKeyStrokes(KeyStroke.getKeyStroke(KeyEvent.VK_M, 0))
        .build()
    zoomToSelectionButton.toolTipText = zoomToSelectionAction.defaultToolTipText
    component.add(zoomToSelectionButton)

    if (parentDisposable != null) {
      Disposer.register(parentDisposable) { activeTimeline?.selectionRange?.removeDependencies(this) }
    }

    updateTimeline()
  }

  fun updateTimeline() {
    val newTimeline = timelineSupplier()
    if (activeTimeline !== newTimeline) {
      activeTimeline?.selectionRange?.removeDependencies(this)
      activeTimeline = newTimeline
      newTimeline?.selectionRange?.addDependency(this)?.onChange(Range.Aspect.RANGE) {
        zoomToSelectionButton.isEnabled = zoomToSelectionAction.isEnabled
      }
      zoomToSelectionButton.isEnabled = zoomToSelectionAction.isEnabled
    }
  }

  fun setButtonsEnabled(enabled: Boolean) {
    zoomOutButton.isEnabled = enabled
    zoomInButton.isEnabled = enabled
    resetZoomButton.isEnabled = enabled
    zoomToSelectionButton.isEnabled = enabled && zoomToSelectionAction.isEnabled
  }

  companion object {
    const val ZOOM_IN = "Zoom in"
    const val ZOOM_OUT = "Zoom out"
    const val RESET_ZOOM = "Reset zoom"
    const val ZOOM_TO_SELECTION = "Zoom to Selection"

    val SHORTCUT_MODIFIER_MASK_NUMBER
      get() = if (ClientSystemInfo.isMac()) InputEvent.META_DOWN_MASK else InputEvent.CTRL_DOWN_MASK
  }
}
