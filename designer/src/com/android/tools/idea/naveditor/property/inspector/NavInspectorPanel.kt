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
import com.android.tools.idea.common.property.inspector.InspectorPanel
import com.android.tools.idea.naveditor.property.NavActionsProperty
import com.android.tools.idea.naveditor.property.NavComponentTypeProperty
import com.android.tools.idea.naveditor.property.NavPropertiesManager
import com.android.tools.idea.naveditor.property.TYPE_EDITOR_PROPERTY_LABEL
import com.intellij.openapi.Disposable
import org.jetbrains.android.dom.navigation.NavActionElement
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

    propertiesByName.put(TYPE_EDITOR_PROPERTY_LABEL, NavComponentTypeProperty(components))

    val schema = NavigationSchema.getOrCreateSchema(components[0].model.facet)

    val actionContainingComponents =
        components.filter { schema.getDestinationSubtags(it.tagName).containsKey(NavActionElement::class.java) }
    if (!actionContainingComponents.isEmpty()) {
      val actionsProperty = NavActionsProperty(actionContainingComponents)
      propertiesByName.put(actionsProperty.name, actionsProperty)
    }

    // TODO: deeplinks and arguments
  }
}
