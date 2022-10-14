/*
 * Copyright (C) 2022 The Android Open Source Project
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

import com.android.tools.adtui.common.secondaryPanelBackground
import com.android.tools.idea.common.model.NlComponent
import com.android.tools.idea.uibuilder.property.ui.InputTypeEditor
import com.android.tools.property.panel.api.ActionIconButton
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.PlatformCoreDataKeys
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.ui.popup.Balloon
import com.intellij.openapi.ui.popup.JBPopupFactory
import com.intellij.ui.BalloonImpl
import com.intellij.ui.awt.RelativePoint
import icons.StudioIcons
import org.jetbrains.android.dom.attrs.AttributeDefinition
import java.awt.Point

/**
 * An InputType property item.
 *
 * This property item contains the flag methods to check and control the mask values of each flag without showing each flag
 * as a child of the property.
 */
class InputTypePropertyItem(
  namespace: String,
  name: String,
  type: NlPropertyType,
  attrDefinition: AttributeDefinition,
  componentName: String,
  libraryName: String,
  model: NlPropertiesModel,
  components: List<NlComponent>,
  optionalValue1: Any? = null,
  optionalValue2: Any? = null
) : NlFlagsPropertyItem(namespace, name, type, attrDefinition, componentName, libraryName, model, components, optionalValue1,
                        optionalValue2) {

  override val colorButton: ActionIconButton = object : ActionIconButton {
    override val actionButtonFocusable = true
    override val actionIcon = StudioIcons.LayoutEditor.Properties.FLAG
    override val action = object : AnAction() {
      override fun actionPerformed(event: AnActionEvent) {
        val panel = InputTypeEditor(this@InputTypePropertyItem)
        val balloon = JBPopupFactory.getInstance()
          .createBalloonBuilder(panel)
          .setShadow(true)
          .setHideOnAction(false)
          .setBlockClicksThroughBalloon(true)
          .setAnimationCycle(200)
          .setFillColor(secondaryPanelBackground)
          .createBalloon() as BalloonImpl

        panel.balloon = balloon
        val component = event.getData(PlatformCoreDataKeys.CONTEXT_COMPONENT)
        if (component != null) {
          balloon.show(RelativePoint(component, Point(8, 8)), Balloon.Position.below)
          ApplicationManager.getApplication().invokeLater { panel.requestFocus() }
        }
      }
    }
  }
}
