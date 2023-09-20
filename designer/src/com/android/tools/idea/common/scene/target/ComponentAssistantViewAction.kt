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
import com.android.tools.idea.common.model.NlComponent
import com.android.tools.idea.uibuilder.api.ViewEditor
import com.android.tools.idea.uibuilder.api.ViewHandler
import com.android.tools.idea.uibuilder.api.actions.DirectViewAction
import com.android.tools.idea.uibuilder.api.actions.ViewActionPresentation
import com.android.tools.idea.uibuilder.assistant.ComponentAssistantFactory
import com.android.tools.idea.uibuilder.handlers.relative.targets.drawBottom
import com.intellij.openapi.ui.popup.Balloon
import icons.StudioIcons
import java.awt.Point
import java.awt.event.ActionEvent
import java.awt.event.KeyEvent
import java.lang.ref.WeakReference
import javax.swing.AbstractAction
import javax.swing.JComponent
import javax.swing.KeyStroke

private const val ACTION_HIDE = "actionHide"

/**
 * A [DirectViewAction] that displays a popup displaying a [JComponent] provided by
 * [ComponentAssistantFactory].
 */
// TODO(b/120382660): Replace icon with the final version
class ComponentAssistantViewAction
@JvmOverloads
constructor(
  assistantLabel: String = "Set Sample Data",
  private val panelFactoryFactory: (NlComponent) -> ComponentAssistantFactory?
) : DirectViewAction(StudioIcons.LayoutEditor.Properties.TOOLS_ATTRIBUTE, assistantLabel) {

  private var onClose: (cancelled: Boolean) -> Unit = {}

  /** Add an action to the action map so that the popup is hidden when the user presses escape. */
  private fun setupActionInputMap(component: JComponent, onCancel: () -> Unit) {
    component.actionMap.put(
      ACTION_HIDE,
      object : AbstractAction() {
        override fun actionPerformed(e: ActionEvent?) {
          fireCancelEvent()
          onCancel()
        }
      }
    )
    component
      .getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW)
      .put(KeyStroke.getKeyStroke(KeyEvent.VK_ESCAPE, 0), ACTION_HIDE)
  }

  private fun fireCloseEvent() = onClose(false)

  private fun fireCancelEvent() = onClose(true)

  override fun perform(
    editor: ViewEditor,
    handler: ViewHandler,
    parent: NlComponent,
    selectedChildren: MutableList<NlComponent>,
    modifiers: Int
  ) {
    if (selectedChildren.size != 1) {
      // The ComponentAssistant can only be invoked on 1 specific component. If there are multiple
      // selected, we can not execute this actions (and should not be displayed).
      return
    }

    val selectedComponent = selectedChildren[0]
    val panelFactory = panelFactoryFactory(selectedComponent) ?: return
    val sceneComponent = editor.scene.getSceneComponent(selectedComponent) ?: return

    val scene = sceneComponent.scene
    val designSurface = scene.designSurface
    val context = scene.sceneManager.sceneView.context
    val popup =
      LightCalloutPopup(
        closedCallback = this::fireCloseEvent,
        cancelCallBack = this::fireCancelEvent
      )
    val popupRef = WeakReference(popup)
    val assistantContext =
      ComponentAssistantFactory.Context(selectedComponent) { cancel ->
        if (cancel) popupRef.get()?.cancel() else popupRef.get()?.close()
      }
    val component =
      panelFactory.createComponent(assistantContext).apply {
        name = "Component Assistant" // For UI tests
      }

    setupActionInputMap(component) { popupRef.get()?.cancel() }

    onClose = { cancelled ->
      onClose = {} // One-off trigger. Disable the callback
      assistantContext.onClose(cancelled)
    }

    val position =
      Point(
        context.getSwingXDip(sceneComponent.centerX.toFloat()),
        context.getSwingYDip(sceneComponent.drawBottom.toFloat())
      )
    val parentComponent = designSurface.layeredPane
    if (canShowBelow(parentComponent, position, component)) {
      popup.show(component, parentComponent, position)
    } else {
      val location =
        Point(
          context.getSwingXDip(sceneComponent.centerX.toFloat()),
          context.getSwingYDip(sceneComponent.drawBottom.toFloat())
        )
      popup.show(component, parentComponent, location, position = Balloon.Position.above)
    }
  }

  override fun updatePresentation(
    presentation: ViewActionPresentation,
    editor: ViewEditor,
    handler: ViewHandler,
    component: NlComponent,
    selectedChildren: MutableList<NlComponent>,
    modifiersEx: Int
  ) {
    super.updatePresentation(
      presentation,
      editor,
      handler,
      component,
      selectedChildren,
      modifiersEx
    )

    val visible = (selectedChildren.size == 1).and(panelFactoryFactory(selectedChildren[0]) != null)
    presentation.setVisible(visible)
  }
}
