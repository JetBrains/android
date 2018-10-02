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

import com.android.SdkConstants.*
import com.android.tools.idea.common.model.NlComponent
import com.android.tools.idea.common.property.NlProperty
import com.android.tools.idea.common.property.inspector.InspectorPanel
import com.android.tools.idea.naveditor.model.destinationType
import com.android.tools.idea.naveditor.model.isInclude
import com.android.tools.idea.naveditor.model.isNavigation
import com.android.tools.idea.naveditor.property.*
import com.intellij.openapi.Disposable
import org.jetbrains.android.dom.AndroidDomElement
import org.jetbrains.android.dom.navigation.DeeplinkElement
import org.jetbrains.android.dom.navigation.NavActionElement
import org.jetbrains.android.dom.navigation.NavArgumentElement
import org.jetbrains.android.dom.navigation.NavigationSchema

/**
 * Panel shown in the nav editor properties inspector. Notably includes actions, deeplinks, and arguments.
 */
class NavInspectorPanel(parentDisposable: Disposable) : InspectorPanel<NavPropertiesManager>(parentDisposable, null) {

  override fun collectExtraProperties(components: List<NlComponent>,
                                      propertiesManager: NavPropertiesManager,
                                      propertiesByName: MutableMap<String, NlProperty>) {
    if (components.isEmpty()) {
      return
    }

    propertiesByName[TYPE_EDITOR_PROPERTY_LABEL] = NavComponentTypeProperty(components)
    val schema = NavigationSchema.get(components[0].model.module)

    addProperties(components, schema, propertiesByName, NavActionElement::class.java, ::NavActionsProperty)
    addProperties(components, schema, propertiesByName, DeeplinkElement::class.java, ::NavDeeplinkProperty)
    addProperties(components, schema, propertiesByName, NavArgumentElement::class.java) { c ->
      if (c.all { it.destinationType != null && !it.isNavigation}) {
        NavDestinationArgumentsProperty(c)
      }
      else {
        NavArgumentDefaultValuesProperty(c, propertiesManager)
      }
    }
    components.filter { it.isInclude }.forEach {
      propertiesByName[ATTR_ID] = SimpleProperty(ATTR_ID, listOf(it), ANDROID_URI, it.id)
      propertiesByName[ATTR_LABEL] = SimpleProperty(ATTR_LABEL, listOf(it), ANDROID_URI, it.resolveAttribute(ANDROID_URI, ATTR_LABEL))
    }
  }

  private fun addProperties(components: List<NlComponent>, schema: NavigationSchema, propertiesByName: MutableMap<String, NlProperty>,
                            elementClass: Class<out AndroidDomElement>, propertyConstructor: (List<NlComponent>) -> NlProperty) {
    val relevantComponents =
        components.filter { schema.getDestinationSubtags(it.tagName).containsKey(elementClass) }
    if (!relevantComponents.isEmpty()) {
      val property = propertyConstructor(relevantComponents)
      propertiesByName[property.name] = property
    }
  }
}
