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
import com.android.tools.idea.common.scene.SceneContext
import com.android.tools.idea.uibuilder.graphics.NlIcon
import com.android.tools.idea.uibuilder.property.assistant.ComponentAssistant
import icons.StudioIcons
import java.awt.Point
import javax.swing.JComponent

/**
 * An [ActionTarget] that displays a popup displaying a [JComponent]
 * provided by [ComponentAssistant.PanelFactory].
 */
class ComponentAssistantActionTarget(
  private val panelFactory: ComponentAssistant.PanelFactory
) : ActionTarget(null, NlIcon(StudioIcons.Menu.MENU, StudioIcons.Menu.MENU), null) {

  private var onClose: (cancelled: Boolean) -> Unit = {}
  private var popup: LightCalloutPopup = LightCalloutPopup(closedCallback = this::fireCloseEvent, cancelCallBack = this::fireCancelEvent)

  override fun mouseRelease(x: Int, y: Int, closestTargets: MutableList<Target>) {
    val designSurface = component.scene.designSurface
    val context = SceneContext.get(designSurface.currentSceneView)
    val position = Point(context.getSwingXDip(centerX), context.getSwingYDip(myTop))
    val assistantContext = ComponentAssistant.Context(component.nlComponent, popup::close)
    val component = panelFactory.createComponent(assistantContext).apply {
      name = "Component Assistant" // For UI tests
    }

    onClose = { cancelled ->
      onClose = {} // One-off trigger. Disable the callback
      assistantContext.onClose(cancelled)
    }
    popup.show(component, designSurface, position)
  }

  private fun fireCloseEvent() {
    onClose(false)
  }

  private fun fireCancelEvent() {
    onClose(true)
  }
}