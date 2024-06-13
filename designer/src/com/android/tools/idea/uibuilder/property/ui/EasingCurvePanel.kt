/*
 * Copyright (C) 2020 The Android Open Source Project
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
package com.android.tools.idea.uibuilder.property.ui

import com.android.SdkConstants
import com.android.tools.adtui.common.secondaryPanelBackground
import com.android.tools.idea.common.command.NlWriteCommandActionUtil
import com.android.tools.idea.uibuilder.property.NlPropertiesModel
import com.android.tools.idea.uibuilder.property.NlPropertyItem
import com.android.tools.property.panel.api.PropertiesModel
import com.android.tools.property.panel.api.PropertiesModelListener
import com.android.tools.property.panel.api.PropertiesTable
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.TransactionGuard
import com.intellij.openapi.application.runReadAction
import com.intellij.util.ui.JBUI
import java.awt.BorderLayout
import javax.swing.JPanel

/** Custom panel to support direct editing of transition easing curves */
class EasingCurvePanel(
  private val model: NlPropertiesModel,
  easingAttributeName: String,
  properties: PropertiesTable<NlPropertyItem>,
) : JPanel(BorderLayout()) {

  private val PANEL_WIDTH = 200
  private val PANEL_HEIGHT = PANEL_WIDTH

  private val transitionEasing = properties.getOrNull(SdkConstants.AUTO_URI, easingAttributeName)

  private val easingCurvePanel = EasingCurve()
  private var processingChange: Boolean = false
  private var processingModelUpdate: Boolean = false

  private val modelListener =
    object : PropertiesModelListener<NlPropertyItem> {
      override fun propertyValuesChanged(model: PropertiesModel<NlPropertyItem>) {
        updateFromValues()
      }
    }

  init {
    val panelSize = JBUI.size(PANEL_WIDTH, PANEL_HEIGHT)
    preferredSize = panelSize

    easingCurvePanel.background = secondaryPanelBackground
    add(easingCurvePanel)

    easingCurvePanel.addActionListener {
      if (!processingModelUpdate) {
        processingChange = true
        if (transitionEasing != null) {
          var component = transitionEasing.componentName
          TransactionGuard.submitTransaction(
            transitionEasing.model,
            Runnable {
              NlWriteCommandActionUtil.run(
                transitionEasing.components,
                "Set $component.${transitionEasing.name} to ${it.actionCommand}",
              ) {
                var cubic = it.actionCommand
                if (transitionEasing.name == "transitionEasing") {
                  when (cubic) {
                    "cubic(0.2,0.2,0.8,0.8)" -> cubic = "linear"
                    "cubic(0.4,0,0.2,1)" -> cubic = "standard"
                    "cubic(0.4,0,1,1)" -> cubic = "accelerate"
                    "cubic(0,0,0.2,1)" -> cubic = "decelerate"
                  }
                } else {
                  when (cubic) {
                    "cubic(0.2,0.2,0.8,0.8)" -> cubic = "linear"
                    "cubic(0.4,0,0.2,1)" -> cubic = "easeInOut"
                    "cubic(0.4,0,1,1)" -> cubic = "easeIn"
                    "cubic(0,0,0.2,1)" -> cubic = "easeOut"
                  }
                }
                transitionEasing.value = cubic
              }
            },
          )
        }
        processingChange = false
      }
    }

    updateFromValues()
  }

  override fun addNotify() {
    super.addNotify()
    model.addListener(modelListener)
  }

  override fun removeNotify() {
    super.removeNotify()
    model.removeListener(modelListener)
  }

  private fun updateFromValues() {
    if (processingChange) {
      return
    }
    val application = ApplicationManager.getApplication()
    if (application.isReadAccessAllowed) {
      updateFromProperty()
    } else {
      runReadAction { updateFromProperty() }
    }
  }

  private fun updateFromProperty() {
    transitionEasing?.value?.let {
      var cubic: String? = it
      when (cubic) {
        // constraint ones
        "standard" -> cubic = "cubic(0.4,0,0.2,1)"
        "accelerate" -> cubic = "cubic(0.4,0,1,1)"
        "decelerate" -> cubic = "cubic(0,0,0.2,1)"
        "linear" -> cubic = "cubic(0.2,0.2,0.8,0.8)"
        // transitions ones
        "easeInOut" -> cubic = "cubic(0.4,0,0.2,1)"
        "easeIn" -> cubic = "cubic(0.4,0,1,1)"
        "easeOut" -> cubic = "cubic(0,0,0.2,1)"
        "bounce" -> cubic = "cubic(0.2,0.2,0.8,0.8)"
      }
      processingModelUpdate = true
      easingCurvePanel.controlPoints = cubic
      processingModelUpdate = false
    }
  }
}
