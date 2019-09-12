/*
 * Copyright (C) 2019 The Android Open Source Project
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
package com.android.tools.idea.layoutinspector.resource

import com.android.SdkConstants.ANDROID_NS_NAME
import com.android.SdkConstants.ANDROID_URI
import com.android.SdkConstants.ATTR_ID
import com.android.SdkConstants.ATTR_NAME
import com.android.SdkConstants.PREFIX_RESOURCE_REF
import com.android.SdkConstants.PREFIX_THEME_REF
import com.android.SdkConstants.TAG_ITEM
import com.android.SdkConstants.TAG_SELECTOR
import com.android.builder.model.AaptOptions
import com.android.ide.common.rendering.api.ResourceNamespace
import com.android.ide.common.rendering.api.ResourceReference
import com.android.ide.common.rendering.api.ResourceValue
import com.android.ide.common.rendering.api.StyleItemResourceValue
import com.android.ide.common.rendering.api.StyleResourceValue
import com.android.ide.common.resources.ResourceItem
import com.android.ide.common.resources.ResourceResolver
import com.android.ide.common.resources.ResourceResolver.MAX_RESOURCE_INDIRECTION
import com.android.ide.common.resources.configuration.FolderConfiguration
import com.android.resources.FolderTypeRelationship
import com.android.resources.ResourceFolderType
import com.android.resources.ResourceType
import com.android.resources.ResourceUrl
import com.android.tools.idea.AndroidPsiUtils
import com.android.tools.idea.layoutinspector.model.ViewNode
import com.android.tools.idea.layoutinspector.properties.InspectorPropertyItem
import com.android.tools.idea.namespacing
import com.android.tools.idea.res.ResourceNamespaceContext
import com.android.tools.idea.res.ResourceRepositoryManager
import com.android.tools.idea.res.StateList
import com.android.tools.idea.res.colorToString
import com.android.tools.idea.res.resolve
import com.android.tools.idea.res.resolveAsIcon
import com.android.tools.idea.res.resolveColor
import com.android.tools.idea.res.resolveLayout
import com.android.tools.idea.res.resolveStateList
import com.android.tools.idea.res.resourceNamespace
import com.android.tools.idea.res.toFileResourcePathString
import com.android.tools.idea.util.toVirtualFile
import com.android.tools.layoutinspector.proto.LayoutInspectorProto.Property.Type.COLOR
import com.intellij.ide.util.EditSourceUtil
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.module.ModuleManager
import com.intellij.openapi.project.Project
import com.intellij.pom.Navigatable
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiRecursiveElementVisitor
import com.intellij.psi.xml.XmlAttribute
import com.intellij.psi.xml.XmlAttributeValue
import com.intellij.psi.xml.XmlFile
import com.intellij.psi.xml.XmlTag
import com.intellij.util.text.nullize
import org.jetbrains.android.dom.manifest.Manifest
import org.jetbrains.android.facet.AndroidFacet
import org.jetbrains.android.util.AndroidResourceUtil
import javax.swing.Icon

/**
 * Find the [AndroidFacet] that matches the specified [packageName] among the facets in the project.
 *
 * The namespaces from the agent are using the real package names.
 */
fun findFacetFromPackage(project: Project, packageName: String): AndroidFacet? {
  return ModuleManager.getInstance(project).modules
    .mapNotNull { AndroidFacet.getInstance(it) }
    .firstOrNull { Manifest.getMainManifest(it)?.`package`?.value == packageName }
}

/**
 * Map a [namespace] to [ResourceNamespace.RES_AUTO] if namespaces are not turned on for the [facet].
 *
 * The namespaces from the agent are using the real package names.
 * Map these into [ResourceNamespace.RES_AUTO] if the design resources are not using namespaces.
 */
fun mapNamespace(facet: AndroidFacet?, namespace: ResourceNamespace): ResourceNamespace =
  when {
    facet != null && facet.namespacing == AaptOptions.Namespacing.REQUIRED -> namespace
    namespace == ResourceNamespace.ANDROID -> ResourceNamespace.ANDROID
    else -> ResourceNamespace.RES_AUTO
  }

/**
 * Adjust a [ResourceReference] depending on namespaces being turned on for the [facet].
 *
 * The namespaces in resource references from the agent are using the real package names.
 * Map these into [ResourceNamespace.RES_AUTO] if the design resources are not using namespaces.
 */
fun mapReference(facet: AndroidFacet?, reference: ResourceReference?): ResourceReference? =
  if (reference == null || facet?.namespacing == AaptOptions.Namespacing.REQUIRED) reference
  else ResourceReference(mapNamespace(facet, reference.namespace), reference.resourceType, reference.name)

/**
 * Find a [Navigatable] given a [PsiElement].
 */
fun findNavigatable(element: PsiElement): Navigatable? =
  when (element) {
    is Navigatable -> element
    else -> EditSourceUtil.getDescriptor(element)
  }

/**
 * Holds functions which implements the services offered in [ResourceLookup].
 */
class ResourceLookupResolver(
  private val project: Project,
  private val appFacet: AndroidFacet,
  private val folderConfiguration: FolderConfiguration,
  private val resolver: ResourceResolver
) {
  private val projectResources = ResourceRepositoryManager.getProjectResources(appFacet)
  private val androidResourceNamespaceResolver =
    ResourceNamespace.Resolver { namespacePrefix -> if (namespacePrefix == ANDROID_NS_NAME) ANDROID_URI else null }
  private val androidNamespaceContext = ResourceNamespaceContext(ResourceNamespace.ANDROID, androidResourceNamespaceResolver)

  /**
   * Find the attribute value from resource reference.
   */
  fun findAttributeValue(property: InspectorPropertyItem, source: ResourceReference): String? {
    return when (source.resourceType) {
      ResourceType.LAYOUT -> findAttributeValueFromViewTag(property, source)
      ResourceType.STYLE -> findAttributeValueFromStyle(property, source)
      else -> null
    }
  }

  /**
   * Find the file locations for a [property].
   *
   * The list of file locations will start from [InspectorPropertyItem.source]. If that is a reference
   * the definition of that reference will be next etc.
   * The [max] guards against recursive indirection in the resources.
   * Each file location is specified by:
   *  - a string containing the file name and a line number
   *  - a [Navigatable] that can be used to goto the source location
   */
  fun findFileLocations(property: InspectorPropertyItem, source: ResourceReference?, max: Int): List<SourceLocation> {
    return when (source?.resourceType) {
      ResourceType.LAYOUT -> findFileLocationsFromViewTag(property, source, max)
      ResourceType.STYLE -> findFileLocationsFromStyle(property, source, max)
      else -> emptyList()
    }
  }

  /**
   * Resolve this drawable property as an icon.
   */
  fun resolveAsIcon(property: InspectorPropertyItem): Icon? {
    val url = property.value?.let { ResourceUrl.parse(it) } ?: return null
    val tag = findViewTagInFile(property.view, property.view.layout) ?: return null
    val (namespace, namespaceResolver) = getNamespacesContext(tag)
    val reference = url.resolve(namespace, namespaceResolver) ?: return null
    return resolver.resolveAsIcon(resolver.getUnresolvedResource(reference), project, appFacet)
  }

  private fun findFileLocationsFromViewTag(property: InspectorPropertyItem, layout: ResourceReference, max: Int): List<SourceLocation> {
    val xmlAttributeValue = findLayoutAttribute(property, layout)?.valueElement ?: return emptyList()
    val location = createFileLocation(xmlAttributeValue) ?: return emptyList()
    val resValue = dereferenceRawAttributeValue(xmlAttributeValue)
    if (max <= 1 || resValue == null) {
      return listOf(location)
    }
    val result = mutableListOf<SourceLocation>()
    result.add(location)
    addValueReference(resValue, result, max - 1)
    return result
  }

  private fun findFileLocationsFromStyle(property: InspectorPropertyItem, style: ResourceReference, max: Int): List<SourceLocation> {
    val reference = mapReference(style) ?: return emptyList()
    val attr = attr(property)
    val styleValue = resolver.getStyle(reference) ?: return emptyList()
    val value = styleValue.getItem(attr) ?: return emptyList()
    val tag = convertStyleItemValueToXmlTag(styleValue, value) ?: return emptyList()
    val location = createFileLocation(tag) ?: return emptyList()
    if (max <= 1 || value.reference == null) {
      return listOf(location)
    }
    val result = mutableListOf<SourceLocation>()
    result.add(location)
    dereference(value)?.let { addValueReference(it, result, max - 1) }
    return result
  }

  private fun findAttributeValueFromViewTag(property: InspectorPropertyItem, layout: ResourceReference): String? {
    val xmlAttributeValue = findLayoutAttribute(property, layout)?.valueElement ?: return null
    val resValue = dereferenceRawAttributeValue(xmlAttributeValue) ?: return xmlAttributeValue.value
    return resolveValue(property, resValue) ?: xmlAttributeValue.value
  }

  private fun findAttributeValueFromStyle(property: InspectorPropertyItem, style: ResourceReference): String? {
    val reference = mapReference(style) ?: return null
    val styleValue = resolver.getStyle(reference) ?: return null
    val styleItem = styleValue.getItem(attr(property))?.let { StyleItemResourceValueWithStyleReference(styleValue, it) } ?: return null
    return resolveValue(property, styleItem)
  }

  /**
   * Resolve a [ResourceValue] to a string value.
   *
   * Color values should be resolved by paying attention to the alpha factor
   * in color state lists. This is handled by [ResourceResolver.resolveColor].
   *
   * Other types are resolved using [dereference] rather than [ResourceResolver.resolveResValue]
   * since the former also handles state lists.
   */
  private fun resolveValue(property: InspectorPropertyItem, resValue: ResourceValue): String? {
    if (property.type == COLOR) {
      return resolver.resolveColor(resValue, project)?.let { colorToString(it) }
    }
    return toString(property, generateSequence(resValue) { dereference(it) }.take(MAX_RESOURCE_INDIRECTION).last())
  }

  /**
   * Convert the [ResourceValue] into a readable string value.
   *
   * If the value is a file return the reference value instead of the filename.
   */
  private fun toString(property: InspectorPropertyItem, resValue: ResourceValue): String? {
    val stringValue = resValue.value ?: return null
    // If this is not a file accept the value as is.
    toFileResourcePathString(stringValue) ?: return stringValue

    // Otherwise convert to a reference value instead (e.g. @drawable/my_drawable).
    val tag = findViewTagInFile(property.view, property.view.layout) ?: return stringValue
    val (namespace, namespaceResolver) = getNamespacesContext(tag)
    val reference = resValue.asReference()
    val url = reference.getRelativeResourceUrl(namespace, namespaceResolver)
    return url.toString()
  }

  private fun attr(property: InspectorPropertyItem): ResourceReference {
    val attrNamespace = mapNamespace(ResourceNamespace.fromNamespaceUri(property.namespace) ?: ResourceNamespace.ANDROID)
    return ResourceReference.attr(attrNamespace, property.attrName)
  }

  private fun dereference(value: ResourceValue): ResourceValue? {
    val stateList = convertToStateList(value)
    return when {
      stateList != null -> dereferenceStateList(stateList, value)
      value.value?.startsWith(PREFIX_THEME_REF) == true -> dereferenceThemeReference(value.reference)
      else -> resolver.dereference(value)
    }
  }

  private fun dereferenceStateList(stateList: StateList, value: ResourceValue): ResourceValue? {
    val state = stateList.states.lastOrNull() ?: return null
    val stringValue = state.value ?: return null
    val url = ResourceUrl.parse(stringValue) ?: return null
    val tag = convertStateListToXmlTag(value) ?: return null
    val (namespace, namespaceResolver) = getNamespacesContext(tag)
    return url.resolve(namespace, namespaceResolver)?.let { dereferenceReference(it) }
  }

  // Cannot use ResourceHelper.resolve directly since some framework state lists do not have namespace declarations.
  // Instead default to the predefined androidResourceNamespaceResolver.
  private fun getNamespacesContext(tag: XmlTag): ResourceNamespaceContext {
    return tag.resourceNamespace?.let {
      ResourceNamespaceContext(it, object : ResourceNamespace.Resolver {
        override fun uriToPrefix(namespaceUri: String): String? = tag.getPrefixByNamespace(namespaceUri)
        override fun prefixToUri(namespacePrefix: String): String? = tag.getNamespaceByPrefix(namespacePrefix).nullize()
      })
    } ?: androidNamespaceContext
  }

  // Unfortunately a style item is not a ResourceItem, and we need the style value to locate this value.
  // Therefore return the StyleItemResourceValue with the style reference.
  private fun dereferenceThemeReference(themeReference: ResourceReference?): StyleItemResourceValueWithStyleReference? {
    val attr = themeReference ?: return null
    for (theme in resolver.allThemes) {
      val value = findItemInTheme(theme, attr)
      if (value != null) {
        return value
      }
    }
    return null
  }

  private fun dereferenceRawAttributeValue(xmlAttributeValue: XmlAttributeValue): ResourceValue? {
    val url = ResourceUrl.parse(xmlAttributeValue.value) ?: return null
    return url.resolve(xmlAttributeValue)?.let { dereferenceReference(it) }
  }

  private fun dereferenceReference(reference: ResourceReference): ResourceValue? {
    if (reference.resourceType == ResourceType.ATTR) {
      return dereferenceThemeReference(reference)
    }
    else {
      return resolver.getUnresolvedResource(reference)
    }
  }

  private fun findItemInTheme(theme: StyleResourceValue, attr: ResourceReference): StyleItemResourceValueWithStyleReference? {
    var style = theme
    for (depth in 0 until MAX_RESOURCE_INDIRECTION) {
      val item = style.getItem(attr)

      if (item != null) {
        return StyleItemResourceValueWithStyleReference(style, item)
      }
      style = resolver.getParent(style) ?: return null
    }
    return null
  }

  private fun addValueReference(resourceValue: ResourceValue, result: MutableList<SourceLocation>, max: Int) {
    val tag = convertToXmlTag(resourceValue) ?: return
    val location = createFileLocation(tag) ?: return
    result.add(location)
    if (max > 1) {
      val nextValue = dereference(resourceValue) ?: return
      addValueReference(nextValue, result, max - 1)
    }
  }

  private fun convertToXmlTag(value: ResourceValue): XmlTag? {
    val stateList = convertToStateList(value)
    return when {
      stateList != null -> convertStateListToXmlTag(value)
      value is StyleItemResourceValueWithStyleReference -> convertStyleItemValueToXmlTag(value.style, value)
      else -> convertSimpleValueToXmlTag(value)
    }
  }

  private fun convertSimpleValueToXmlTag(value: ResourceValue): XmlTag? {
    val item = convertToResourceItem(value) ?: return null
    if (FolderTypeRelationship.getRelatedFolders(item.type).contains(ResourceFolderType.VALUES)) {
      return AndroidResourceUtil.getItemTag(project, item)
    }
    val xmlFile = AndroidResourceUtil.getItemPsiFile(project, item) as? XmlFile
    return xmlFile?.rootTag
  }

  private fun convertStyleItemValueToXmlTag(style: StyleResourceValue, item: StyleItemResourceValue): XmlTag? {
    // TODO: Unfortunately style items are not ResourceItems. For now lookup the item in the XmlTag of the style.
    val styleItem = convertToResourceItem(style) ?: return null
    val styleTag = AndroidResourceUtil.getItemTag(project, styleItem) ?: return null
    val itemTags = styleTag.findSubTags(TAG_ITEM)
    return itemTags.find { it.getAttributeValue(ATTR_NAME) == item.attrName }
  }

  private fun convertStateListToXmlTag(value: ResourceValue): XmlTag? {
    val stringValue = value.value ?: return null
    val virtualFile = toFileResourcePathString(stringValue)?.toVirtualFile() ?: return null
    val xmlFile = (AndroidPsiUtils.getPsiFileSafely(project, virtualFile) as? XmlFile) ?: return null
    val rootTag = xmlFile.rootTag ?: return null
    if (TAG_SELECTOR != rootTag.name) {
      return null
    }
    // TODO: Select the correct state between the sub tags of rootTag on the actual state of the view:
    return rootTag.findSubTags(TAG_ITEM).lastOrNull()
  }

  private fun convertToStateList(value: ResourceValue): StateList? {
    val stringValue = value.value ?: return null
    // Do not allow resolveStateList to resolve a reference value:
    if (stringValue.startsWith(PREFIX_RESOURCE_REF)) {
      return null
    }
    return resolver.resolveStateList(value, project)
  }

  // TODO: Remove this when project style values are known to also be ResourceItems
  private fun convertToResourceItem(value: ResourceValue): ResourceItem? {
    if (value is ResourceItem) {
      // Framework values are already ResourceItems:
      return value
    }
    // Otherwise find the item from the ResourceRepository by matching the configuration:
    val items = projectResources.getResources(value.asReference())
    return folderConfiguration.findMatchingConfigurable(items)
  }

  private fun createFileLocation(element: PsiElement): SourceLocation? {
    val source = findSourceLocationWithLineNumber(element) ?: return null
    val navigatable = findNavigatable(element) ?: return null
    return SourceLocation(source, navigatable)
  }

  private fun findSourceLocationWithLineNumber(element: PsiElement): String? {
    val file = element.containingFile ?: return null
    val doc = file.virtualFile?.let { FileDocumentManager.getInstance().getDocument(it) }
    val line = doc?.getLineNumber(element.textOffset)?.plus(1) ?: return file.name
    return "${file.name}:$line"
  }

  private fun findLayoutAttribute(property: InspectorPropertyItem, layout: ResourceReference): XmlAttribute? {
    val reference = mapReference(layout) ?: return null
    val tag = findViewTagInFile(property.view, reference)
    return tag?.getAttribute(property.attrName, property.namespace)
  }

  private fun findViewTagInFile(view: ViewNode, layout: ResourceReference?): XmlTag? {
    if (layout == null) {
      return null
    }
    view.tag?.let { return it }

    val layoutValue = resolver.getUnresolvedResource(layout)
    val file = resolver.resolveLayout(layoutValue) ?: return null
    val xmlFile = (AndroidPsiUtils.getPsiFileSafely(project, file) as? XmlFile) ?: return null
    val locator = ViewLocator(view)
    xmlFile.rootTag?.accept(locator)
    return locator.found?.also { view.tag = it }
  }

  /**
   * Map a [namespace] to [ResourceNamespace.RES_AUTO] if namespaces are not turned on.
   *
   * The namespaces in resource references from the agent are using the real package names.
   * Map these into [ResourceNamespace.RES_AUTO] if the design resources are not handled
   * with namespaces.
   */
  private fun mapNamespace(namespace: ResourceNamespace): ResourceNamespace = mapNamespace(appFacet, namespace)

  private fun mapReference(reference: ResourceReference?): ResourceReference? = mapReference(appFacet, reference)

  /**
   * A [PsiRecursiveElementVisitor] to find a [view] in an [XmlFile].
   */
  private inner class ViewLocator(private val view: ViewNode) : PsiRecursiveElementVisitor() {
    var found: XmlTag? = null

    override fun visitElement(element: PsiElement) {
      if (element !is XmlTag) return
      if (sameId(element)) {
        found = element
        return
      }
      super.visitElement(element)
    }

    private fun sameId(tag: XmlTag): Boolean {
      val attr = tag.getAttributeValue(ATTR_ID, ANDROID_URI)
      if (view.viewId == null || attr == null) {
        return false
      }
      val ref = ResourceUrl.parse(attr)?.resolve(tag)
      return mapReference(view.viewId) == ref
    }
  }
}
