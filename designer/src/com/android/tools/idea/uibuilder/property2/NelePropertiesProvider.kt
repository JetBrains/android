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
package com.android.tools.idea.uibuilder.property2

import com.android.SdkConstants.*
import com.android.tools.idea.common.model.NlComponent
import com.android.tools.idea.common.property2.api.PropertiesTable
import com.android.tools.idea.lint.LintIdeClient
import com.android.tools.idea.model.AndroidModuleInfo
import com.android.tools.idea.uibuilder.model.currentActivityIsDerivedFromAppCompatActivity
import com.android.tools.idea.uibuilder.model.moduleDependsOnAppCompat
import com.android.tools.idea.uibuilder.property2.support.TypeResolver
import com.google.common.collect.HashBasedTable
import com.google.common.collect.ImmutableTable
import com.google.common.collect.Table
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.project.DumbService
import com.intellij.openapi.util.text.StringUtil
import com.intellij.psi.xml.XmlTag
import com.intellij.xml.NamespaceAwareXmlAttributeDescriptor
import com.intellij.xml.XmlAttributeDescriptor
import org.jetbrains.android.dom.AndroidDomElementDescriptorProvider
import org.jetbrains.android.dom.attrs.AttributeDefinition
import org.jetbrains.android.dom.attrs.AttributeFormat
import org.jetbrains.android.resourceManagers.ModuleResourceManagers
import java.awt.EventQueue
import java.util.*

/**
 * Properties generator for Nele.
 *
 * Given a list of [NlComponent]s generate a table with all the properties
 * the components have in common. Only attributes that are available for
 * all versions down to and including minSdkVersion will be included.
 *
 * If this module uses AppCompat, the AppCompat attributes are added to the
 * available properties (unless an existing framework attribute exists).
 *
 * There are special exceptions for the srcCompat attribute and attributes
 * on an AutoCompleteTextView widget.
 */
class NelePropertiesProvider(private val model: NelePropertiesModel) {
  private val descriptorProvider = AndroidDomElementDescriptorProvider()
  private val emptyTable = ImmutableTable.of<String, String, NelePropertyItem>()

  fun getProperties(components: List<NlComponent>): PropertiesTable<NelePropertyItem> {
    assert(!EventQueue.isDispatchThread() || ApplicationManager.getApplication().isUnitTestMode)

    if (components.isEmpty()) {
      return PropertiesTable.emptyTable()
    }

    return DumbService.getInstance(model.facet.module.project).runReadActionInSmartMode<PropertiesTable<NelePropertyItem>> {
      PropertiesTable.create(getPropertiesImpl(components))
    }
  }

  private fun getPropertiesImpl(components: List<NlComponent>): Table<String, String, NelePropertyItem> {
    val resourceManagers = ModuleResourceManagers.getInstance(model.facet)
    val localResourceManager = resourceManagers.localResourceManager
    val systemResourceManager = resourceManagers.systemResourceManager
    if (systemResourceManager == null) {
      Logger.getInstance(NelePropertiesProvider::class.java).error("No system resource manager for module: " + model.facet.module.name)
      return emptyTable
    }

    val project = model.facet.module.project
    val apiLookup = LintIdeClient.getApiLookup(project)
    val minApi = AndroidModuleInfo.getInstance(model.facet).minSdkVersion.featureLevel
    val nlModel = components[0].model
    val appCompatUsed = nlModel.moduleDependsOnAppCompat() && nlModel.currentActivityIsDerivedFromAppCompatActivity()

    val localAttrDefs = localResourceManager.attributeDefinitions
    val systemAttrDefs = systemResourceManager.attributeDefinitions

    var combinedProperties: Table<String, String, NelePropertyItem>? = null

    for (component in components) {
      val tag = component.tag
      if (!tag.isValid) {
        return emptyTable
      }

      val elementDescriptor = descriptorProvider.getDescriptor(tag) ?: return emptyTable

      val descriptors = elementDescriptor.getAttributesDescriptors(tag)
      val properties = HashBasedTable.create<String, String, NelePropertyItem>(3, descriptors.size)

      for (desc in descriptors) {
        val name = desc.name
        val namespace = getNamespace(desc, tag)
        // Exclude the framework attributes that were added after the current min API level.
        if (NS_RESOURCES == namespace && apiLookup != null &&
            apiLookup.getFieldVersion("android/R\$attr", name) > minApi) {
          continue
        }
        val attrDefs = if (NS_RESOURCES == namespace) systemAttrDefs else localAttrDefs
        val attrDef = attrDefs?.getAttrDefByName(name)
        val property = createProperty(namespace, name, attrDef, components)
        properties.put(namespace, name, property)
      }

      if (appCompatUsed && tag.localName.indexOf('.') < 0) {
        val styleable = localAttrDefs.getStyleableByName("AppCompat" + tag.localName)
        if (styleable != null) {
          for (attrDef in styleable.attributes) {
            if (properties.contains(NS_RESOURCES, attrDef.name)) {
              // If the corresponding framework attribute is supported, prefer the framework attribute.
              continue
            }
            val namePair = getPropertyName(attrDef)
            val property = createProperty(namePair.first, namePair.second, attrDef, components)
            properties.put(property.namespace, property.name, property)
          }
        }
        // Exception: Always prefer ATTR_SRC_COMPAT over ATTR_SRC:
        if (properties.contains(AUTO_URI, ATTR_SRC_COMPAT)) {
          properties.remove(ANDROID_URI, ATTR_SRC)
          properties.remove(AUTO_URI, ATTR_SRC)
        }
      }

      // Exceptions:
      if (tag.name == AUTO_COMPLETE_TEXT_VIEW) {
        // An AutoCompleteTextView has a popup that is created at runtime.
        // Properties for this popup can be added to the AutoCompleteTextView tag.
        val attr = systemAttrDefs?.getAttrDefByName(ATTR_POPUP_BACKGROUND)
        val property = createProperty(ANDROID_URI, ATTR_POPUP_BACKGROUND, attr, components)
        properties.put(ANDROID_URI, ATTR_POPUP_BACKGROUND, property)
      }

      combinedProperties = combine(properties, combinedProperties)
    }

    // The following properties are deprecated in the support library and can be ignored by tools:
    combinedProperties?.let {
      it.remove(AUTO_URI, ATTR_PADDING_START)
      it.remove(AUTO_URI, ATTR_PADDING_END)
      it.remove(AUTO_URI, ATTR_THEME)
    }

    return combinedProperties ?: emptyTable
  }

  private fun createProperty(namespace: String, name: String, attr: AttributeDefinition?, components: List<NlComponent>): NelePropertyItem {
    val type = TypeResolver.resolveType(name, attr)
    val libraryName = attr?.libraryName ?: ""
    if (attr != null && attr.formats.contains(AttributeFormat.Flag) && attr.values.isNotEmpty()) {
      return NeleFlagsPropertyItem(namespace, name, type, attr, libraryName, model, components)
    }
    return NelePropertyItem(namespace, name, type, attr, libraryName, model, components)
  }

  private fun getNamespace(descriptor: XmlAttributeDescriptor, context: XmlTag): String {
    return (descriptor as? NamespaceAwareXmlAttributeDescriptor)?.getNamespace(context) ?: ANDROID_URI
  }

  // TODO: Fix when AttributeDefinition supports namespaces
  private fun getPropertyName(definition: AttributeDefinition): Pair<String, String> {
    val qualifiedName = definition.name
    if (qualifiedName.startsWith(ANDROID_NS_NAME_PREFIX)) {
      return ANDROID_URI to StringUtil.trimStart(qualifiedName, ANDROID_NS_NAME_PREFIX)
    }
    return AUTO_URI to qualifiedName
  }

  // When components of different type are selected: e.g. a ImageButton and a TextView,
  // we just show the attributes those components have in common.
  private fun combine(properties: Table<String, String, NelePropertyItem>,
                      combinedProperties: Table<String, String, NelePropertyItem>?): Table<String, String, NelePropertyItem> {
    if (combinedProperties == null) {
      return properties
    }
    val namespaces = ArrayList(combinedProperties.rowKeySet())
    val propertiesToRemove = ArrayList<String>()
    for (namespace in namespaces) {
      propertiesToRemove.clear()
      for ((name, item) in combinedProperties.row(namespace)) {
        if (item != properties.get(namespace, name)) {
          propertiesToRemove.add(name)
        }
      }
      for (propertyName in propertiesToRemove) {
        combinedProperties.remove(namespace, propertyName)
      }
    }
    // Never include the ID attribute when looking at multiple components:
    combinedProperties.remove(ANDROID_URI, ATTR_ID)
    return combinedProperties
  }
}
