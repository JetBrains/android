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
import com.android.tools.idea.common.property.NlProperty
import com.android.tools.idea.naveditor.model.actionDestinationId
import com.android.tools.idea.naveditor.model.findVisibleDestination
import com.android.tools.idea.uibuilder.property.NlPropertyItem
import com.intellij.openapi.command.WriteCommandAction
import com.intellij.util.xml.XmlName
import org.jetbrains.android.dom.attrs.AttributeDefinitions
import org.jetbrains.android.dom.navigation.NavigationSchema
import org.jetbrains.android.dom.navigation.NavigationSchema.ATTR_DEFAULT_VALUE
import org.jetbrains.android.dom.navigation.NavigationSchema.TAG_ARGUMENT

/**
 * Property representing all the arguments (possibly zero) for an action.
 */
class NavActionArgumentsProperty(components: List<NlComponent>, propertiesManager: NavPropertiesManager)
  : NavArgumentsProperty(components, propertiesManager) {

  init {
    refreshList()
  }

  override fun refreshList() {
    if (attrDefs == null) {
      return
    }
    properties.clear()

    val localArguments: Map<String, NlComponent> =
        components.flatMap { it.children }
            .filter { it.tagName == NavigationSchema.TAG_ARGUMENT }
            .associateBy { it.getAttribute(ANDROID_URI, ATTR_NAME) ?: "" }

    val destinationToLocal: Map<NlComponent, NlComponent?> =
        components.mapNotNull { component ->
          component.actionDestinationId?.let {
            component.findVisibleDestination(it)
          }
        }
            .flatMap { it.children }
            .filter { it.tagName == TAG_ARGUMENT && !it.getAttribute(ANDROID_URI, ATTR_NAME).isNullOrEmpty() }
            .associate { it to localArguments[it.getAttribute(ANDROID_URI, ATTR_NAME)] }

    destinationToLocal.mapTo(properties) { (dest, local) -> NavActionArgumentProperty(dest, local, components, attrDefs, this) }
  }
}

class NavActionArgumentProperty(destinationArgument: NlComponent,
                                private val actionArgument: NlComponent?,
                                private val parents: List<NlComponent>,
                                attrDefs: AttributeDefinitions,
                                private val navArgumentsProperty: NavActionArgumentsProperty) :
    NlPropertyItem(XmlName(ATTR_NAME, ANDROID_URI),
        attrDefs.getAttrDefByName(ATTR_NAME),
        listOf(destinationArgument),
        navArgumentsProperty.propertiesManager), NavArgumentProperty {

  override val defaultValueProperty: NlProperty =
      actionArgument?.let { ActionArgumentPropertyItem(attrDefs) } ?:
          object : NewElementProperty(parents[0], TAG_ARGUMENT, ATTR_DEFAULT_VALUE, ANDROID_URI, attrDefs,
              navArgumentsProperty.propertiesManager) {
            override fun setValue(value: Any?) {
              super.setValue(value)
              WriteCommandAction.runWriteCommandAction(null) {
                tag?.setAttribute(ATTR_NAME, ANDROID_URI, this@NavActionArgumentProperty.value)
              }
            }
          }

  private inner class ActionArgumentPropertyItem(attrDefs: AttributeDefinitions)
    : NlPropertyItem(XmlName(ATTR_DEFAULT_VALUE, ANDROID_URI), attrDefs.getAttrDefByName(ATTR_DEFAULT_VALUE),
      listOf(actionArgument), myPropertiesManager) {
    override fun setValue(value: Any?) {
      super.setValue(value)
      deleteIfNeeded()
    }

    private fun deleteIfNeeded() {
      if (value.isNullOrEmpty()) {
        navArgumentsProperty.model.delete(listOf(actionArgument))
        navArgumentsProperty.refreshList()
      }
    }
  }
}