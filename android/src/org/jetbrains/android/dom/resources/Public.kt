/*
 * Copyright (C) 2020 The Android Open Source Project
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
package org.jetbrains.android.dom.resources

import com.android.ide.common.rendering.api.ResourceNamespace
import com.android.ide.common.rendering.api.ResourceReference
import com.android.ide.common.resources.ResourceItem
import com.android.resources.ResourceType
import com.android.tools.idea.res.StudioResourceRepositoryManager
import com.android.tools.idea.res.psi.ResourceReferencePsiElement
import com.android.tools.idea.res.resourceNamespace
import com.intellij.psi.PsiElement
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.psi.xml.XmlAttribute
import com.intellij.psi.xml.XmlTag
import com.intellij.util.xml.Convert
import com.intellij.util.xml.ConvertContext
import com.intellij.util.xml.GenericAttributeValue
import com.intellij.util.xml.GenericDomValue
import com.intellij.util.xml.Required
import com.intellij.util.xml.ResolvingConverter
import com.intellij.util.xml.ResolvingConverter.StringConverter
import org.jetbrains.android.dom.AndroidDomElement
import org.jetbrains.android.facet.AndroidFacet
import java.util.stream.Collectors

/**
 * Public tag DOM for values resource files in library modules.
 */
interface PublicResource: GenericDomValue<String>, AndroidDomElement {
  @Required
  @Convert(PublicResourceNameConverter::class)
  fun getName(): GenericAttributeValue<String?>?

  @Required
  @Convert(PublicResourceTypeConverter::class)
  fun getType(): GenericAttributeValue<ResourceType>?
}

/**
 * Converter for <public\> tag name attributes.
 */
class PublicResourceNameConverter: StringConverter() {

  override fun resolve(resourceName: String?, context: ConvertContext?): PsiElement? {
    if (resourceName == null) {
      return null
    }
    val element = context?.xmlElement ?: return null
    val resourceNamespace = element.resourceNamespace ?: return null
    val tag = PsiTreeUtil.getParentOfType(element, XmlTag::class.java) ?: return null
    val xmlAttribute: XmlAttribute = tag.getAttribute("type") ?: return null
    val attributeValue = xmlAttribute.value ?: return null
    val resourceType = ResourceType.fromXmlValue(attributeValue) ?: return null
    return ResourceReferencePsiElement(element, ResourceReference(resourceNamespace, resourceType, resourceName))
  }

  override fun getVariants(context: ConvertContext?): Collection<String> {
    val element = context?.invocationElement?.parent as? PublicResource ?: return emptyList()
    val module = context.module ?: return emptyList()
    val facet = AndroidFacet.getInstance(module) ?: return emptyList()
    val elementType = element.getType()?.value
    val moduleResources = StudioResourceRepositoryManager.getInstance(facet).moduleResources
    return if (elementType == null) {
      // No type attribute set, show all resources.
      moduleResources.allResources.stream().map { obj: ResourceItem -> obj.name }.collect(Collectors.toList())
    }
    else {
      // User has already provided the type attribute, so we can filter by ResourceType.
      moduleResources.getResourceNames(ResourceNamespace.RES_AUTO, elementType)
    }
  }
}


/**
 * Converter for <public\> tag type attributes.
 */
class PublicResourceTypeConverter: ResolvingConverter<ResourceType>() {
  override fun toString(resourceType: ResourceType?, context: ConvertContext?): String? {
    return resourceType?.getName()
  }

  override fun fromString(resourceTypeValue: String?, context: ConvertContext?): ResourceType? {
    return if (resourceTypeValue == null) null else ResourceType.fromXmlValue(resourceTypeValue)
  }

  override fun getVariants(context: ConvertContext?): Collection<ResourceType> {
    return ResourceType.REFERENCEABLE_TYPES.toMutableList()
  }
}
