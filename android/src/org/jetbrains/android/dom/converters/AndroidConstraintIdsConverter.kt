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
package org.jetbrains.android.dom.converters

import com.android.SdkConstants.PREFIX_ANDROID
import com.android.SdkConstants.RESOURCE_CLZ_ID
import com.android.resources.ResourceType
import com.android.tools.idea.res.findIdsInFile
import com.android.tools.idea.res.psi.AndroidResourceToPsiResolver
import com.android.tools.idea.util.androidFacet
import com.intellij.codeInsight.CodeInsightBundle
import com.intellij.codeInsight.lookup.LookupElementBuilder
import com.intellij.psi.PsiElement
import com.intellij.psi.ResolveResult
import com.intellij.psi.xml.XmlAttribute
import com.intellij.psi.xml.XmlAttributeValue
import com.intellij.util.xml.ConvertContext
import com.intellij.util.xml.GenericDomValue
import com.intellij.util.xml.converters.DelimitedListConverter
import org.jetbrains.android.dom.resources.ResourceValue

/**
 * Converter that supports the id reference syntax that is unique to the {@link SdkConstants.CONSTRAINT_REFERENCED_IDS} attribute.
 */
class AndroidConstraintIdsConverter : DelimitedListConverter<ResourceValue>(", ") {
  override fun convertString(string: String?, context: ConvertContext?): ResourceValue? {
    val value = ResourceValue.reference(string, false) ?: return null
    value.setResourceType(ResourceType.ID.getName())
    return value
  }

  override fun toString(value: ResourceValue?): String? = value?.resourceName

  override fun getReferenceVariants(context: ConvertContext?,
                                    genericDomValue: GenericDomValue<out MutableList<ResourceValue>>?
  ): Array<Any> {
    val file = context?.file ?: return EMPTY_ARRAY
    return findIdsInFile(file).stream().map { LookupElementBuilder.create(it) }.toArray()
  }

  override fun resolveReference(value: ResourceValue?, context: ConvertContext?): PsiElement? {
    if (value == null || context == null || context.referenceXmlElement == null) {
      return null
    }
    val module = context.module ?: return null
    val facet = module.androidFacet ?: return null
    val resolveResultList = AndroidResourceToPsiResolver.getInstance().resolveReference(value, context.referenceXmlElement!!, facet)
    return pickMostRelevantId(resolveResultList, context)?.element
  }

  override fun getUnresolvedMessage(value: String?): String {
    return CodeInsightBundle.message("error.cannot.resolve.default.message", value)
  }

  private fun pickMostRelevantId(resolveResultList: Array<ResolveResult>, context: ConvertContext): ResolveResult? {
    return resolveResultList.asSequence().minWith(Comparator.comparing<ResolveResult, Boolean> {
      it.element?.containingFile != context.file
    }.thenComparing(
      Comparator.comparing<ResolveResult, Boolean> {
        ((it.element as? XmlAttributeValue)?.parent as? XmlAttribute)?.name != PREFIX_ANDROID + RESOURCE_CLZ_ID }))
  }
}
