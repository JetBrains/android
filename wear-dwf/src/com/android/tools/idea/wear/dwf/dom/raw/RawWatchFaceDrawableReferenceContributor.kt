/*
 * Copyright (C) 2025 The Android Open Source Project
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
package com.android.tools.idea.wear.dwf.dom.raw

import com.android.SdkConstants
import com.android.ide.common.rendering.api.ResourceNamespace
import com.android.resources.ResourceType
import com.android.resources.ResourceUrl
import com.android.resources.ResourceVisibility
import com.android.tools.idea.flags.StudioFlags
import com.android.tools.idea.res.StudioResourceRepositoryManager
import com.android.tools.idea.res.getResourceItems
import com.android.tools.idea.res.psi.ResourceRepositoryToPsiResolver
import com.android.tools.idea.res.resolve
import com.intellij.codeInsight.lookup.LookupElementBuilder
import com.intellij.patterns.XmlAttributeValuePattern
import com.intellij.patterns.XmlPatterns
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiReference
import com.intellij.psi.PsiReferenceBase
import com.intellij.psi.PsiReferenceContributor
import com.intellij.psi.PsiReferenceProvider
import com.intellij.psi.PsiReferenceRegistrar
import com.intellij.psi.xml.XmlAttributeValue
import com.intellij.psi.xml.XmlFile
import com.intellij.util.ProcessingContext
import org.jetbrains.android.dom.isDeclarativeWatchFaceFile
import org.jetbrains.android.dom.resources.ResourceValue
import org.jetbrains.android.facet.AndroidFacet

/**
 * A [PsiReferenceContributor] that is responsible for converting drawable resources referenced in
 * Declarative Watch Face files (in `res/raw`) into [PsiReference]s. The drawables referenced in the
 * XML file do not have a `@drawable` prefix. Furthermore, the attributes are not defined through
 * [org.jetbrains.android.dom.AndroidDomElement]s as we use an [com.intellij.xml.XmlSchemaProvider].
 *
 * Drawable resources can be referenced in the [DRAWABLE_RESOURCE_ATTRIBUTES] attributes. These
 * attributes can be used by multiple different tags.
 *
 * @see RawWatchfaceXmlSchemaProvider
 * @see <a href="https://developer.android.com/reference/wear-os/wff/watch-face?version=1">Watch
 *   Face Format reference</a>
 */
class RawWatchFaceDrawableReferenceContributor : PsiReferenceContributor() {
  override fun registerReferenceProviders(registrar: PsiReferenceRegistrar) {
    registrar.registerReferenceProvider(
      XmlPatterns.xmlAttributeValue(),
      RawWatchFaceDrawableReferenceProvider(),
    )
  }
}

private class RawWatchFaceDrawableReferenceProvider : PsiReferenceProvider() {
  override fun getReferencesByElement(
    element: PsiElement,
    context: ProcessingContext,
  ): Array<out PsiReference?> {
    if (!StudioFlags.WEAR_DECLARATIVE_WATCH_FACE_XML_EDITOR_SUPPORT.get())
      return PsiReference.EMPTY_ARRAY

    val xmlFile = element.containingFile as? XmlFile ?: return PsiReference.EMPTY_ARRAY
    if (!isDeclarativeWatchFaceFile(xmlFile)) return PsiReference.EMPTY_ARRAY

    val attributeValue = element as XmlAttributeValue
    if (attributeValue.value.isEmpty()) return PsiReference.EMPTY_ARRAY

    // Images within a Complication can reference a complication data source using []
    if (attributeValue.value.startsWith("[")) return PsiReference.EMPTY_ARRAY

    val attributeName = XmlAttributeValuePattern.getLocalName(attributeValue)
    if (attributeName !in DRAWABLE_RESOURCE_ATTRIBUTES) return PsiReference.EMPTY_ARRAY

    return arrayOf(RawWatchFaceDrawablePsiReference(attributeValue))
  }
}

private class RawWatchFaceDrawablePsiReference(private val attributeValue: XmlAttributeValue) :
  PsiReferenceBase<XmlAttributeValue>(attributeValue) {

  override fun resolve(): PsiElement? {
    val facet = AndroidFacet.getInstance(attributeValue) ?: return null

    val resourceValue =
      ResourceValue.parse(
        attributeValue.value,
        /* withLiterals */ true,
        /* withPrefix */ false,
        /* requireValid */ true,
      )
        ?: return null
    val resourceName = resourceValue.resourceName ?: return null
    val resourceType = resourceValue.type ?: ResourceType.DRAWABLE
    val resourceUrl = ResourceUrl.create(/* namespace */ null, resourceType, resourceName)
    val resourceReference = resourceUrl.resolve(attributeValue) ?: return null
    return ResourceRepositoryToPsiResolver.resolveReference(
        resourceReference,
        attributeValue,
        facet,
      )
      .firstOrNull()
      ?.element
  }

  override fun getVariants(): Array<out Any?> {
    val withPrefix = element.value.startsWith(SdkConstants.PREFIX_RESOURCE_REF)
    val resourceValues =
      StudioResourceRepositoryManager.getInstance(element)
        ?.moduleResources
        ?.getResourceItems(
          // The namespace RES_AUTO as declarative watch faces can only reference project resources
          ResourceNamespace.RES_AUTO,
          ResourceType.DRAWABLE,
          ResourceVisibility.PUBLIC,
        )
        ?.mapNotNull {
          ResourceValue.reference(if (withPrefix) "@drawable/$it" else it, withPrefix)
        } ?: emptyList()

    return resourceValues.map { LookupElementBuilder.create(it.toString()) }.toTypedArray()
  }
}
