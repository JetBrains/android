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
package com.android.tools.idea.naveditor.property.inspector

import com.android.SdkConstants.ATTR_LAYOUT
import com.android.tools.idea.common.model.NlComponent
import com.android.tools.idea.common.property.NlProperty
import com.android.tools.idea.common.property.inspector.InspectorComponent
import com.android.tools.idea.common.property.inspector.InspectorProvider
import com.android.tools.idea.naveditor.model.destinationType
import com.android.tools.idea.naveditor.property.NavPropertiesManager
import com.android.tools.idea.naveditor.property.isCustomProperty
import org.jetbrains.android.dom.navigation.NavigationSchema

class CustomPropertiesInspectorProvider : InspectorProvider<NavPropertiesManager> {

  private val inspectors = mutableMapOf<String, InspectorComponent<NavPropertiesManager>>()

  override fun isApplicable(components: List<NlComponent>,
                            properties: Map<String, NlProperty>,
                            propertiesManager: NavPropertiesManager): Boolean {
    return properties.values.any { it.isCustomProperty }
  }

  override fun createCustomInspector(components: List<NlComponent>,
                                     properties: Map<String, NlProperty>,
                                     propertiesManager: NavPropertiesManager): InspectorComponent<NavPropertiesManager> {
    val inspector = inspectors.getOrPut(components[0].tagName) {
      var propertyMap = properties.filterValues { it.isCustomProperty }.mapValues { it.key }
      val layoutProperty = properties[ATTR_LAYOUT]
      if (components.all { it.destinationType == NavigationSchema.DestinationType.OTHER } && layoutProperty != null) {
        propertyMap = propertyMap.plus(ATTR_LAYOUT to ATTR_LAYOUT)
      }
      NavigationInspectorComponent(properties, propertiesManager, propertyMap, null)
    }
    inspector.updateProperties(components, properties, propertiesManager)
    return inspector
  }

  override fun resetCache() {
    inspectors.clear()
  }
}