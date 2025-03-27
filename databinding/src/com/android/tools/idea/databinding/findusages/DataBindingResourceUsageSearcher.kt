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
import com.intellij.psi.util.parentOfType
import com.intellij.psi.xml.XmlTag
import com.intellij.usageView.UsageInfo
import com.intellij.usages.Usage
import com.intellij.usages.UsageInfo2UsageAdapter
import com.intellij.util.Processor

/**
 * Adding relevant DataBinding classes and fields to usages of Android Resources.
 *
 * @see org.jetbrains.android.AndroidResourcesFindUsagesHandlerFactory
 */
class DataBindingResourceUsageSearcher : CustomUsageSearcher() {
  override fun processElementUsages(
    element: PsiElement,
    processor: Processor<in Usage>,
    options: FindUsagesOptions,
  ) {
    runReadAction {
      if (element !is ResourceReferencePsiElement) return@runReadAction
      val androidFacet =
        element.getCopyableUserData(RESOURCE_CONTEXT_ELEMENT)?.androidFacet ?: return@runReadAction
      val bindingModuleCache = LayoutBindingModuleCache.getInstance(androidFacet)

      when (element.resourceReference.resourceType) {
        ResourceType.LAYOUT -> {
          // When searching for usages of a layout resource, we want to add any uses of the
          // generated DataBinding class.
          val lightBindingClasses =
            bindingModuleCache.getLightBindingClasses { layoutGroup ->
              layoutGroup.mainLayout.resource.name == element.resourceReference.name
            }
          lightBindingClasses.forEach {
            // We don't care about the result of `allMatch`, but are rather using the API to ensure
            // that we stop processing results as soon as `processor.process` results false.
            @Suppress("Noop")
            ReferencesSearch.search(it, options.searchScope).allMatch { reference ->
              processor.process(UsageInfo2UsageAdapter(UsageInfo(reference)))
            }
          }
        }
        ResourceType.ID -> {
          // When searching for usages of an ID resource, we want to add any uses of the generated
          // fields, in every accessible DataBinding
          // class.
          val javaFieldName = convertAndroidIdToJavaFieldName(element.resourceReference.name)
          val definingXmlTag = element.parentOfType<XmlTag>()
          val relevantFields =
            bindingModuleCache
              .getLightBindingClasses()
              .mapNotNull { bindingClass ->
                bindingClass.allFields.find { it.name == javaFieldName }
              }
              .filter {
                // It's possible for the same ID to be used in two different views within the same
                // group. Most of the time we want to
                // include all references in find usages. But here when looking for view binding
                // usages, we only want to return the light
                // class corresponding to this specific view. Check that the defining XmlTag for
                // this light class is the same as the
                // containing tag for this ID.
                // If we couldn't find the defining tag above, go ahead and include all results (err
                // on the side of more info).
                definingXmlTag == null || it.navigationElement as? XmlTag == definingXmlTag
              }

          relevantFields.forEach {
            // We don't care about the result of `allMatch`, but are rather using the API to ensure
            // that we stop processing results as soon as `processor.process` results false.
            @Suppress("Noop")
            ReferencesSearch.search(it, options.searchScope).allMatch { reference ->
              processor.process(UsageInfo2UsageAdapter(UsageInfo(reference)))
            }
          }
        }
        else -> return@runReadAction
      }
    }
  }
}
