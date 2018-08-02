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

import com.android.SdkConstants.*
import com.android.ide.common.rendering.api.ResourceNamespace
import com.android.ide.common.rendering.api.ResourceReference
import com.android.tools.idea.common.model.NlComponent
import com.android.tools.idea.common.property.NlProperty
import com.android.tools.idea.naveditor.model.*
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
class NavArgumentDefaultValuesProperty(components: List<NlComponent>, propertiesManager: NavPropertiesManager)
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
        if (component.isAction) {
          component.actionDestination?.let {
            if (it.isNavigation) it.startDestination else it
          }
        }
        else {
          component.startDestination
        }
      }
        .flatMap { it.children }
        .filter { it.tagName == TAG_ARGUMENT && !it.getAttribute(ANDROID_URI, ATTR_NAME).isNullOrEmpty() }
        .associate { it to localArguments[it.getAttribute(ANDROID_URI, ATTR_NAME)] }

    destinationToLocal.mapTo(properties) { (dest, local) -> NavArgumentDefaultValueProperty(dest, local, components, attrDefs, this) }
  }
}

class NavArgumentDefaultValueProperty(destinationArgument: NlComponent,
                                      private val actionArgument: NlComponent?,
                                      private val parents: List<NlComponent>,
                                      attrDefs: AttributeDefinitions,
                                      private val navArgumentsProperty: NavArgumentDefaultValuesProperty) :
  NlPropertyItem(XmlName(ATTR_NAME, ANDROID_URI),
                 attrDefs.getAttrDefinition(ResourceReference.attr(ResourceNamespace.ANDROID, ATTR_NAME)),
                 listOf(destinationArgument),
                 navArgumentsProperty.propertiesManager), NavArgumentProperty {

  override val defaultValueProperty: NlProperty =
    actionArgument?.let { ActionArgumentPropertyItem(attrDefs) } ?:
    object : NewElementProperty(parents[0], TAG_ARGUMENT, ATTR_DEFAULT_VALUE, ANDROID_URI, attrDefs,
                                navArgumentsProperty.propertiesManager) {
      override fun setValue(value: Any?) {
        super.setValue(value)
        WriteCommandAction.runWriteCommandAction(navArgumentsProperty.propertiesManager.project) {
          tag?.setAttribute(ATTR_NAME, ANDROID_URI, this@NavArgumentDefaultValueProperty.value)
        }
      }
    }

  override val typeProperty: NlProperty = ActionArgumentTypePropertyItem(attrDefs, destinationArgument)

  private inner class ActionArgumentTypePropertyItem(attrDefs: AttributeDefinitions, destinationArgument: NlComponent)
    : NlPropertyItem(XmlName(ATTR_TYPE, AUTO_URI),
                     attrDefs.getAttrDefinition(ResourceReference.attr(ResourceNamespace.ANDROID, ATTR_TYPE)),
                     listOf(destinationArgument), myPropertiesManager)

  private inner class ActionArgumentPropertyItem(attrDefs: AttributeDefinitions)
    : NlPropertyItem(XmlName(ATTR_DEFAULT_VALUE, ANDROID_URI),
                     attrDefs.getAttrDefinition(ResourceReference.attr(ResourceNamespace.ANDROID, ATTR_DEFAULT_VALUE)),
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