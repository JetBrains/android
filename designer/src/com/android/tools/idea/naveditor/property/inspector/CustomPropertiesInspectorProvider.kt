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

import com.android.SdkConstants.ANDROID_URI
import com.android.tools.idea.common.model.NlComponent
import com.android.tools.idea.common.property.NlProperty
import com.android.tools.idea.common.property.inspector.InspectorComponent
import com.android.tools.idea.common.property.inspector.InspectorProvider
import com.android.tools.idea.naveditor.property.NavPropertiesManager
import com.android.tools.idea.projectsystem.GoogleMavenArtifactId
import com.android.tools.idea.uibuilder.property.NlPropertyItem

class CustomPropertiesInspectorProvider : InspectorProvider<NavPropertiesManager> {

  private val inspectors = mutableMapOf<String, InspectorComponent<NavPropertiesManager>>()

  override fun isApplicable(components: List<NlComponent>,
                            properties: Map<String, NlProperty>,
                            propertiesManager: NavPropertiesManager): Boolean {
    return properties.values.any { isCustomProperty(it) }
  }

  override fun createCustomInspector(components: List<NlComponent>,
                                     properties: Map<String, NlProperty>,
                                     propertiesManager: NavPropertiesManager): InspectorComponent<NavPropertiesManager> {
    val inspector = inspectors.getOrPut(components[0].tagName) {
      NavigationInspectorComponent(properties, propertiesManager, makePropertyNameUiNameMap(properties), null)
    }
    inspector.updateProperties(components, properties, propertiesManager)
    return inspector
  }

  private fun isCustomProperty(property: NlProperty) =
    property is NlPropertyItem &&
    property.definition?.libraryName?.startsWith(GoogleMavenArtifactId.NAVIGATION_FRAGMENT.mavenGroupId) != true &&
    property.namespace != ANDROID_URI

  private fun makePropertyNameUiNameMap(properties: Map<String, NlProperty>): Map<String, String> {
    return properties.filterValues { isCustomProperty(it) }.mapValues { it.key }
  }

  override fun resetCache() {
    inspectors.clear()
  }
}