/*
 * Copyright 2000-2010 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.jetbrains.android.dom.converters

import com.android.SdkConstants
import com.android.ide.common.rendering.api.ResourceReference
import com.android.ide.common.rendering.api.StyleResourceValue
import com.android.ide.common.resources.ResourceItem
import com.android.ide.common.resources.ResourceResolver.MAX_RESOURCE_INDIRECTION
import com.android.resources.ResourceUrl
import com.android.tools.idea.res.StudioResourceRepositoryManager
import com.android.tools.idea.res.getNamespacesContext
import com.android.tools.idea.res.psi.ResourceReferencePsiElement
import com.android.tools.idea.res.resolve
import com.intellij.openapi.module.ModuleUtilCore
import com.intellij.openapi.util.Pair
import com.intellij.openapi.util.Pair.pair
import com.intellij.psi.PsiElement
import com.intellij.psi.xml.XmlTag
import com.intellij.util.xml.ConvertContext
import com.intellij.util.xml.ResolvingConverter
import org.jetbrains.android.dom.resources.Attr
import org.jetbrains.android.dom.resources.StyleItem
import org.jetbrains.android.facet.AndroidFacet
import org.jetbrains.android.resourceManagers.FrameworkResourceManager
import org.jetbrains.android.resourceManagers.ModuleResourceManagers
import org.jetbrains.annotations.NonNls
import java.util.ArrayDeque

/**
 * This converter deals with the name attribute in a [StyleItem] or [Attr] tag.
 *
 * The dom element will resolve to a ResourceReferencePsiElement which is a Fake PsiElement.
 */
class AttrNameConverter : ResolvingConverter<ResourceReference>() {

  override fun getVariants(context: ConvertContext): Collection<ResourceReference> {
    val result = arrayListOf<ResourceReference>()

    // TODO(lukeegan): When we have access to ConvertContext in createLookupElement (b/138991620), we want to prioritize variants which are
    //  in parent styles.
    val manager = FrameworkResourceManager.getInstance(context)
    if (manager != null) {
      val attrDefs = manager.attributeDefinitions
      if (attrDefs != null) {
        for (attr in attrDefs.attrs) {
          result.add(attr)
        }
      }
    }

    val facet = AndroidFacet.getInstance(context)
    if (facet != null) {
      val localResourceManager = ModuleResourceManagers.getInstance(facet).localResourceManager
      val attrDefs = localResourceManager.attributeDefinitions
      for (attr in attrDefs.attrs) {
        result.add(attr)
      }
    }
    return result
  }

  override fun resolve(resourceReference: ResourceReference?, context: ConvertContext): PsiElement? {
    if (context.xmlElement == null || resourceReference == null) {
      return null
    }
    val facet = AndroidFacet.getInstance(context) ?: return null
    val allResources = StudioResourceRepositoryManager.getInstance(facet).getResourcesForNamespace(resourceReference.namespace) ?: return null
    val hasResources = allResources.hasResources(resourceReference.namespace, resourceReference.resourceType, resourceReference.name)
    return if (hasResources) ResourceReferencePsiElement(context.xmlElement as PsiElement, resourceReference) else null
  }

  override fun isReferenceTo(element: PsiElement,
                             stringValue: String,
                             resolveResult: ResourceReference?,
                             context: ConvertContext): Boolean {
    val target = resolve(resolveResult, context)
    return element.manager.areElementsEquivalent(target, element)
  }

  /**
   * Try to find the parents of the styles where this item is defined and add to the suggestion every non-framework attribute that has been
   * used. This is helpful in themes like AppCompat where there is not only a framework attribute defined but also a custom attribute. This
   * will show both in the completion list.
   */
  private fun getAttributesUsedByParentStyle(styleTag: XmlTag): Collection<ResourceReference> {
    val module = ModuleUtilCore.findModuleForPsiElement(styleTag) ?: return emptyList()
    val appResources = StudioResourceRepositoryManager.getAppResources(module) ?: return emptyList()
    var parentStyleReference: ResourceReference? = getParentStyleFromTag(styleTag) ?: return emptyList()
    val parentStyles = appResources.getResources(parentStyleReference!!)

    val attributeNames = HashSet<ResourceReference>()
    val toExplore = ArrayDeque<Pair<ResourceItem, Int>>()
    for (parentStyle in parentStyles) {
      toExplore.push(pair(parentStyle, 0))
    }

    while (!toExplore.isEmpty()) {
      val top = toExplore.pop()
      val depth = top.second
      if (depth > MAX_RESOURCE_INDIRECTION) {
        continue // This branch in the parent graph is too deep.
      }

      val parentValue = top.first.resourceValue as StyleResourceValue?
      if (parentValue == null || parentValue.isFramework) {
        // No parent or the parent is a framework style
        continue
      }

      for (value in parentValue.definedItems) {
        if (!value.isFramework) {
          val attr = value.attr
          if (attr != null) {
            attributeNames.add(attr)
          }
        }
      }

      parentStyleReference = parentValue.parentStyle
      if (parentStyleReference != null) {
        for (parentStyle in appResources.getResources(parentStyleReference)) {
          toExplore.add(pair(parentStyle, depth + 1))
        }
      }
    }
    return attributeNames
  }

  /**
   * Finds the parent style from the passed style [XmlTag]. The parent name might be in the parent attribute or it can be part of the
   * style name. Returns null if it cannot determine the parent style.
   */
  private fun getParentStyleFromTag(styleTag: XmlTag): ResourceReference? {
    var parentName = styleTag.getAttributeValue(SdkConstants.ATTR_PARENT)
    if (parentName == null) {
      val styleName = styleTag.getAttributeValue(SdkConstants.ATTR_NAME) ?: return null
      val lastDot = styleName.lastIndexOf('.')
      if (lastDot == -1) {
        return null
      }
      parentName = styleName.substring(0, lastDot)
    }
    return ResourceUrl.parseStyleParentReference(parentName)?.resolve(styleTag)
  }

  override fun fromString(@NonNls s: String?, context: ConvertContext): ResourceReference? {
    if (s == null) {
      return null
    }
    val xmlElement = context.xmlElement ?: return null
    return ResourceUrl.parseAttrReference(s)?.resolve(xmlElement)
  }

  override fun toString(resourceReference: ResourceReference?, context: ConvertContext?): String? {
    if (resourceReference == null || context == null) {
      return null
    }
    val tag = context.tag ?: return resourceReference.resourceUrl.toString()
    val namespaceContext = getNamespacesContext(tag)
    return if (namespaceContext != null) {
      resourceReference.getRelativeResourceUrl(namespaceContext.currentNs, namespaceContext.resolver).qualifiedName
    } else {
      resourceReference.resourceUrl.toString()
    }
  }
}
