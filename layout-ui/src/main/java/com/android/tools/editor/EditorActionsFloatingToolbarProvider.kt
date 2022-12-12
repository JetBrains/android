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
package com.android.tools.editor

import com.android.tools.adtui.ui.DesignSurfaceToolbarUI
import com.android.tools.adtui.util.ActionToolbarUtil
import com.intellij.openapi.Disposable
import com.intellij.openapi.actionSystem.ActionGroup
import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.openapi.actionSystem.ActionToolbar
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.impl.ActionButton
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.util.Disposer
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.UIUtil
import java.awt.GridBagConstraints
import java.awt.GridBagLayout
import java.awt.event.AdjustmentEvent
import java.awt.event.ContainerAdapter
import java.awt.event.ContainerEvent
import java.awt.event.MouseAdapter
import java.awt.event.MouseEvent
import javax.swing.Box
import javax.swing.BoxLayout
import javax.swing.JComponent
import javax.swing.JPanel
import javax.swing.Timer

const val zoomActionPlace = "ZoomActionsToolbar"
const val zoomLabelPlace = "ZoomLabelToolbar"
const val otherActionsPlace = "DesignSurfaceFloatingOtherActionsToolbar"

private val VERTICAL_PANEL_MARGINS get() = JBUI.insets(0, 4, 4, 0)

/**
 * Provides the floating action toolbar for editor. It provides support for pan and zoom specifically, and arbitrary actions can be added
 * in additional toolbar segments.
 * [component] is used for data-context retrieval. See [ActionToolbar.setTargetComponent].
 */
abstract class EditorActionsFloatingToolbarProvider(
  private val component: JComponent,
  parentDisposable: Disposable
) : PanZoomListener, Disposable {

  val floatingToolbar: JComponent = JPanel(GridBagLayout()).apply { isOpaque = false }

  private val zoomToolbars: MutableList<ActionToolbar> = mutableListOf()
  private val otherToolbars: MutableMap<ActionGroup, ActionToolbar> = mutableMapOf()

  private val emptyBoxConstraints = GridBagConstraints().apply {
    gridx = 0
    gridy = 0
    gridwidth = 2
    gridheight = 1
    weightx = 1.0
    weighty = 1.0
    anchor = GridBagConstraints.LAST_LINE_END
    insets = JBUI.insets(0)
  }
  private val zoomControlsConstraints
    get() = GridBagConstraints().apply {
      gridx = 1
      gridy = getActionGroups().otherGroups.size + 2
      anchor = GridBagConstraints.FIRST_LINE_END
      insets = VERTICAL_PANEL_MARGINS
    }
  private val zoomLabelConstraints
    get() = GridBagConstraints().apply {
      gridx = 0
      gridy = getActionGroups().otherGroups.size + 2
      weightx = 1.0
      anchor = GridBagConstraints.FIRST_LINE_END
      insets = VERTICAL_PANEL_MARGINS
    }

  /**
   * The Zoom Label toolbar panel. It should only be visible for a short time after changing zoom level and stay visible while interacting
   * with zoom controls.
   */
  private var hiddenZoomLabelComponent: JComponent? = null

  /**
   * Timer used to automatically set the Zoom Label panel to not visible after a period of time.
   * */
  private var hiddenZoomLabelTimer: Timer? = ApplicationManager
    .getApplication()
    .takeUnless {
      it.isUnitTestMode
    }?.let {
      Timer(2000) {
        hiddenZoomLabelComponent?.isVisible = false
      }.apply {
        isRepeats = false
      }
    }

  init {
    Disposer.register(parentDisposable, this)
  }

  fun findActionButton(group: ActionGroup, action: AnAction): ActionButton? {
    val toolbar = otherToolbars[group] ?: return null
    return ActionToolbarUtil.findActionButton(toolbar, action)
  }

  protected fun updateToolbar() {
    val actionGroups = getActionGroups()
    val actionManager = ActionManager.getInstance()
    val zoomActionGroup = actionGroups.zoomControlsGroup?.let {
      createToolbar("ZoomActionsToolbar", actionManager, it, component)
    }
    val zoomLabelToolbar = actionGroups.zoomLabelGroup?.let {
      createToolbar("ZoomLabelToolbar", actionManager, it, component).apply {
        component.border = JBUI.Borders.empty(2)
      }
    }
    zoomToolbars.apply {
      clear()
      if (zoomActionGroup != null) {
        add(zoomActionGroup)
      }
      if (zoomLabelToolbar != null) {
        add(zoomLabelToolbar)
      }
    }
    otherToolbars.clear()
    actionGroups.otherGroups.associateWithTo(otherToolbars) { createToolbar("DesignSurfaceFloatingOtherActionsToolbar", actionManager, it, component) }

    floatingToolbar.removeAll()
    if (zoomActionGroup != null || otherToolbars.isNotEmpty() || zoomLabelToolbar != null) {
      // Empty space with weight to push components down.
      floatingToolbar.add(Box.createRigidArea(JBUI.size(10)), emptyBoxConstraints)
    }
    for ((index, toolbar) in otherToolbars.values.withIndex()) {
      val controlsPanel = toolbar.component.wrapInDesignSurfaceUI()
      val otherControlsConstraints = GridBagConstraints().apply {
        gridx = 1
        gridy = index + 1
        weightx = 1.0
        anchor = GridBagConstraints.FIRST_LINE_END
        insets = VERTICAL_PANEL_MARGINS
      }
      floatingToolbar.add(controlsPanel, otherControlsConstraints)
      controlsPanel.revalidate()
    }
    if (zoomLabelToolbar != null) {
      val zoomLabelPanel = zoomLabelToolbar.component.wrapInDesignSurfaceUI().apply {
        // Initialising the visibility to false will avoid to show the label when preview gets updated or created
        isVisible = false
      }
      floatingToolbar.add(zoomLabelPanel, zoomLabelConstraints)
      hiddenZoomLabelTimer?.start()
      hiddenZoomLabelComponent = zoomLabelPanel
      zoomLabelPanel.revalidate()
    }
    if (zoomActionGroup != null) {
      val zoomControlsPanel = zoomActionGroup.component.wrapInDesignSurfaceUI()
      floatingToolbar.add(zoomControlsPanel, zoomControlsConstraints)
      zoomControlsPanel.revalidate()
    }

    pauseZoomLabelTimerWhileInteractingOn(listOfNotNull(zoomLabelToolbar as? JPanel, zoomActionGroup as? JPanel))
  }

  override fun dispose() {
    floatingToolbar.removeAll()
    // Stop timer so that it can be garbage collected.
    hiddenZoomLabelTimer?.stop()
    // Set to null to guarantee Timer.start() will not be called again on it.
    hiddenZoomLabelTimer = null
  }

  override fun zoomChanged(previousScale: Double, newScale: Double) = UIUtil.invokeLaterIfNeeded {
    zoomToolbars.forEach { it.updateActionsImmediately() }
    hiddenZoomLabelComponent?.isVisible = true
    hiddenZoomLabelTimer?.restart()
  }

  override fun panningChanged(adjustmentEvent: AdjustmentEvent?) = UIUtil.invokeLaterIfNeeded {
    otherToolbars.values.forEach { it.updateActionsImmediately() }
  }

  abstract fun getActionGroups(): EditorActionsToolbarActionGroups

  /**
   * Sets mouse listeners to the given [JPanel]s. It will pause & restart the Zoom Label panel timer while the mouse is on these [panels].
   *
   * The effect is that the [hiddenZoomLabelComponent] will only go invisible after a period of time has happened without interacting with
   * these [panels].
   */
  private fun pauseZoomLabelTimerWhileInteractingOn(panels: List<JPanel>) {
    val mouseListener = object : MouseAdapter() {
      override fun mouseEntered(e: MouseEvent?) {
        super.mouseEntered(e)
        // Stop timer whenever a component becomes active (similar to mouse rollover).
        hiddenZoomLabelTimer?.stop()
      }

      override fun mouseExited(e: MouseEvent?) {
        // Resume whenever a relevant component is not being interacted.
        hiddenZoomLabelTimer?.start()
      }
    }
    val containerListener = object : ContainerAdapter() {
      override fun componentAdded(e: ContainerEvent?) {
        super.componentAdded(e)
        // Add the same listener to possible children in component, since they might consume the event.
        e?.child?.addMouseListener(mouseListener)
      }
    }
    panels.forEach {
      it.addMouseListener(mouseListener)
      it.addContainerListener(containerListener)
    }
  }
}

private fun JComponent.wrapInDesignSurfaceUI(): JPanel {
  return DesignSurfaceToolbarUI.createPanel(this).apply { layout = BoxLayout(this, BoxLayout.Y_AXIS) }
}

private fun createToolbar(
  place: String,
  actionManager: ActionManager, actionGroup: ActionGroup, target: JComponent): ActionToolbar {
  // Place must be "DesignSurface" to get the correct variation for zoom icons.
  val toolbar = actionManager.createActionToolbar(place, actionGroup, false).apply {
    layoutPolicy = ActionToolbar.WRAP_LAYOUT_POLICY
    setTargetComponent(target)
    setMinimumButtonSize(ActionToolbar.DEFAULT_MINIMUM_BUTTON_SIZE)
    component.apply {
      border = JBUI.Borders.empty(1)
      isOpaque = false
    }
  }
  ActionToolbarUtil.makeToolbarNavigable(toolbar)
  return toolbar
}
