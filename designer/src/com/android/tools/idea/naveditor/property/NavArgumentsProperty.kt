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
package com.android.tools.idea.naveditor.property

import com.android.SdkConstants.ATTR_NAME
import com.android.tools.idea.common.model.NlComponent
import com.android.tools.idea.common.property.NlProperty
import com.android.tools.idea.naveditor.property.inspector.SimpleProperty
import com.android.tools.idea.uibuilder.property.NlPropertyItem
import com.intellij.util.xml.XmlName
import org.jetbrains.android.dom.attrs.AttributeDefinitions
import org.jetbrains.android.dom.navigation.NavigationSchema
import org.jetbrains.android.dom.navigation.NavigationSchema.ATTR_DEFAULT_VALUE
import org.jetbrains.android.resourceManagers.LocalResourceManager
import org.jetbrains.android.resourceManagers.ModuleResourceManagers

/**
 * Property representing all the arguments (possibly zero) for a destinations.
 */
class NavArgumentsProperty(components: List<NlComponent>, val propertiesManager: NavPropertiesManager) : SimpleProperty("Arguments", components) {

  private val resourceManager: LocalResourceManager? = if (myComponents.isEmpty()) {
    null
  } else {
    ModuleResourceManagers.getInstance(myComponents[0].model.facet).localResourceManager
  }

  private val attrDefs: AttributeDefinitions? = resourceManager?.attributeDefinitions

  val properties = mutableListOf<NavArgumentProperty>()

  init {
    refreshList()
  }

  fun refreshList() {
    if (attrDefs == null) {
      return
    }
    properties.clear()

    components.flatMap { it.children }
        .filter { it.tagName == NavigationSchema.TAG_ARGUMENT }
        .mapTo(properties) { NavArgumentPropertyImpl(listOf(it), attrDefs, this) }
    properties.add(NewNavElementProperty(components[0], attrDefs, propertiesManager))
  }
}

private class NewNavElementProperty(parent: NlComponent, attrDefs: AttributeDefinitions, propertiesManager: NavPropertiesManager)
  : NewElementProperty(parent, NavigationSchema.TAG_ARGUMENT, ATTR_NAME, null, attrDefs, propertiesManager), NavArgumentProperty {

  override val defaultValueProperty = NewElementProperty(parent, NavigationSchema.TAG_ARGUMENT, ATTR_DEFAULT_VALUE, null, attrDefs, propertiesManager)
}

interface NavArgumentProperty : NlProperty {
  val defaultValueProperty: NlProperty
}

private class NavArgumentPropertyImpl(components: List<NlComponent>,
                          attrDefs: AttributeDefinitions,
                          private val navArgumentsProperty: NavArgumentsProperty) :
    NlPropertyItem(XmlName(ATTR_NAME), attrDefs.getAttrDefByName(ATTR_NAME), components, navArgumentsProperty.propertiesManager),
    NavArgumentProperty {

  override val defaultValueProperty: NlPropertyItem = object : NlPropertyItem(XmlName(ATTR_DEFAULT_VALUE),
      attrDefs.getAttrDefByName(ATTR_DEFAULT_VALUE), components, navArgumentsProperty.propertiesManager) {
    override fun setValue(value: Any?) {
      super.setValue(value)
      deleteIfNeeded()
    }
  }

  val isEmpty: Boolean
    get() = value.isNullOrEmpty() && defaultValueProperty.value.isNullOrEmpty()

  override fun setValue(value: Any?) {
    super.setValue(value)
    deleteIfNeeded()
  }

  private fun deleteIfNeeded() {
    if (isEmpty) {
      navArgumentsProperty.model.delete(components)
      navArgumentsProperty.refreshList()
    }
  }
}

