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
package com.android.tools.idea.layoutinspector

import com.android.SdkConstants.ANDROID_NS_NAME
import com.android.SdkConstants.ANDROID_URI
import com.android.SdkConstants.ATTR_ID
import com.android.SdkConstants.ATTR_NAME
import com.android.builder.model.AaptOptions
import com.android.ide.common.rendering.api.ResourceNamespace
import com.android.ide.common.rendering.api.ResourceReference
import com.android.resources.ResourceFolderType
import com.android.resources.ResourceType
import com.android.resources.ResourceUrl
import com.android.tools.idea.layoutinspector.model.ViewNode
import com.android.tools.idea.layoutinspector.properties.InspectorPropertyItem
import com.android.tools.idea.namespacing
import com.android.tools.idea.res.getNamespaceResolver
import com.android.tools.idea.res.resolve
import com.intellij.ide.util.EditSourceUtil
import com.intellij.openapi.module.ModuleManager
import com.intellij.openapi.project.Project
import com.intellij.pom.Navigatable
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiRecursiveElementVisitor
import com.intellij.psi.xml.XmlFile
import com.intellij.psi.xml.XmlTag
import org.jetbrains.android.dom.resources.Style
import org.jetbrains.android.facet.AndroidFacet
import org.jetbrains.android.resourceManagers.ModuleResourceManagers

/**
 * Utility for looking up resources in a project.
 *
 * This class contains facilities for jumping to the definition of property values
 * given the runtime data available.
 */
object DesignLookup {

  /**
   * Goto the definition of the property value.
   *
   * Given the "source" location of a property value i.e. the place where it was set,
   * jump to the place where the value is set possibly opening a new file for editing.
   */
  fun gotoLayoutAttributeDefinition(property: InspectorPropertyItem) {
    val element = findAttributeDefinition(property) ?: return
    val navigatable = element as? Navigatable ?: EditSourceUtil.getDescriptor(element)
    navigatable?.navigate(true)
  }

  fun gotoViewInLayout(project: Project, view: ViewNode) {
    val layout = view.layout ?: return
    val tag = findViewTagInFile(project, view, layout) ?: return
    val navigatable = tag as? Navigatable ?: EditSourceUtil.getDescriptor(tag)
    navigatable?.navigate(true)
  }

  private fun findAttributeDefinition(property: InspectorPropertyItem): PsiElement? =
    when (property.source?.resourceType) {
      ResourceType.LAYOUT -> findInLayout(property)
      ResourceType.STYLE -> findInStyle(property)
      else -> null
    }

  private fun findInStyle(property: InspectorPropertyItem): PsiElement? {
    val project = property.model.layoutInspector?.layoutInspectorModel?.project ?: return null
    val source = property.source ?: return null
    val facet = findFacetWithResource(project, source) ?: return null
    // TODO: Compute the current configuration and use ResourceResolver instead:
    val resourceManager = ModuleResourceManagers.getInstance(facet).getResourceManager(source.namespace.packageName) ?: return null
    val styles = resourceManager.findValueResources(map(facet, source.namespace), ResourceType.STYLE.getName(), source.name)
    val style = styles.firstOrNull() as? Style ?: return null
    val tag = style.xmlElement as? XmlTag ?: return null
    val prefix = getNamespaceResolver(tag).uriToPrefix(property.namespace) ?: computeDefaultStylePrefix(property)
    val resourceItem = ResourceUrl.createAttrReference(prefix, property.name)
    val item = style.items.firstOrNull { it.name.value == resourceItem }
    return item?.xmlTag?.getAttribute(ATTR_NAME) ?: tag
  }

  // Resource files may not have namespace declarations. Accept "android" anyway (unless the source location is in the framework):
  private fun computeDefaultStylePrefix(property: InspectorPropertyItem) =
    if (property.namespace == ANDROID_URI && property.namespace != property.source?.namespace?.xmlNamespaceUri) ANDROID_NS_NAME else ""

  private fun findInLayout(property: InspectorPropertyItem): PsiElement? {
    val project = property.model.layoutInspector?.layoutInspectorModel?.project ?: return null
    val resource = property.source ?: return null
    val tag = findViewTagInFile(project, property.view, resource)
    return tag?.getAttribute(property.name, property.namespace)?.valueElement
  }

  private fun findViewTagInFile(project: Project, view: ViewNode, resource: ResourceReference): XmlTag? {
    val facet = findFacetWithResource(project, resource) ?: return null
    // TODO: Compute the current configuration and use ResourceResolver instead:
    // TODO: Add lookup of Android framework layouts as well:
    val resourceManager = ModuleResourceManagers.getInstance(facet).localResourceManager
    val files = resourceManager.findResourceFiles(map(facet, resource.namespace), ResourceFolderType.LAYOUT, resource.name, true, true)
    val file = files.firstOrNull() as? XmlFile ?: return null
    var tag = view.tag
    if (tag != null) {
      return tag
    }
    val locator = ViewLocator(facet, view)
    file.rootTag?.accept(locator)
    tag = locator.found ?: return null
    view.tag = tag
    return tag
  }

  /**
   * Map a [namespace] to [ResourceNamespace.RES_AUTO] if namespaces are not turned on for the [facet].
   *
   * The namespaces in resource references from the agent are using the real package names.
   * Map these into [ResourceNamespace.RES_AUTO] if the design resources are not handled
   * with namespaces.
   */
  private fun map(facet: AndroidFacet, namespace: ResourceNamespace): ResourceNamespace =
    when {
      facet.namespacing == AaptOptions.Namespacing.REQUIRED -> namespace
      namespace == ResourceNamespace.ANDROID -> ResourceNamespace.ANDROID
      else -> ResourceNamespace.RES_AUTO
    }

  private fun map(facet: AndroidFacet, reference: ResourceReference?): ResourceReference? =
    if (reference == null || facet.namespacing == AaptOptions.Namespacing.REQUIRED) reference
    else ResourceReference(map(facet, reference.namespace), reference.resourceType, reference.name)

  /**
   * Find the [AndroidFacet] with the specified [resource].
   *
   * For an Android framework resource any facet will do.
   */
  private fun findFacetWithResource(project: Project, resource: ResourceReference): AndroidFacet? {
    return ModuleManager.getInstance(project).modules
      .mapNotNull { AndroidFacet.getInstance(it) }
      .firstOrNull { it.manifest?.`package`?.value == resource.namespace.packageName || resource.namespace == ResourceNamespace.ANDROID }
  }

  /**
   * A [PsiRecursiveElementVisitor] to find a [view] in an [XmlFile].
   */
  private class ViewLocator(private val facet: AndroidFacet, private val view: ViewNode) : PsiRecursiveElementVisitor() {
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
      return map(facet, view.viewId) == ref
    }
  }
}
