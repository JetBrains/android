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

import com.android.SdkConstants.ANDROID_URI
import com.android.SdkConstants.ATTR_NAME
import com.android.tools.idea.common.model.NlComponent
import com.android.tools.idea.uibuilder.property.NlPropertyItem
import com.intellij.util.xml.XmlName
import org.jetbrains.android.dom.attrs.AttributeDefinitions
import org.jetbrains.android.dom.navigation.NavigationSchema
import org.jetbrains.android.dom.navigation.NavigationSchema.ATTR_DEFAULT_VALUE

/**
 * Property representing all the arguments (possibly zero) for a destination.
 */
class NavDestinationArgumentsProperty(components: List<NlComponent>, propertiesManager: NavPropertiesManager) :
    NavArgumentsProperty(components, propertiesManager) {

  internal var newArgumentCount: Int = 0

  init {
    refreshList()
  }

  override fun refreshList() {
    if (attrDefs == null) {
      return
    }
    properties.clear()

    components.flatMap { it.children }
      .filter { it.tagName == NavigationSchema.TAG_ARGUMENT }
      .mapTo(properties) { NavDestinationArgumentPropertyImpl(listOf(it), attrDefs, this) }
    repeat(newArgumentCount) {
      properties.add(NewNavElementProperty(components[0], attrDefs, propertiesManager, this))
    }
  }

  fun addRow() {
    newArgumentCount++
    refreshList()
  }

  fun deleteRows(indexes: IntArray) {
    for (index in indexes.reversed()) {
      val navArgumentProperty = properties[index]
      if (navArgumentProperty is NavDestinationArgumentPropertyImpl) {
        navArgumentProperty.deleteArgument()
      } else {
        properties.removeAt(index)
        newArgumentCount--
      }
    }
    refreshList()
  }
}

private class NewNavElementProperty(
  parent: NlComponent,
  attrDefs: AttributeDefinitions,
  propertiesManager: NavPropertiesManager,
  val navDestinationArgumentsProperty: NavDestinationArgumentsProperty
)
  : NewElementProperty(parent, NavigationSchema.TAG_ARGUMENT, ATTR_NAME, ANDROID_URI, attrDefs, propertiesManager), NavArgumentProperty {
  override fun setValue(value: Any?) {
    super.setValue(value)
    if ((value as? String)?.isEmpty() == false) {
      navDestinationArgumentsProperty.newArgumentCount--
    }
  }

  override val defaultValueProperty =
      object: NewElementProperty(parent, NavigationSchema.TAG_ARGUMENT, ATTR_DEFAULT_VALUE, ANDROID_URI, attrDefs, propertiesManager) {
        override fun setValue(value: Any?) {
          super.setValue(value)
          if ((value as? String)?.isEmpty() == false) {
            navDestinationArgumentsProperty.newArgumentCount--
          }
        }
      }
}

private class NavDestinationArgumentPropertyImpl(components: List<NlComponent>,
                                                 attrDefs: AttributeDefinitions,
                                                 private val navArgumentsProperty: NavDestinationArgumentsProperty) :
    NlPropertyItem(XmlName(ATTR_NAME, ANDROID_URI), attrDefs.getAttrDefByName(ATTR_NAME), components, navArgumentsProperty.propertiesManager),
    NavArgumentProperty {

  override val defaultValueProperty: NlPropertyItem = object : NlPropertyItem(XmlName(ATTR_DEFAULT_VALUE, ANDROID_URI),
      attrDefs.getAttrDefByName(ATTR_DEFAULT_VALUE), components, navArgumentsProperty.propertiesManager) {
    override fun setValue(value: Any?) {
      super.setValue(value)
      deleteIfNeeded()
    }
  }

  override fun setValue(value: Any?) {
    super.setValue(value)
    deleteIfNeeded()
  }

  private fun deleteIfNeeded() {
    if (value.isNullOrEmpty() && defaultValueProperty.value.isNullOrEmpty()) {
      deleteArgument()
    }
  }

  fun deleteArgument() {
    navArgumentsProperty.model.delete(components)
    navArgumentsProperty.refreshList()
  }
}

