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
package com.android.tools.idea.common.editor

import com.android.tools.adtui.ui.DesignSurfaceToolbarUI
import com.android.tools.idea.common.model.NlModel
import com.android.tools.idea.common.surface.DesignSurface
import com.android.tools.idea.common.surface.DesignSurfaceListener
import com.android.tools.idea.common.surface.PanZoomListener
import com.android.tools.idea.flags.StudioFlags
import com.android.tools.idea.uibuilder.editor.BasicDesignSurfaceActionGroups
import com.android.tools.idea.uibuilder.editor.EditableDesignSurfaceActionGroups
import com.intellij.openapi.Disposable
import com.intellij.openapi.actionSystem.ActionGroup
import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.openapi.actionSystem.ActionToolbar
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.util.Disposer
import com.intellij.util.ui.JBDimension
import com.intellij.util.ui.JBUI
import java.awt.GridBagConstraints
import java.awt.GridBagLayout
import java.awt.event.ActionListener
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

private val VERTICAL_PANEL_MARGINS get() = JBUI.insets(0, 4, 4, 0)

/** Creates the actions toolbar used on the [DesignSurface] */
class DesignSurfaceActionsToolbar(
  private val designSurface: DesignSurface,
  private val component: JComponent,
  parentDisposable: Disposable
) : DesignSurfaceListener, PanZoomListener, Disposable {

  val designSurfaceToolbar: JComponent = JPanel(GridBagLayout()).apply { isOpaque = false }

  private val zoomToolbars: MutableList<ActionToolbar> = mutableListOf()
  private val panToolbars: MutableList<ActionToolbar> = mutableListOf()

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
  private val panControlsConstraints
    get() = GridBagConstraints().apply {
      gridx = 0
      gridy = 1
      gridwidth = 2
      anchor = GridBagConstraints.FIRST_LINE_END
      insets = VERTICAL_PANEL_MARGINS
    }
  private val zoomControlsConstraints
    get() = GridBagConstraints().apply {
      gridx = 1
      gridy = 2
      anchor = GridBagConstraints.FIRST_LINE_END
      insets = VERTICAL_PANEL_MARGINS
    }
  private val zoomLabelConstraints
    get() = GridBagConstraints().apply {
      gridx = 0
      gridy = 2
      weightx = 1.0
      anchor = GridBagConstraints.FIRST_LINE_END
      insets = VERTICAL_PANEL_MARGINS
    }

  /**
   * The Zoom Label toolbar panel. It should only be visible for a short time after changing zoom level and stay visible while interacting
   * with zoom controls.
   */
  private var hiddenZoomLabelComponent: JComponent? = null
  /** Timer used to automatically set the Zoom Label panel to not visible after a period of time. */
  private var hiddenZoomLabelTimer: Timer? =
    if (!ApplicationManager.getApplication().isUnitTestMode) { // Running a timer in a TestCase may cause it to fail.
      Timer(2000, ActionListener { hiddenZoomLabelComponent?.isVisible = false }).apply {
        isRepeats = false
      }
    }
    else {
      null
    }

  init {
    Disposer.register(parentDisposable, this)
    designSurface.addListener(this)
    designSurface.addPanZoomListener(this)
    updateToolbar()
  }

  private fun updateToolbar() {
    val actionGroups = getActionGroups()
    val actionManager = ActionManager.getInstance()
    val zoomControlsToolbar = createToolbar(actionManager, actionGroups.zoomControlsGroup, component)
    val zoomLabelToolbar = createToolbar(actionManager, actionGroups.zoomLabelGroup, component).apply {
      component.border = JBUI.Borders.empty(2)
    }
    val panControlsToolbar = createToolbar(actionManager, actionGroups.panControlsGroup, component)
    zoomToolbars.apply {
      clear()
      add(zoomControlsToolbar)
      add(zoomLabelToolbar)
    }
    panToolbars.apply {
      clear()
      add(panControlsToolbar)
    }

    val zoomControlsPanel = zoomControlsToolbar.component.wrapInDesignSurfaceUI()
    val zoomLabelPanel = zoomLabelToolbar.component.wrapInDesignSurfaceUI()
    val panControlsPanel = panControlsToolbar.component.wrapInDesignSurfaceUI()
    designSurfaceToolbar.apply {
      removeAll()
      // Empty space with weight to push components down.
      add(Box.createRigidArea(JBUI.size(10)), emptyBoxConstraints)
      add(panControlsPanel, panControlsConstraints)
      add(zoomLabelPanel, zoomLabelConstraints)
      add(zoomControlsPanel, zoomControlsConstraints)
    }

    hiddenZoomLabelTimer?.start()
    hiddenZoomLabelComponent = zoomLabelPanel
    pauseZoomLabelTimerWhileInteractingOn(arrayOf(zoomLabelToolbar as JPanel, zoomControlsToolbar as JPanel))
  }

  override fun dispose() {
    designSurfaceToolbar.removeAll()
    // Stop timer so that it can be garbage collected.
    hiddenZoomLabelTimer?.stop()
    // Set to null to guarantee Timer.start() will not be called again on it.
    hiddenZoomLabelTimer = null
    designSurface.removeListener(this)
    designSurface.removePanZoomListener(this)
  }

  override fun modelChanged(surface: DesignSurface, model: NlModel?) {
    updateToolbar()
  }

  override fun zoomChanged(designSurface: DesignSurface?) {
    zoomToolbars.forEach { it.updateActionsImmediately() }
    hiddenZoomLabelComponent?.isVisible = true
    hiddenZoomLabelTimer?.restart()
  }

  override fun panningChanged(adjustmentEvent: AdjustmentEvent?) {
    panToolbars.forEach { it.updateActionsImmediately() }
  }

  private fun getActionGroups(): DesignSurfaceActionGroups {
    return if (StudioFlags.NELE_DESIGN_SURFACE_ZOOM.get()) {
      if (designSurface.layoutType.isEditable()) {
        // Only editable file types support panning.
        EditableDesignSurfaceActionGroups(component, parentDisposable = this)
      }
      else {
        BasicDesignSurfaceActionGroups(component, parentDisposable = this)
      }
    }
    else {
      DesignSurfaceEmptyActionGroups
    }
  }

  /**
   * Sets mouse listeners to the given [JPanel]s. It will pause & restart the Zoom Label panel timer while the mouse is on these [panels].
   *
   * The effect is that the [hiddenZoomLabelComponent] will only go invisible after a period of time has happened without interacting with
   * these [panels].
   */
  private fun pauseZoomLabelTimerWhileInteractingOn(panels: Array<JPanel>) {
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

private fun createToolbar(actionManager: ActionManager, actionGroup: ActionGroup, target: JComponent): ActionToolbar =
  // Place must be "DesignSurface" to get the correct variation for zoom icons.
  actionManager.createActionToolbar("DesignSurface", actionGroup, false).apply {
    layoutPolicy = ActionToolbar.WRAP_LAYOUT_POLICY
    setTargetComponent(target)
    setMinimumButtonSize(JBDimension(22, 22))
    component.apply {
      border = JBUI.Borders.empty(1)
      isOpaque = false
    }
  }