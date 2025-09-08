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
package com.android.tools.idea.wear.dwf.dom.raw.expressions

import com.android.ide.common.rendering.api.ResourceNamespace
import com.android.ide.common.rendering.api.ResourceReference
import com.android.resources.ResourceType
import com.android.resources.ResourceVisibility
import com.android.tools.idea.res.StudioResourceRepositoryManager
import com.android.tools.idea.res.getResourceItems
import com.android.tools.idea.res.psi.ResourceRepositoryToPsiResolver
import com.android.tools.idea.util.androidFacet
import com.android.tools.idea.wear.dwf.dom.raw.removeSurroundingQuotes
import com.intellij.codeInsight.lookup.LookupElementBuilder
import com.intellij.lang.injection.InjectedLanguageManager
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiReferenceBase
import org.jetbrains.android.dom.resources.ResourceValue
import org.jetbrains.android.facet.AndroidFacet

/**
 * Represents a reference to a string resource which is used in a `<Template>`'s `<Parameter>` tag.
 *
 * @see <a
 *   href="https://developer.android.com/reference/wear-os/wff/group/part/text/formatter/template">Template</a>
 */
class TemplateParameterStringReference(private val psiElement: PsiElement) :
  PsiReferenceBase<PsiElement>(psiElement) {

  // technically the resource can be surrounded by quotes, and it will still be recognised by the
  // watch face renderer
  private val resourceReference =
    ResourceReference(
      ResourceNamespace.RES_AUTO,
      ResourceType.STRING,
      psiElement.text.removeSurroundingQuotes(),
    )

  override fun resolve(): PsiElement? {
    val facet = AndroidFacet.getInstance(psiElement) ?: return null
    return ResourceRepositoryToPsiResolver.resolveReference(resourceReference, psiElement, facet)
      .firstNotNullOfOrNull { it.element }
  }

  override fun getVariants(): Array<out Any?> {
    val withPrefix = false
    val facet =
      AndroidFacet.getInstance(psiElement)
        ?: InjectedLanguageManager.getInstance(psiElement.project)
          .getInjectionHost(psiElement)
          ?.androidFacet
        ?: return emptyArray()

    val resourceValues =
      StudioResourceRepositoryManager.getInstance(facet)
        .moduleResources
        .getResourceItems(
          // The namespace RES_AUTO as declarative watch faces can only reference project resources
          ResourceNamespace.RES_AUTO,
          ResourceType.STRING,
          ResourceVisibility.PUBLIC,
        )
        .mapNotNull { ResourceValue.reference(it, withPrefix) }

    return resourceValues.map { LookupElementBuilder.create(it.toString()) }.toTypedArray()
  }
}
