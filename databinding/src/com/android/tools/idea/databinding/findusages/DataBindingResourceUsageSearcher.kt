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
package com.android.tools.idea.databinding.findusages

import com.android.resources.ResourceType
import com.android.tools.idea.databinding.module.LayoutBindingModuleCache
import com.android.tools.idea.databinding.util.DataBindingUtil.convertAndroidIdToJavaFieldName
import com.android.tools.idea.res.psi.ResourceReferencePsiElement
import com.android.tools.idea.res.psi.ResourceReferencePsiElement.Companion.RESOURCE_CONTEXT_ELEMENT
import com.android.tools.idea.util.androidFacet
import com.intellij.find.findUsages.CustomUsageSearcher
import com.intellij.find.findUsages.FindUsagesOptions
import com.intellij.openapi.application.runReadAction
import com.intellij.psi.PsiElement
import com.intellij.psi.search.searches.ReferencesSearch
import com.intellij.usageView.UsageInfo
import com.intellij.usages.Usage
import com.intellij.usages.UsageInfo2UsageAdapter
import com.intellij.util.Processor
import org.jetbrains.annotations.NotNull

/**
 * Adding relevant DataBinding classes and fields to usages of Android Resources.
 *
 * @see org.jetbrains.android.AndroidResourcesFindUsagesHandlerFactory
 */
class DataBindingResourceUsageSearcher : CustomUsageSearcher() {
  override fun processElementUsages(
    element: PsiElement,
    processor: Processor<in Usage>,
    options: FindUsagesOptions
  ) {
    runReadAction {
      if (element !is ResourceReferencePsiElement) return@runReadAction
      val androidFacet = element.getCopyableUserData(RESOURCE_CONTEXT_ELEMENT)?.androidFacet ?: return@runReadAction
      val bindingModuleCache = LayoutBindingModuleCache.getInstance(androidFacet)
      val groups = bindingModuleCache.bindingLayoutGroups

      when (element.resourceReference.resourceType) {
        ResourceType.LAYOUT -> {
          // When searching for usages of a layout resource, we want to add any uses of the generated DataBinding class.
          val layoutGroup =
            groups.firstOrNull { it.mainLayout.resource.name == element.resourceReference.name } ?: return@runReadAction
          val lightBindingClasses = bindingModuleCache.getLightBindingClasses(layoutGroup)
          lightBindingClasses.forEach {
            ReferencesSearch.search(it, options.searchScope).all { reference ->
              processor.process(UsageInfo2UsageAdapter(UsageInfo(reference)))
            }
          }
        }
        ResourceType.ID -> {
          // When searching for usages of an ID resource, we want to add any uses of the generated fields, in every accessible DataBinding
          // class.
          val lightClasses = groups.flatMap { group -> bindingModuleCache.getLightBindingClasses(group) }
          val javaFieldName = convertAndroidIdToJavaFieldName(element.resourceReference.name)
          val relevantFields = lightClasses.mapNotNull { bindingClass -> bindingClass.allFields.find { it.name == javaFieldName } }
          relevantFields.forEach {
            ReferencesSearch.search(it, options.searchScope).all { reference ->
              processor.process(UsageInfo2UsageAdapter(UsageInfo(reference)))
            }
          }
        }
        else -> return@runReadAction
      }
    }
  }
}