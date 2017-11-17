/*
 * Copyright (C) 2017 The Android Open Source Project
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
package com.android.tools.idea.naveditor.property.inspector

import com.android.tools.idea.common.model.NlComponent
import com.android.tools.idea.common.property.NlProperty
import com.android.tools.idea.common.property.editors.NlComponentEditor
import com.android.tools.idea.common.property.inspector.InspectorComponent
import com.android.tools.idea.common.property.inspector.InspectorPanel
import com.android.tools.idea.common.property.inspector.InspectorProvider
import com.android.tools.idea.naveditor.model.destinationType
import com.android.tools.idea.naveditor.property.NavPropertiesManager
import com.android.tools.idea.naveditor.property.SET_START_DESTINATION_PROPERTY_NAME
import com.android.tools.idea.naveditor.property.SetStartDestinationProperty
import java.awt.BorderLayout
import javax.swing.JButton
import javax.swing.JPanel

class NavSetStartProvider : InspectorProvider<NavPropertiesManager> {
  override fun isApplicable(components: List<NlComponent>,
                            properties: Map<String, NlProperty>,
                            propertiesManager: NavPropertiesManager): Boolean {
    if (components.size != 1 || components[0].isRoot) {
      return false
    }
    return components[0].destinationType != null
  }

  override fun createCustomInspector(components: List<NlComponent>,
                                     properties: Map<String, NlProperty>,
                                     propertiesManager: NavPropertiesManager): InspectorComponent<NavPropertiesManager> {
    return SetStartButton.also { it.updateProperties(components, properties, propertiesManager) }
  }

  override fun resetCache() {
    // nothing
  }


  private object SetStartButton : InspectorComponent<NavPropertiesManager> {
    lateinit var myComponent: NlComponent
    val myButton = JButton("Set Start Destination")
    val myPanel = JPanel(BorderLayout())
    lateinit var myStartDestinationProperty: SetStartDestinationProperty

    init {
      myPanel.add(myButton, BorderLayout.CENTER)
      myButton.addActionListener {
        myStartDestinationProperty.setValue("true")
        updateEnabled()
      }
      myButton.name = SET_START_DESTINATION_PROPERTY_NAME
    }

    override fun updateProperties(components: List<NlComponent>,
                                  properties: Map<String, NlProperty>,
                                  propertiesManager: NavPropertiesManager) {
      myComponent = components[0]
      myStartDestinationProperty = properties[SET_START_DESTINATION_PROPERTY_NAME] as SetStartDestinationProperty
      updateEnabled()
    }

    private fun updateEnabled() {
      myButton.isEnabled = (myStartDestinationProperty.value == null)
    }

    override fun getMaxNumberOfRows() = 1

    override fun attachToInspector(inspector: InspectorPanel<NavPropertiesManager>) {
      inspector.addPanel(myPanel)
    }

    override fun refresh() {
      // Nothing
    }

    override fun getEditors(): List<NlComponentEditor> = listOf()
  }
}