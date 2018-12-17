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
import com.android.tools.idea.uibuilder.model.hasNlComponentInfo
import com.android.tools.idea.uibuilder.model.viewInfo
import com.android.tools.idea.uibuilder.property2.support.TypeResolver
import com.google.common.collect.HashBasedTable
import com.google.common.collect.ImmutableTable
import com.google.common.collect.Table
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.project.DumbService
import com.intellij.openapi.util.text.StringUtil
import com.intellij.psi.PsiClass
import com.intellij.psi.PsiManager
import com.intellij.psi.util.ClassUtil
import com.intellij.psi.xml.XmlTag
import com.intellij.xml.NamespaceAwareXmlAttributeDescriptor
import com.intellij.xml.XmlAttributeDescriptor
import org.jetbrains.android.dom.AndroidDomElementDescriptorProvider
import org.jetbrains.android.dom.attrs.AttributeDefinition
import org.jetbrains.android.dom.attrs.AttributeDefinitions
import com.android.ide.common.rendering.api.AttributeFormat
import com.android.ide.common.rendering.api.ResourceNamespace
import com.android.ide.common.rendering.api.ResourceReference
import org.jetbrains.android.facet.AndroidFacet
import org.jetbrains.android.resourceManagers.ModuleResourceManagers
import java.awt.EventQueue
import java.util.*

private const val EXPECTED_ROWS = 3
private const val EXPECTED_CELLS_PER_ROW = 10

/**
 * Properties generator for Nele.
 *
 * Given a list of [NlComponent]s generate a table with all the properties
 * the components have in common. Only attributes that are available for
 * all versions down to and including minSdkVersion will be included.
 *
 * If layoutlib inflated a different view class, add the attributes from
 * that view as well (unless an existing framework attribute exists).
 *
 * There are special exceptions for the srcCompat attribute and attributes
 * on an AutoCompleteTextView widget.
 */
class NelePropertiesProvider(private val facet: AndroidFacet): PropertiesProvider {
  private val descriptorProvider = AndroidDomElementDescriptorProvider()
  private val emptyTable = ImmutableTable.of<String, String, NelePropertyItem>()

  override fun getProperties(model: NelePropertiesModel,
                             optionalValue: Any?,
                             components: List<NlComponent>): PropertiesTable<NelePropertyItem> {
    assert(!EventQueue.isDispatchThread() || ApplicationManager.getApplication().isUnitTestMode)

    if (components.isEmpty()) {
      return PropertiesTable.emptyTable()
    }

    return DumbService.getInstance(facet.module.project).runReadActionInSmartMode<PropertiesTable<NelePropertyItem>> {
      PropertiesTable.create(getPropertiesImpl(model, components))
    }
  }

  override fun createEmptyTable(): PropertiesTable<NelePropertyItem> =
    PropertiesTable.create(HashBasedTable.create<String, String, NelePropertyItem>(EXPECTED_ROWS, EXPECTED_CELLS_PER_ROW))

  private fun getPropertiesImpl(model: NelePropertiesModel, components: List<NlComponent>): Table<String, String, NelePropertyItem> {
    val resourceManagers = ModuleResourceManagers.getInstance(facet)
    val localResourceManager = resourceManagers.localResourceManager
    val frameworkResourceManager = resourceManagers.frameworkResourceManager
    if (frameworkResourceManager == null) {
      Logger.getInstance(NelePropertiesProvider::class.java).error("No system resource manager for module: " + facet.module.name)
      return emptyTable
    }

    val project = facet.module.project
    val apiLookup = LintIdeClient.getApiLookup(project)
    val minApi = AndroidModuleInfo.getInstance(facet).minSdkVersion.featureLevel

    val localAttrDefs = localResourceManager.attributeDefinitions
    val systemAttrDefs = frameworkResourceManager.attributeDefinitions

    var combinedProperties: Table<String, String, NelePropertyItem>? = null

    for (component in components) {
      val tag = component.tag
      if (!tag.isValid) {
        return emptyTable
      }

      val elementDescriptor = descriptorProvider.getDescriptor(tag) ?: return emptyTable

      val descriptors = elementDescriptor.getAttributesDescriptors(tag)
      val properties = HashBasedTable.create<String, String, NelePropertyItem>(EXPECTED_ROWS, descriptors.size)

      for (desc in descriptors) {
        val name = desc.name
        val namespaceUri = getNamespace(desc, tag)
        // Exclude the framework attributes that were added after the current min API level.
        if (ANDROID_URI == namespaceUri && apiLookup != null &&
            apiLookup.getFieldVersion("android/R\$attr", name) > minApi) {
          continue
        }
        val attrDefs = if (ANDROID_URI == namespaceUri) systemAttrDefs else localAttrDefs
        val namespace = ResourceNamespace.fromNamespaceUri(namespaceUri)
        val attrDef = namespace?.let { attrDefs?.getAttrDefinition(ResourceReference.attr(it, name)) }
        val property = createProperty(namespaceUri, name, attrDef, model, components)
        properties.put(namespaceUri, name, property)
      }

      val tagClass = elementDescriptor.declaration as? PsiClass
      val className = tagClass?.qualifiedName
      if (className != null && component.hasNlComponentInfo) {
        val viewClassName = component.viewInfo?.className
        if (viewClassName != className && viewClassName != null) {
          addAttributesFromInflatedStyleable(properties, localAttrDefs, tagClass, viewClassName, model, components)
        }
      }

      // Exception: Always prefer ATTR_SRC_COMPAT over ATTR_SRC:
      if (properties.contains(AUTO_URI, ATTR_SRC_COMPAT)) {
        properties.remove(ANDROID_URI, ATTR_SRC)
        properties.remove(AUTO_URI, ATTR_SRC)
      }

      // Exceptions:
      if (tag.name == AUTO_COMPLETE_TEXT_VIEW) {
        // An AutoCompleteTextView has a popup that is created at runtime.
        // Properties for this popup can be added to the AutoCompleteTextView tag.
        val attr = systemAttrDefs?.getAttrDefByName(ATTR_POPUP_BACKGROUND)
        val property = createProperty(ANDROID_URI, ATTR_POPUP_BACKGROUND, attr, model, components)
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

  private fun addAttributesFromInflatedStyleable(properties: Table<String, String, NelePropertyItem>,
                                                 localAttrDefs: AttributeDefinitions,
                                                 xmlClass: PsiClass,
                                                 inflatedClassName: String,
                                                 model: NelePropertiesModel,
                                                 components: List<NlComponent>) {
    var inflatedClass = ClassUtil.findPsiClass(PsiManager.getInstance(xmlClass.project), inflatedClassName)
    while (inflatedClass != null && inflatedClass != xmlClass) {
      val styleable = inflatedClass.name?.let { localAttrDefs.getStyleableByName(it) }
      if (styleable != null) {
        for (attrDef in styleable.attributes) {
          if (properties.contains(ANDROID_URI, attrDef.name)) {
            // If the corresponding framework attribute is supported, prefer the framework attribute.
            continue
          }
          val namePair = getPropertyName(attrDef)
          val property = createProperty(namePair.first, namePair.second, attrDef, model, components)
          properties.put(property.namespace, property.name, property)
        }
      }

      inflatedClass = inflatedClass.superClass
    }
  }

  private fun createProperty(namespace: String,
                             name: String,
                             attr: AttributeDefinition?,
                             model: NelePropertiesModel,
                             components: List<NlComponent>): NelePropertyItem {
    val type = TypeResolver.resolveType(name, attr)
    val libraryName = attr?.libraryName ?: ""
    if (namespace == ANDROID_URI && name == ATTR_ID) {
      return NeleIdPropertyItem(model, attr, null, components)
    }
    if (attr != null && attr.formats.contains(AttributeFormat.FLAGS) && attr.values.isNotEmpty()) {
      return NeleFlagsPropertyItem(namespace, name, type, attr, libraryName, model, null, components)
    }
    return NelePropertyItem(namespace, name, type, attr, libraryName, model, null, components)
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
