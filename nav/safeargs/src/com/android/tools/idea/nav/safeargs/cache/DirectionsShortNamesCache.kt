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
import com.android.tools.idea.nav.safeargs.project.SafeArgsProjectComponent
import com.android.tools.idea.nav.safeargs.psi.LightDirectionsClass
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
 * A short names cache for finding any [LightDirectionsClass] classes or their methods by their unqualified
 * name.
 */
class DirectionsShortNamesCache(project: Project) : PsiShortNamesCache() {
  private val component = project.getComponent(SafeArgsProjectComponent::class.java)

  private val lightClassesCache: CachedValue<Map<String, LightDirectionsClass>>

  private val allClassNamesCache: CachedValue<Array<String>>

  init {
    val cachedValuesManager = CachedValuesManager.getManager(project)

    lightClassesCache = cachedValuesManager.createCachedValue {
      val listOfMaps = component.modulesUsingSafeArgs.map { facet ->
        val directionsCache = SafeArgsCacheModuleService.getInstance(facet)
        directionsCache.directions.associateBy { lightDirections -> lightDirections.name }
      }

      // Convert List<Map<String, ...>> to Map<String, ...> by folding down all maps into a single one
      val maps: Map<String, LightDirectionsClass> = listOfMaps.fold(mutableMapOf()) { acc, curr -> acc.putAll(curr); acc }
      CachedValueProvider.Result.create(maps, component)
    }

    allClassNamesCache = cachedValuesManager.createCachedValue {
      CachedValueProvider.Result.create(lightClassesCache.value.keys.toTypedArray(), lightClassesCache)
    }
  }


  override fun getAllClassNames(): Array<String> = allClassNamesCache.value
  override fun getClassesByName(name: String, scope: GlobalSearchScope): Array<PsiClass> {
    val matchingClass = lightClassesCache.value[name]?.takeUnless { PsiSearchScopeUtil.isInScope(scope, it) } ?: return PsiClass.EMPTY_ARRAY
    return arrayOf(matchingClass)
  }

  override fun getAllMethodNames() = arrayOf<String>()
  override fun getMethodsByName(name: String, scope: GlobalSearchScope) = arrayOf<PsiMethod>()

  override fun getMethodsByNameIfNotMoreThan(name: String, scope: GlobalSearchScope, maxCount: Int): Array<PsiMethod> {
    return getMethodsByName(name, scope).take(maxCount).toTypedArray()
  }

  override fun processMethodsWithName(name: String,
                                      scope: GlobalSearchScope,
                                      processor: Processor<PsiMethod>): Boolean {
    // We are asked to process each method in turn, aborting if false is ever returned, and passing
    // that result back up the chain.
    return getMethodsByName(name, scope).all { method -> processor.process(method) }
  }

  override fun getAllFieldNames() = arrayOf<String>()
  override fun getFieldsByName(name: String, scope: GlobalSearchScope) = arrayOf<PsiField>()

  override fun getFieldsByNameIfNotMoreThan(name: String, scope: GlobalSearchScope, maxCount: Int): Array<PsiField> {
    return getFieldsByName(name, scope).take(maxCount).toTypedArray()
  }
}
