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
package com.android.tools.idea.databinding.finders

import com.android.tools.idea.AndroidPsiUtils
import com.android.tools.idea.databinding.module.LayoutBindingModuleCache
import com.android.tools.idea.databinding.project.LayoutBindingEnabledFacetsProvider
import com.android.tools.idea.databinding.psiclass.LightDataBindingComponentClass
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiClass
import com.intellij.psi.PsiElementFinder
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.psi.search.PsiSearchScopeUtil
import com.intellij.psi.util.CachedValue
import com.intellij.psi.util.CachedValueProvider
import com.intellij.psi.util.CachedValuesManager

/**
 * A finder responsible for finding all the generated DataBindingComponents in this project.
 *
 * See [LightDataBindingComponentClass]
 */
class DataBindingComponentClassFinder(project: Project) : PsiElementFinder() {
  private val enabledFacetsProvider = LayoutBindingEnabledFacetsProvider.getInstance(project)
  private val classes: CachedValue<List<PsiClass>>

  init {
    classes =
      CachedValuesManager.getManager(project)
        .createCachedValue(
          {
            val classes: List<PsiClass> =
              enabledFacetsProvider.getDataBindingEnabledFacets().mapNotNull { facet ->
                LayoutBindingModuleCache.getInstance(facet).lightDataBindingComponentClass
              }
            CachedValueProvider.Result.create(
              classes,
              enabledFacetsProvider,
              AndroidPsiUtils.getPsiModificationTrackerIgnoringXml(project),
            )
          },
          false,
        )
  }

  override fun findClass(qualifiedName: String, scope: GlobalSearchScope): PsiClass? {
    return classes.value.find { psiClass -> check(psiClass, qualifiedName, scope) }
  }

  override fun findClasses(qualifiedName: String, scope: GlobalSearchScope): Array<PsiClass> {
    return classes.value.filter { psiClass -> check(psiClass, qualifiedName, scope) }.toTypedArray()
  }

  private fun check(psiClass: PsiClass?, qualifiedName: String, scope: GlobalSearchScope): Boolean {
    return psiClass != null &&
      qualifiedName == psiClass.qualifiedName &&
      PsiSearchScopeUtil.isInScope(scope, psiClass)
  }
}
