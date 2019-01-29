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
import com.android.tools.idea.naveditor.property.inspector.SimpleProperty
import com.android.tools.idea.uibuilder.property.NlPropertyItem
import com.intellij.openapi.command.WriteCommandAction
import com.intellij.psi.xml.XmlFile
import com.intellij.util.xml.XmlName
import org.jetbrains.android.dom.attrs.AttributeDefinitions
import org.jetbrains.android.dom.navigation.NavigationSchema
import org.jetbrains.android.dom.navigation.NavigationSchema.ATTR_DEFAULT_VALUE
import org.jetbrains.android.dom.navigation.NavigationSchema.TAG_ARGUMENT
import org.jetbrains.android.resourceManagers.ModuleResourceManagers
import org.jetbrains.android.resourceManagers.ResourceManager

/**
 * Property representing all the arguments (possibly zero) for an action.
 */
class NavArgumentDefaultValuesProperty(components: List<NlComponent>, val propertiesManager: NavPropertiesManager)
  : SimpleProperty("Arguments", components) {

  private val resourceManager: ResourceManager? = if (components.isEmpty()) {
    null
  }
  else {
    ModuleResourceManagers.getInstance(components[0].model.facet).localResourceManager
  }

  private val attrDefs: AttributeDefinitions? = resourceManager?.attributeDefinitions

  val properties = mutableListOf<NavArgumentDefaultValueProperty>()

  init {
    refreshList()
  }

  fun refreshList() {
    if (attrDefs == null) {
      return
    }
    properties.clear()

    val localArguments: Map<String, NlComponent> =
      components.flatMap { it.children }
        .filter { it.tagName == NavigationSchema.TAG_ARGUMENT }
        .associateBy { it.getAttribute(ANDROID_URI, ATTR_NAME) ?: "" }

    val includes = mutableListOf<NlComponent>()
    val destinationArgNameToType: MutableMap<String, String?> =
      components.mapNotNull { component ->
        if (component.isAction) {
          component.actionDestination?.let {
            when {
              it.isInclude -> {
                includes.add(it)
                null
              }
              it.isNavigation -> it.startDestination
              else -> it
            }
          }
        }
        else {
          component.startDestination
        }
      }
        .flatMap { it.children }
        .filter { it.tagName == TAG_ARGUMENT && !it.getAttribute(ANDROID_URI, ATTR_NAME).isNullOrEmpty() }
        .associate { it.getAttribute(ANDROID_URI, ATTR_NAME)!! to it.getAttribute(AUTO_URI, ATTR_ARG_TYPE) }
        .toMutableMap()

    includes.flatMap { findIncludeArgumentNameToType(it.includeFile, it.startDestinationId) }.associateTo(destinationArgNameToType) { it }

    destinationArgNameToType.mapTo(properties) { (name, type) ->
      val local = localArguments[name]
      val propertyBase = local?.let { ArgumentDefaultValuePropertyItem(attrDefs, listOf(local)) }
                         ?: object : NewElementProperty(components[0], TAG_ARGUMENT, ATTR_DEFAULT_VALUE, ANDROID_URI, attrDefs,
                                                        propertiesManager) {
                           override fun setValue(value: Any?) {
                             super.setValue(value)
                             WriteCommandAction.runWriteCommandAction(propertiesManager.project) {
                               tag?.setAttribute(ATTR_NAME, ANDROID_URI, name)
                             }
                           }
                         }
      NavArgumentDefaultValueProperty(propertyBase, name, type)
    }
  }

  private fun findIncludeArgumentNameToType(file: XmlFile?, startDestination: String?): List<Pair<String, String?>> {
    if (file == null || startDestination == null) {
      return listOf()
    }
    val startDestinationTag =
      file.rootTag?.subTags?.find { it.getAttributeValue(ATTR_ID, ANDROID_URI) == "$NEW_ID_PREFIX$startDestination" } ?: return listOf()

    return startDestinationTag.findSubTags(TAG_ARGUMENT)
      .mapNotNull { it.getAttributeValue(ATTR_NAME, ANDROID_URI)?.let { name -> name to it.getAttributeValue(ATTR_ARG_TYPE, AUTO_URI) } }
  }

  private inner class ArgumentDefaultValuePropertyItem(attrDefs: AttributeDefinitions, val argumentComponents: List<NlComponent>)
    : NlPropertyItem(XmlName(ATTR_DEFAULT_VALUE, ANDROID_URI),
                     attrDefs.getAttrDefinition(ResourceReference.attr(ResourceNamespace.ANDROID, ATTR_DEFAULT_VALUE)),
                     argumentComponents, propertiesManager) {
    override fun setValue(value: Any?) {
      super.setValue(value)
      deleteIfNeeded()
    }

    private fun deleteIfNeeded() {
      if (value.isNullOrEmpty()) {
        model.delete(argumentComponents)
        this@NavArgumentDefaultValuesProperty.refreshList()
      }
    }
  }
}

class NavArgumentDefaultValueProperty(base: NlProperty, val argName: String, val type: String?) : NlProperty by base