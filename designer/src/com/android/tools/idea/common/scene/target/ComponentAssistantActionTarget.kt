/*
 * Copyright (C) 2018 The Android Open Source Project
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
package com.android.tools.idea.common.scene.target

import com.android.tools.adtui.LightCalloutPopup
import com.android.tools.adtui.canShowBelow
import com.android.tools.idea.common.scene.SceneContext
import com.android.tools.idea.uibuilder.graphics.NlIcon
import com.android.tools.idea.uibuilder.property.assistant.ComponentAssistantFactory
import com.intellij.openapi.ui.popup.Balloon
import com.intellij.openapi.wm.IdeFocusManager
import icons.StudioIcons
import java.awt.Point
import java.awt.event.ActionEvent
import java.awt.event.KeyEvent
import javax.swing.AbstractAction
import javax.swing.JComponent
import javax.swing.KeyStroke

private const val ACTION_HIDE = "actionHide"

/**
 * An [ActionTarget] that displays a popup displaying a [JComponent]
 * provided by [ComponentAssistantFactory].
 */
// TODO: Replace icon with the final version
class ComponentAssistantActionTarget(
  private val panelFactory: ComponentAssistantFactory
) : ActionTarget(NlIcon(StudioIcons.Shell.ToolWindows.BUILD, StudioIcons.Shell.ToolWindows.BUILD), null) {

  private var onClose: (cancelled: Boolean) -> Unit = {}
  private var popup: LightCalloutPopup = LightCalloutPopup(closedCallback = this::fireCloseEvent, cancelCallBack = this::fireCancelEvent)

  override fun mouseRelease(x: Int, y: Int, closestTargets: MutableList<Target>) {
    val designSurface = component.scene.designSurface
    val context = SceneContext.get(designSurface.currentSceneView)
    val position = Point(context.getSwingXDip(centerX), context.getSwingYDip(myBottom))
    val assistantContext = ComponentAssistantFactory.Context(
      component.nlComponent,
      { cancel ->
        if (cancel) popup.cancel() else popup.close()
      })
    val component = panelFactory.createComponent(assistantContext).apply {
      name = "Component Assistant" // For UI tests
    }

    setupActionInputMap(component)

    onClose = { cancelled ->
      onClose = {} // One-off trigger. Disable the callback
      assistantContext.onClose(cancelled)
    }

    val parentComponent = designSurface.layeredPane
    if (canShowBelow(parentComponent, position, component)) {
      popup.show(component, parentComponent, position)
    }
    else {
      val location = Point(context.getSwingXDip(centerX), context.getSwingYDip(myTop))
      popup.show(component, parentComponent, location, position = Balloon.Position.above)
    }

    IdeFocusManager.getGlobalInstance().requestFocus(component, true)
  }

  /**
   * Add an action to the action map so that the popup is hidden when the user presses escape.
   */
  private fun setupActionInputMap(component: JComponent) {
    component.actionMap.put(ACTION_HIDE, object : AbstractAction() {
      override fun actionPerformed(e: ActionEvent?) {
        fireCancelEvent()
        popup.cancel()
      }
    })
    component.getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW).put(KeyStroke.getKeyStroke(KeyEvent.VK_ESCAPE, 0), ACTION_HIDE)
  }

  private fun fireCloseEvent() {
    onClose(false)
  }

  private fun fireCancelEvent() {
    onClose(true)
  }
}