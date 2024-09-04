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
import com.android.ide.common.rendering.api.ResourceNamespace
import com.android.ide.common.rendering.api.ResourceReference
import com.android.resources.ResourceType
import com.android.tools.idea.res.findIdUrlsInFile
import com.android.tools.idea.res.psi.AndroidResourceToPsiResolver
import com.android.tools.idea.res.psi.ResourceRepositoryToPsiResolver
import com.android.tools.idea.util.androidFacet
import com.intellij.analysis.AnalysisBundle
import com.intellij.codeInsight.lookup.LookupElementBuilder
import com.intellij.psi.PsiElement
import com.intellij.psi.ResolveResult
import com.intellij.psi.xml.XmlAttribute
import com.intellij.psi.xml.XmlAttributeValue
import com.intellij.util.xml.ConvertContext
import com.intellij.util.xml.GenericDomValue
import com.intellij.util.xml.converters.DelimitedListConverter

/**
 * Converter that supports the id reference syntax that is unique to the {@link
 * SdkConstants.CONSTRAINT_REFERENCED_IDS} attribute.
 */
class AndroidConstraintIdsConverter : DelimitedListConverter<ResourceReference>(", ") {
  override fun convertString(string: String?, context: ConvertContext): ResourceReference? {
    if (string == null) {
      return null
    }
    return ResourceReference(ResourceNamespace.RES_AUTO, ResourceType.ID, string)
  }

  override fun toString(value: ResourceReference?): String? = value?.name

  override fun getReferenceVariants(
    context: ConvertContext,
    genericDomValue: GenericDomValue<out MutableList<ResourceReference>>?,
  ): Array<Any> {
    val file = context.file
    return findIdUrlsInFile(file)
      .stream()
      .map { url ->
        val name = (url.namespace?.let { "$it:" } ?: "") + url.name
        LookupElementBuilder.create(name)
      }
      .toArray()
  }

  override fun resolveReference(
    resourceReference: ResourceReference?,
    context: ConvertContext,
  ): PsiElement? {
    if (resourceReference == null || context.referenceXmlElement == null) {
      return null
    }
    val facet = context.module?.androidFacet ?: return null
    val resourceToPsiResolver =
      AndroidResourceToPsiResolver.getInstance() as? ResourceRepositoryToPsiResolver ?: return null
    val resolveResultList =
      resourceToPsiResolver.resolveReference(
        resourceReference,
        context.referenceXmlElement!!,
        facet,
        false,
      )
    return pickMostRelevantId(resolveResultList, context)?.element
  }

  override fun getUnresolvedMessage(value: String?): String {
    return AnalysisBundle.message("error.cannot.resolve.default.message", value)
  }

  private fun pickMostRelevantId(
    resolveResultList: Array<out ResolveResult>,
    context: ConvertContext,
  ): ResolveResult? {
    return resolveResultList
      .asSequence()
      .minWithOrNull(
        Comparator.comparing<ResolveResult, Boolean> { it.element?.containingFile != context.file }
          .thenComparing(
            Comparator.comparing {
              ((it.element as? XmlAttributeValue)?.parent as? XmlAttribute)?.name !=
                PREFIX_ANDROID + RESOURCE_CLZ_ID
            }
          )
      )
  }
}
