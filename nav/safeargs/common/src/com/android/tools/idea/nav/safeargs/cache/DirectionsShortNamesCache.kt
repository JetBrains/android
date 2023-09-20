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
package com.android.tools.idea.nav.safeargs.cache

import com.android.tools.idea.nav.safeargs.module.SafeArgsCacheModuleService
import com.android.tools.idea.nav.safeargs.project.ProjectNavigationResourceModificationTracker
import com.android.tools.idea.nav.safeargs.project.SafeArgsEnabledFacetsProjectService
import com.android.tools.idea.nav.safeargs.psi.java.LightDirectionsClass
import com.android.tools.idea.nav.safeargs.safeArgsModeTracker
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiClass
import com.intellij.psi.PsiField
import com.intellij.psi.PsiMethod
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.psi.search.PsiSearchScopeUtil
import com.intellij.psi.search.PsiShortNamesCache
import com.intellij.psi.util.CachedValue
import com.intellij.psi.util.CachedValueProvider
import com.intellij.psi.util.CachedValuesManager
import com.intellij.util.Processor

/**
 * A short names cache for finding any [LightDirectionsClass] classes or their methods by their
 * unqualified name.
 */
class DirectionsShortNamesCache(project: Project) : PsiShortNamesCache() {
  private val enabledFacetsProvider = SafeArgsEnabledFacetsProjectService.getInstance(project)
  private val lightClassesCache: CachedValue<Map<String, List<LightDirectionsClass>>>

  private val allClassNamesCache: CachedValue<Array<String>>

  init {
    val cachedValuesManager = CachedValuesManager.getManager(project)

    lightClassesCache =
      cachedValuesManager.createCachedValue {
        val lightClasses =
          enabledFacetsProvider.modulesUsingSafeArgs
            .asSequence()
            .flatMap { facet ->
              SafeArgsCacheModuleService.getInstance(facet).directions.asSequence()
            }
            .groupBy { lightClass -> lightClass.name }
        CachedValueProvider.Result.create(
          lightClasses,
          ProjectNavigationResourceModificationTracker.getInstance(project),
          project.safeArgsModeTracker
        )
      }

    allClassNamesCache =
      cachedValuesManager.createCachedValue {
        CachedValueProvider.Result.create(
          lightClassesCache.value.keys.toTypedArray(),
          lightClassesCache
        )
      }
  }

  override fun getAllClassNames(): Array<String> = allClassNamesCache.value

  override fun getClassesByName(name: String, scope: GlobalSearchScope): Array<PsiClass> {
    return lightClassesCache.value[name]
      ?.asSequence()
      ?.filter { PsiSearchScopeUtil.isInScope(scope, it) }
      ?.map { it as PsiClass }
      ?.toList()
      ?.toTypedArray() ?: PsiClass.EMPTY_ARRAY
  }

  override fun getAllMethodNames() = arrayOf<String>()

  override fun getMethodsByName(name: String, scope: GlobalSearchScope) = arrayOf<PsiMethod>()

  override fun getMethodsByNameIfNotMoreThan(
    name: String,
    scope: GlobalSearchScope,
    maxCount: Int
  ): Array<PsiMethod> {
    return getMethodsByName(name, scope).take(maxCount).toTypedArray()
  }

  override fun processMethodsWithName(
    name: String,
    scope: GlobalSearchScope,
    processor: Processor<in PsiMethod>
  ): Boolean {
    // We are asked to process each method in turn, aborting if false is ever returned, and passing
    // that result back up the chain.
    return getMethodsByName(name, scope).all { method -> processor.process(method) }
  }

  override fun getAllFieldNames() = arrayOf<String>()

  override fun getFieldsByName(name: String, scope: GlobalSearchScope) = arrayOf<PsiField>()

  override fun getFieldsByNameIfNotMoreThan(
    name: String,
    scope: GlobalSearchScope,
    maxCount: Int
  ): Array<PsiField> {
    return getFieldsByName(name, scope).take(maxCount).toTypedArray()
  }
}
