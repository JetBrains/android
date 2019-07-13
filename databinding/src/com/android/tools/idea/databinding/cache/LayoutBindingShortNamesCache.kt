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

import com.android.tools.idea.databinding.DataBindingProjectComponent
import com.android.tools.idea.databinding.ModuleDataBinding
import com.android.tools.idea.databinding.psiclass.LightBindingClass
import com.android.tools.idea.res.ResourceRepositoryManager
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
import com.intellij.util.containers.HashSet
import org.jetbrains.android.facet.AndroidFacet

/**
 * Cache for classes generated from data binding layout xml files.
 *
 * See also: [LightBindingClass]
 */
class LayoutBindingShortNamesCache(project: Project) : PsiShortNamesCache() {
  private val component = project.getComponent(DataBindingProjectComponent::class.java)
  private val lightBindingCache: CachedValue<Map<String, List<LightBindingClass>>>
  private val methodsByNameCache: CachedValue<Map<String, List<PsiMethod>>>
  private val fieldsByNameCache: CachedValue<Map<String, List<PsiField>>>

  private val allClassNamesCache: CachedValue<Array<String>>
  private val allMethodNamesCache: CachedValue<Array<String>>
  private val allFieldNamesCache: CachedValue<Array<String>>

  init {
    val cachedValuesManager = CachedValuesManager.getManager(project)

    val bindingCacheProvider = LightBindingCacheProvider(component)

    lightBindingCache = cachedValuesManager.createCachedValue(bindingCacheProvider, false)

    allClassNamesCache = cachedValuesManager.createCachedValue(
      {
        CachedValueProvider.Result.create(ArrayUtil.toStringArray(lightBindingCache.value.keys), bindingCacheProvider)
      }, false)

    methodsByNameCache = cachedValuesManager.createCachedValue(
      {
        val allMethods = lightBindingCache.value.values
          .flatten()
          .flatMap { psiClass -> psiClass.methods.asIterable() }
          .groupBy { method -> method.name }

        CachedValueProvider.Result.create(allMethods, bindingCacheProvider)
      }, false)

    fieldsByNameCache = cachedValuesManager.createCachedValue(
      {
        val allFields = lightBindingCache.value.values
          .flatten()
          .flatMap { psiClass -> psiClass.fields.asIterable() }
          .groupBy { field -> field.name }

        CachedValueProvider.Result.create(allFields, bindingCacheProvider)
      }, false)

    allMethodNamesCache = cachedValuesManager.createCachedValue(
      {
        val names = methodsByNameCache.value.keys
        CachedValueProvider.Result.create(names.toTypedArray(), bindingCacheProvider)
      }, false)

    allFieldNamesCache = cachedValuesManager.createCachedValue(
      {
        val names = fieldsByNameCache.value.keys
        CachedValueProvider.Result.create(names.toTypedArray(), bindingCacheProvider)
      }, false)
  }

  override fun getClassesByName(name: String, scope: GlobalSearchScope): Array<PsiClass> {
    val bindingClasses = lightBindingCache.value[name]?.takeUnless { it.isEmpty() } ?: return PsiClass.EMPTY_ARRAY
    return bindingClasses
      .filter { psiClass -> PsiSearchScopeUtil.isInScope(scope, psiClass) }
      .toTypedArray()
  }

  override fun getAllClassNames(): Array<String> {
    return allClassNamesCache.value
  }

  // TODO(b/139458402): Override a non-deprecated method instead.
  override fun getAllClassNames(dest: HashSet<String>) {
    dest.addAll(allClassNames)
  }

  override fun getMethodsByName(name: String, scope: GlobalSearchScope): Array<PsiMethod> {
    val methods = methodsByNameCache.value[name] ?: return PsiMethod.EMPTY_ARRAY
    return methods.filter { PsiSearchScopeUtil.isInScope(scope, it) }.toTypedArray()
  }

  override fun getMethodsByNameIfNotMoreThan(name: String, scope: GlobalSearchScope, maxCount: Int): Array<PsiMethod> {
    return getMethodsByName(name, scope).take(maxCount).toTypedArray()
  }

  override fun processMethodsWithName(name: String,
                                      scope: GlobalSearchScope,
                                      processor: Processor<PsiMethod>): Boolean {
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

  // TODO(b/139458402): Override a non-deprecated method instead.
  override fun getAllMethodNames(set: HashSet<String>) {
    set.addAll(allClassNames)
  }

  override fun getFieldsByName(name: String, scope: GlobalSearchScope): Array<PsiField> {
    val fields = fieldsByNameCache.value[name] ?: return PsiField.EMPTY_ARRAY
    return fields.filter { field -> PsiSearchScopeUtil.isInScope(scope, field) }.toTypedArray()
  }

  override fun getFieldsByNameIfNotMoreThan(name: String, scope: GlobalSearchScope, maxCount: Int): Array<PsiField> {
    return getFieldsByName(name, scope).take(maxCount).toTypedArray()
  }

  override fun getAllFieldNames(): Array<String> {
    return allFieldNamesCache.value
  }

  // TODO(b/139458402): Override a non-deprecated method instead.
  override fun getAllFieldNames(set: HashSet<String>) {
    set.addAll(allFieldNames)
  }

  private class LightBindingCacheProvider(component: DataBindingProjectComponent)
    : ProjectResourceCachedValueProvider.MergedMapValueProvider<String, LightBindingClass>(component) {

    override fun createCacheProvider(facet: AndroidFacet): ResourceCacheValueProvider<Map<String, List<LightBindingClass>>> {
      return DelegateLayoutInfoCacheProvider(facet)
    }
  }

  private class DelegateLayoutInfoCacheProvider(facet: AndroidFacet)
    : ResourceCacheValueProvider<Map<String, List<LightBindingClass>>>(facet, null) {

    override fun doCompute(): Map<String, List<LightBindingClass>> {
      val moduleResources = ResourceRepositoryManager.getInstance(facet).existingModuleResources ?: return defaultValue()
      val groups = moduleResources.bindingLayoutGroups
      if (groups.isEmpty()) {
        return defaultValue()
      }
      return groups.values
        .flatMap { group -> ModuleDataBinding.getInstance(facet).getLightBindingClasses(group) }
        .groupBy { bindingClass -> bindingClass.name }
    }

    override fun defaultValue(): Map<String, List<LightBindingClass>> {
      return mapOf()
    }
  }
}
