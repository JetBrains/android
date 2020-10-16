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
package org.jetbrains.android

import com.android.annotations.concurrency.WorkerThread
import com.android.ide.common.resources.ResourceRepository
import com.android.resources.ResourceType
import com.android.tools.idea.res.psi.AndroidResourceToPsiResolver
import com.android.tools.idea.res.psi.ResourceReferencePsiElement
import com.android.tools.idea.res.psi.ResourceReferencePsiElement.Companion.RESOURCE_CONTEXT_ELEMENT
import com.android.tools.idea.res.psi.ResourceRepositoryToPsiResolver
import com.android.tools.idea.util.androidFacet
import com.intellij.find.findUsages.FindUsagesHandler
import com.intellij.find.findUsages.FindUsagesHandlerFactory
import com.intellij.find.findUsages.FindUsagesOptions
import com.intellij.openapi.application.runReadAction
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiField
import com.intellij.usageView.UsageInfo
import com.intellij.util.Processor
import com.android.tools.idea.res.findStyleableAttrFieldsForAttr
import com.android.tools.idea.res.findStyleableAttrFieldsForStyleable

/**
 * Provides a custom [FindUsagesHandler] that understands how to search for all relevant Android Resources.
 *
 * This works by creating a [ResourceReferencePsiElement] from the element in the editor, if not a [ResourceReferencePsiElement] already.
 * XML usages are found via a ReferencesSearch on the [ResourceReferencePsiElement].
 * File usages are manually added from [ResourceRepository] as they are not found in the ReferencesSearch. This is done in
 * processElementUsages().
 *
 */
class AndroidResourcesFindUsagesHandlerFactory : FindUsagesHandlerFactory() {
  override fun canFindUsages(element: PsiElement): Boolean = ResourceReferencePsiElement.create(element) != null

  override fun createFindUsagesHandler(originalElement: PsiElement, forHighlightUsages: Boolean): FindUsagesHandler? {
    val resourceReferencePsiElement = ResourceReferencePsiElement.create(originalElement) ?: return null
    return object : FindUsagesHandler(resourceReferencePsiElement) {

      @WorkerThread
      override fun processElementUsages(element: PsiElement, processor: Processor<in UsageInfo>, options: FindUsagesOptions): Boolean {
        if (element !is ResourceReferencePsiElement) {
          return true
        }
        // When highlighting the current file, any possible resources to be highlighted will be found by the default [ReferencesSearch]
        // on the ResourceReferencePsiElement.
        if (!forHighlightUsages) {
          val resourceReference = element.resourceReference
          val contextElement = originalElement.getCopyableUserData(RESOURCE_CONTEXT_ELEMENT)
          if (contextElement != null) {
            // Reduce the scope of the ReferencesSearch to prevent collecting resources of the same reference from unrelated modules.
            runReadAction {
              val reducedScope = ResourceRepositoryToPsiResolver.getResourceSearchScope(resourceReference, contextElement)
              options.searchScope = options.searchScope.intersectWith(reducedScope)
            }
          }

          // Add any file based resources not found in a ReferencesSearch
          if (contextElement != null) {
            runReadAction {
              AndroidResourceToPsiResolver.getInstance()
                .getGotoDeclarationFileBasedTargets(resourceReference, contextElement)
                .forEach { processor.process(UsageInfo(it, false)) }
            }
          }

          // For Attr and Styleable resources, add any StyleableAttr fields that would not be found in ReferencesSearch
          val androidFacet = contextElement?.androidFacet
          if (androidFacet != null) {
            runReadAction {
              when (resourceReference.resourceType) {
                ResourceType.ATTR -> findStyleableAttrFieldsForAttr(androidFacet, resourceReference.name)
                ResourceType.STYLEABLE -> findStyleableAttrFieldsForStyleable(androidFacet, resourceReference.name)
                else -> PsiField.EMPTY_ARRAY
              }.forEach { super.processElementUsages(it, processor, options) }
            }
          }
        }
        return super.processElementUsages(element, processor, options)
      }
    }
  }
}