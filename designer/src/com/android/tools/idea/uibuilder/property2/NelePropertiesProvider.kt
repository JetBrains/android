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
package com.android.tools.idea.uibuilder.property

import com.android.SdkConstants.ANDROID_PKG_PREFIX
import com.android.SdkConstants.ANDROID_SUPPORT_PKG_PREFIX
import com.android.SdkConstants.ANDROID_URI
import com.android.SdkConstants.ATTR_ID
import com.android.SdkConstants.ATTR_PADDING_END
import com.android.SdkConstants.ATTR_PADDING_START
import com.android.SdkConstants.ATTR_POPUP_BACKGROUND
import com.android.SdkConstants.ATTR_SRC
import com.android.SdkConstants.ATTR_SRC_COMPAT
import com.android.SdkConstants.ATTR_THEME
import com.android.SdkConstants.AUTO_COMPLETE_TEXT_VIEW
import com.android.SdkConstants.AUTO_URI
import com.android.SdkConstants.FQCN_AUTO_COMPLETE_TEXT_VIEW
import com.android.ide.common.rendering.api.AttributeFormat
import com.android.ide.common.rendering.api.ResourceNamespace
import com.android.ide.common.rendering.api.ResourceReference
import com.android.resources.ResourceType
import com.android.tools.idea.common.model.NlComponent
import com.android.tools.idea.lint.common.LintIdeClient
import com.android.tools.property.panel.api.PropertiesTable
import com.android.tools.idea.model.AndroidModuleInfo
import com.android.tools.idea.uibuilder.model.hasNlComponentInfo
import com.android.tools.idea.uibuilder.model.viewInfo
import com.android.tools.idea.uibuilder.property.support.TypeResolver
import com.google.common.collect.HashBasedTable
import com.google.common.collect.ImmutableTable
import com.google.common.collect.Table
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.project.DumbService
import com.intellij.psi.JavaPsiFacade
import com.intellij.psi.PsiClass
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.psi.xml.XmlTag
import com.intellij.xml.NamespaceAwareXmlAttributeDescriptor
import com.intellij.xml.XmlAttributeDescriptor
import org.jetbrains.android.dom.AndroidDomElementDescriptorProvider
import org.jetbrains.android.dom.AttributeProcessingUtil
import org.jetbrains.android.dom.attrs.AttributeDefinition
import org.jetbrains.android.dom.attrs.AttributeDefinitions
import org.jetbrains.android.facet.AndroidFacet
import org.jetbrains.android.resourceManagers.ModuleResourceManagers
import java.awt.EventQueue
import java.util.ArrayList

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
class NlPropertiesProvider(private val facet: AndroidFacet): PropertiesProvider {

  override fun getProperties(model: NlPropertiesModel,
                             optionalValue: Any?,
                             components: List<NlComponent>): PropertiesTable<NlPropertyItem> {
    assert(!EventQueue.isDispatchThread() || ApplicationManager.getApplication().isUnitTestMode)

    if (components.isEmpty()) {
      return PropertiesTable.emptyTable()
    }

    val project = facet.module.project
    val resourceManagers = ModuleResourceManagers.getInstance(facet)
    val localResourceManager = resourceManagers.localResourceManager
    val frameworkResourceManager = resourceManagers.frameworkResourceManager
    val localAttrDefs = localResourceManager.attributeDefinitions
    val systemAttrDefs = frameworkResourceManager?.attributeDefinitions

    if (frameworkResourceManager == null) {
      Logger.getInstance(NlPropertiesProvider::class.java).error("No system resource manager for module: " + facet.module.name)
      return PropertiesTable.emptyTable()
    }
    if (systemAttrDefs == null) {
      return PropertiesTable.emptyTable()
    }
    val generator = PropertiesGenerator(facet, model, components, localAttrDefs, systemAttrDefs)

    return DumbService.getInstance(project).runReadActionInSmartMode<PropertiesTable<NlPropertyItem>> {
      PropertiesTable.create(generator.generate())
    }
  }

  override fun createEmptyTable(): PropertiesTable<NlPropertyItem> =
    PropertiesTable.create(HashBasedTable.create(EXPECTED_ROWS, EXPECTED_CELLS_PER_ROW))

  private class PropertiesGenerator(facet: AndroidFacet,
                                    private val model: NlPropertiesModel,
                                    private val components: List<NlComponent>,
                                    private val localAttrDefs: AttributeDefinitions,
                                    private val systemAttrDefs: AttributeDefinitions) {
    private val project = facet.module.project
    private val apiLookup = LintIdeClient.getApiLookup(project)
    private val minApi = AndroidModuleInfo.getInstance(facet).minSdkVersion.featureLevel
    private val psiFacade = JavaPsiFacade.getInstance(project)
    private val descriptorProvider = AndroidDomElementDescriptorProvider()
    private var properties: Table<String, String, NlPropertyItem> = ImmutableTable.of()
    private val emptyTable = ImmutableTable.of<String, String, NlPropertyItem>()

    fun generate(): Table<String, String, NlPropertyItem> {
      var combinedProperties: Table<String, String, NlPropertyItem>? = null
      for (component in components) {
        val tag = component.tag ?: return emptyTable

        val elementDescriptor = descriptorProvider.getDescriptor(tag) ?: return emptyTable
        val descriptors = elementDescriptor.getAttributesDescriptors(tag)
        properties = HashBasedTable.create(EXPECTED_ROWS, descriptors.size)

        loadPropertiesFromDescriptors(tag, descriptors)

        if (component.hasNlComponentInfo) {
          loadPropertiesFromStyleable(component)
          loadPropertiesFromLayoutStyleable(component)
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
          val attr = systemAttrDefs.getAttrDefByName(ATTR_POPUP_BACKGROUND)
          val property = createProperty(ANDROID_URI, ATTR_POPUP_BACKGROUND, attr, FQCN_AUTO_COMPLETE_TEXT_VIEW, model, components)
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

    private fun loadPropertiesFromDescriptors(tag: XmlTag, descriptors: Array<XmlAttributeDescriptor>) {
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
        if (!properties.contains(namespaceUri, name)) {
          val attrDef = namespace?.let { attrDefs.getAttrDefinition(ResourceReference.attr(it, name)) }
          val property = createProperty(namespaceUri, name, attrDef, "", model, components)
          properties.put(namespaceUri, name, property)
        }
      }
    }

    private fun loadPropertiesFromStyleable(component: NlComponent) {
      var psiClass: PsiClass? = findPsiClassOfComponent(component)
      while (psiClass != null) {
        loadFromStyleableName(psiClass, psiClass.name)
        psiClass = psiClass.superClass
      }
    }

    private fun loadPropertiesFromLayoutStyleable(component: NlComponent) {
      val parent = component.parent ?: return
      var psiClass: PsiClass? = findPsiClassOfComponent(parent)
      while (psiClass != null) {
        loadFromStyleableName(psiClass, AttributeProcessingUtil.getLayoutStyleablePrimary(psiClass))
        loadFromStyleableName(psiClass, AttributeProcessingUtil.getLayoutStyleableSecondary(psiClass))
        psiClass = psiClass.superClass
      }
    }

    private fun loadFromStyleableName(psiClass: PsiClass, styleableName: String?) {
      if (styleableName == null) {
        return
      }
      val namespace = findNamespaceFromPsiClass(psiClass) ?: return
      val reference = ResourceReference(namespace, ResourceType.STYLEABLE, styleableName)
      val attrDefs = if (namespace.xmlNamespaceUri == ANDROID_URI) systemAttrDefs else localAttrDefs
      val styleable = attrDefs.getStyleableDefinition(reference) ?: return
      styleable.attributes.forEach { addPropertyFromAttribute(it, psiClass) }
    }

    private fun findPsiClassOfComponent(component: NlComponent): PsiClass? {
      val tag = component.tag ?: return null
      val psiClass = PsiTreeUtil.getParentOfType(tag, PsiClass::class.java)
      val viewClassName = component.viewInfo?.className
      if (viewClassName != null && viewClassName != psiClass?.qualifiedName) {
        return psiFacade.findClass(viewClassName, GlobalSearchScope.allScope(project))
      }
      return psiClass
    }

    // TODO: Fix the namespace computation below...
    private fun findNamespaceFromPsiClass(psiClass: PsiClass): ResourceNamespace? {
      val className = psiClass.qualifiedName ?: return null
      val namespaceUri = if (className.startsWith(ANDROID_PKG_PREFIX) &&
                             !className.startsWith(ANDROID_SUPPORT_PKG_PREFIX)) ANDROID_URI
      else AUTO_URI
      return ResourceNamespace.fromNamespaceUri(namespaceUri)
    }

    private fun addPropertyFromAttribute(attribute: AttributeDefinition, psiClass: PsiClass) {
      val namespace = attribute.resourceReference.namespace.xmlNamespaceUri
      val property = createProperty(namespace, attribute.name, attribute, psiClass.qualifiedName ?: "", model, components)
      if (ANDROID_URI == namespace && apiLookup != null &&
          apiLookup.getFieldVersion("android/R\$attr", attribute.name) > minApi) {
        // Exclude the framework attributes that were added after the current min API level.
        return
      }
      if (namespace != ANDROID_URI && properties.contains(ANDROID_URI, attribute.name)) {
        // If the corresponding framework attribute is supported, prefer the framework attribute.
        return
      }
      properties.put(property.namespace, property.name, property)
    }

    private fun createProperty(namespace: String,
                               name: String,
                               attr: AttributeDefinition?,
                               componentName: String,
                               model: NlPropertiesModel,
                               components: List<NlComponent>): NlPropertyItem {
      val type = TypeResolver.resolveType(name, attr)
      val libraryName = attr?.libraryName ?: ""
      if (namespace == ANDROID_URI && name == ATTR_ID) {
        return NlIdPropertyItem(model, attr, componentName, components)
      }
      if (attr != null && attr.formats.contains(AttributeFormat.FLAGS) && attr.values.isNotEmpty()) {
        return NlFlagsPropertyItem(namespace, name, type, attr, componentName, libraryName, model, components)
      }
      return NlPropertyItem(namespace, name, type, attr, componentName, libraryName, model, components)
    }

    private fun getNamespace(descriptor: XmlAttributeDescriptor, context: XmlTag): String {
      return (descriptor as? NamespaceAwareXmlAttributeDescriptor)?.getNamespace(context) ?: ANDROID_URI
    }

    // When components of different type are selected: e.g. a ImageButton and a TextView,
    // we just show the attributes those components have in common.
    private fun combine(properties: Table<String, String, NlPropertyItem>,
                        combinedProperties: Table<String, String, NlPropertyItem>?): Table<String, String, NlPropertyItem> {
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
}
