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
package com.android.tools.idea.databinding.cache

import com.android.tools.idea.databinding.module.LayoutBindingModuleCache
import com.android.tools.idea.databinding.project.LayoutBindingEnabledFacetsProvider
import com.android.tools.idea.databinding.project.ProjectLayoutResourcesModificationTracker
import com.android.tools.idea.databinding.psiclass.LightBindingClass
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
import com.intellij.util.ArrayUtil
import com.intellij.util.Processor

/**
 * Cache for classes generated from data binding layout xml files.
 *
 * See also: [LightBindingClass]
 */
class LayoutBindingShortNamesCache(project: Project) : PsiShortNamesCache() {
  private val enabledFacetsProvider = LayoutBindingEnabledFacetsProvider.getInstance(project)
  private val lightBindingCache: CachedValue<Map<String, List<LightBindingClass>>>
  private val methodsByNameCache: CachedValue<Map<String, List<PsiMethod>>>
  private val fieldsByNameCache: CachedValue<Map<String, List<PsiField>>>

  private val allClassNamesCache: CachedValue<Array<String>>
  private val allMethodNamesCache: CachedValue<Array<String>>
  private val allFieldNamesCache: CachedValue<Array<String>>

  init {
    val cachedValuesManager = CachedValuesManager.getManager(project)
    val resourcesModifiedTracker = ProjectLayoutResourcesModificationTracker.getInstance(project)

    lightBindingCache =
      cachedValuesManager.createCachedValue {
        val allBindingClasses =
          enabledFacetsProvider.getAllBindingEnabledFacets().flatMap { facet ->
            val bindingModuleCache = LayoutBindingModuleCache.getInstance(facet)
            val groups = bindingModuleCache.bindingLayoutGroups
            groups.flatMap { group -> bindingModuleCache.getLightBindingClasses(group) }
          }

        val groupedClasses = allBindingClasses.groupBy { it.name }.toMutableMap()
        for (suffix in listOf("Binding", "BindingImpl")) {
          allBindingClasses
            .filter { bindingClass -> bindingClass.name.endsWith(suffix) }
            .takeIf { matches -> matches.isNotEmpty() }
            ?.let { matches -> groupedClasses[suffix] = matches }
        }

        CachedValueProvider.Result.create(
          groupedClasses as Map<String, List<LightBindingClass>>,
          enabledFacetsProvider,
          resourcesModifiedTracker
        )
      }

    allClassNamesCache =
      cachedValuesManager.createCachedValue {
        CachedValueProvider.Result.create(
          ArrayUtil.toStringArray(lightBindingCache.value.keys),
          enabledFacetsProvider,
          resourcesModifiedTracker
        )
      }

    methodsByNameCache =
      cachedValuesManager.createCachedValue {
        val allMethods =
          lightBindingCache.value.values
            .flatten()
            .flatMap { psiClass -> psiClass.methods.asIterable() }
            .groupBy { method -> method.name }

        CachedValueProvider.Result.create(
          allMethods,
          enabledFacetsProvider,
          resourcesModifiedTracker
        )
      }

    fieldsByNameCache =
      cachedValuesManager.createCachedValue {
        val allFields =
          lightBindingCache.value.values
            .flatten()
            .flatMap { psiClass -> psiClass.fields.asIterable() }
            .groupBy { field -> field.name }

        CachedValueProvider.Result.create(
          allFields,
          enabledFacetsProvider,
          resourcesModifiedTracker
        )
      }

    allMethodNamesCache =
      cachedValuesManager.createCachedValue {
        val names = methodsByNameCache.value.keys
        CachedValueProvider.Result.create(
          names.toTypedArray(),
          enabledFacetsProvider,
          resourcesModifiedTracker
        )
      }

    allFieldNamesCache =
      cachedValuesManager.createCachedValue {
        val names = fieldsByNameCache.value.keys
        CachedValueProvider.Result.create(
          names.toTypedArray(),
          enabledFacetsProvider,
          resourcesModifiedTracker
        )
      }
  }

  override fun getClassesByName(name: String, scope: GlobalSearchScope): Array<PsiClass> {
    val bindingClasses =
      lightBindingCache.value[name]?.takeUnless { it.isEmpty() } ?: return PsiClass.EMPTY_ARRAY
    return bindingClasses
      .filter { psiClass -> PsiSearchScopeUtil.isInScope(scope, psiClass) }
      .toTypedArray()
  }

  override fun getAllClassNames(): Array<String> {
    return allClassNamesCache.value
  }

  override fun getMethodsByName(name: String, scope: GlobalSearchScope): Array<PsiMethod> {
    val methods = methodsByNameCache.value[name] ?: return PsiMethod.EMPTY_ARRAY
    return methods.filter { PsiSearchScopeUtil.isInScope(scope, it) }.toTypedArray()
  }

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
    for (method in getMethodsByName(name, scope)) {
      if (!processor.process(method)) {
        return false
      }
    }
    return true
  }

  override fun getAllMethodNames(): Array<String> {
    return allMethodNamesCache.value
  }

  override fun getFieldsByName(name: String, scope: GlobalSearchScope): Array<PsiField> {
    val fields = fieldsByNameCache.value[name] ?: return PsiField.EMPTY_ARRAY
    return fields.filter { field -> PsiSearchScopeUtil.isInScope(scope, field) }.toTypedArray()
  }

  override fun getFieldsByNameIfNotMoreThan(
    name: String,
    scope: GlobalSearchScope,
    maxCount: Int
  ): Array<PsiField> {
    return getFieldsByName(name, scope).take(maxCount).toTypedArray()
  }

  override fun getAllFieldNames(): Array<String> {
    return allFieldNamesCache.value
  }
}
