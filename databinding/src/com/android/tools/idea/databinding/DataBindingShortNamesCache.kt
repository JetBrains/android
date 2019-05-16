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
package com.android.tools.idea.databinding

import com.android.tools.idea.databinding.cache.ProjectResourceCachedValueProvider
import com.android.tools.idea.databinding.cache.ResourceCacheValueProvider
import com.android.tools.idea.databinding.psiclass.DataBindingClassFactory
import com.android.tools.idea.databinding.psiclass.LightBindingClass
import com.android.tools.idea.res.DataBindingLayoutInfo
import com.android.tools.idea.res.ResourceRepositoryManager
import com.intellij.psi.PsiClass
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiField
import com.intellij.psi.PsiMethod
import com.intellij.psi.search.GlobalSearchScope
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
 *
 * TODO(b/129543644): This class cannot change its name or package location until we remove
 *  hardcoded references to it from the Kotlin plugin.
 *  Move back to: cache.LayoutBindingShortNamesCache
 */
class DataBindingShortNamesCache(private val component: DataBindingProjectComponent) : PsiShortNamesCache() {
  private val layoutInfoCache: CachedValue<Map<String, List<DataBindingLayoutInfo>>>
  private val methodsByNameCache: CachedValue<Map<String, List<PsiMethod>>>
  private val fieldsByNameCache: CachedValue<Map<String, List<PsiField>>>

  private val allClassNamesCache: CachedValue<Array<String>>
  private val allMethodNamesCache: CachedValue<Array<String>>
  private val allFieldNamesCache: CachedValue<Array<String>>

  init {
    val project = component.project
    val cachedValuesManager = CachedValuesManager.getManager(project)

    val layoutInfoProvider = LayoutInfoCacheProvider(component)

    layoutInfoCache = cachedValuesManager.createCachedValue(layoutInfoProvider, false)

    allClassNamesCache = cachedValuesManager.createCachedValue(
      {
        CachedValueProvider.Result.create(ArrayUtil.toStringArray(layoutInfoCache.value.keys), layoutInfoProvider)
      }, false)

    methodsByNameCache = cachedValuesManager.createCachedValue(
      {
        val allMethods = layoutInfoCache.value.values
          .toPsiClasses()
          .flatMap { psiClass -> psiClass.methods.asIterable() }
          .groupBy { method -> method.name }

        CachedValueProvider.Result.create(allMethods, layoutInfoProvider)
      }, false)

    fieldsByNameCache = cachedValuesManager.createCachedValue(
      {
        val allFields = layoutInfoCache.value.values
          .toPsiClasses()
          .flatMap { psiClass -> psiClass.fields.asIterable() }
          .groupBy { field -> field.name }

        CachedValueProvider.Result.create(allFields, layoutInfoProvider)
      }, false)

    allMethodNamesCache = cachedValuesManager.createCachedValue(
      {
        val names = methodsByNameCache.value.keys
        CachedValueProvider.Result.create(names.toTypedArray(), layoutInfoProvider)
      }, false)

    allFieldNamesCache = cachedValuesManager.createCachedValue(
      {
        val names = fieldsByNameCache.value.keys
        CachedValueProvider.Result.create(names.toTypedArray(), layoutInfoProvider)
      }, false)
  }

  override fun getClassesByName(name: String, scope: GlobalSearchScope): Array<PsiClass> {
    val infoList = layoutInfoCache.value[name]?.takeUnless { it.isEmpty() } ?: return PsiClass.EMPTY_ARRAY
    return infoList
      .filter { info -> isInScope(info.psiFile, scope) }
      .map { info -> DataBindingClassFactory.getOrCreatePsiClass(info) }
      .toTypedArray()
  }

  override fun getAllClassNames(): Array<String> {
    return allClassNamesCache.value
  }

  override fun getAllClassNames(dest: HashSet<String>) {
    dest.addAll(allClassNames)
  }

  override fun getMethodsByName(name: String, scope: GlobalSearchScope): Array<PsiMethod> {
    val methods = methodsByNameCache.value[name] ?: return PsiMethod.EMPTY_ARRAY
    return methods.filter { isInScope(it, scope) }.toTypedArray()
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

  override fun getAllMethodNames(set: HashSet<String>) {
    set.addAll(allClassNames)
  }

  override fun getFieldsByName(name: String, scope: GlobalSearchScope): Array<PsiField> {
    val fields = fieldsByNameCache.value[name] ?: return PsiField.EMPTY_ARRAY
    return fields.filter { field -> isInScope(field, scope) }.toTypedArray()
  }

  override fun getFieldsByNameIfNotMoreThan(name: String, scope: GlobalSearchScope, maxCount: Int): Array<PsiField> {
    return getFieldsByName(name, scope).take(maxCount).toTypedArray()
  }

  override fun getAllFieldNames(): Array<String> {
    return allFieldNamesCache.value
  }

  override fun getAllFieldNames(set: HashSet<String>) {
    set.addAll(allFieldNames)
  }

  private fun isInScope(element: PsiElement, scope: GlobalSearchScope) =
    element.containingFile != null && scope.accept(element.containingFile.virtualFile)

  // Helper function for getting PsiClasses out of layoutInfoCache
  private fun Collection<List<DataBindingLayoutInfo>>.toPsiClasses(): List<PsiClass> {
    return this
      .flatten() // Convert list of List<DataBindingLayoutInfo> into a single list
      .map { info -> DataBindingClassFactory.getOrCreatePsiClass(info) }
  }

  private class LayoutInfoCacheProvider(component: DataBindingProjectComponent)
    : ProjectResourceCachedValueProvider.MergedMapValueProvider<String, DataBindingLayoutInfo>(component) {

    override fun createCacheProvider(facet: AndroidFacet): ResourceCacheValueProvider<Map<String, List<DataBindingLayoutInfo>>> {
      return DelegateLayoutInfoCacheProvider(facet)
    }
  }

  private class DelegateLayoutInfoCacheProvider(facet: AndroidFacet)
    : ResourceCacheValueProvider<Map<String, List<DataBindingLayoutInfo>>>(facet, null) {

    override fun doCompute(): Map<String, List<DataBindingLayoutInfo>> {
      val moduleResources = ResourceRepositoryManager.getInstance(facet).existingModuleResources ?: return defaultValue()
      val resourceFiles = moduleResources.dataBindingResourceFiles ?: return defaultValue()
      // Convert "List<Info>" to a map of "Info.className to List<Info>"
      return resourceFiles.values.groupBy { info -> info.className }
    }

    override fun defaultValue(): Map<String, List<DataBindingLayoutInfo>> {
      return mapOf()
    }
  }
}
