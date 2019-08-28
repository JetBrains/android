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

import com.google.common.collect.Maps
import com.intellij.openapi.module.ModuleManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.ModificationTracker
import com.intellij.psi.PsiManager
import com.intellij.psi.PsiPackage
import com.intellij.psi.impl.file.PsiPackageImpl
import com.intellij.psi.util.CachedValue
import com.intellij.psi.util.CachedValueProvider
import com.intellij.psi.util.CachedValuesManager
import org.jetbrains.android.facet.AndroidFacet

/**
 * Utilities the apply across a whole project for all binding-enabled layouts (e.g. data
 * binding and view binding)
 */
class LayoutBindingProjectComponent(val project: Project) : ModificationTracker {

  private val allBindingEnabledModules: CachedValue<List<AndroidFacet>>
  private val dataBindingEnabledModules: CachedValue<List<AndroidFacet>>
  private val viewBindingEnabledModules: CachedValue<List<AndroidFacet>>

  private val layoutBindingPsiPackages = Maps.newConcurrentMap<String, PsiPackage>()

  init {
    val cachedValuesManager = CachedValuesManager.getManager(project)
    val moduleManager = ModuleManager.getInstance(project)

    allBindingEnabledModules = cachedValuesManager.createCachedValue(
      {
        val facets = moduleManager.modules
            .mapNotNull { module -> AndroidFacet.getInstance(module) }
            .filter { facet -> DataBindingUtil.isDataBindingEnabled(facet) || facet.isViewBindingEnabled() }

        CachedValueProvider.Result.create(facets, DataBindingUtil.getDataBindingEnabledTracker(), moduleManager)
      }, false)

    dataBindingEnabledModules = cachedValuesManager.createCachedValue(
      {
        val facets = allBindingEnabledModules.value
          .filter { facet -> DataBindingUtil.isDataBindingEnabled(facet) }
        CachedValueProvider.Result.create(facets, DataBindingUtil.getDataBindingEnabledTracker(), moduleManager)
      }, false)

    viewBindingEnabledModules = cachedValuesManager.createCachedValue(
      {
        val facets = allBindingEnabledModules.value
          .filter { facet -> facet.isViewBindingEnabled() }
        CachedValueProvider.Result.create(facets, moduleManager)
      }, false)
  }

  fun getAllBindingEnabledFacets(): List<AndroidFacet> = allBindingEnabledModules.value
  fun getDataBindingEnabledFacets(): List<AndroidFacet> = dataBindingEnabledModules.value
  fun getViewBindingEnabledFacets(): List<AndroidFacet> = viewBindingEnabledModules.value

  /**
   * Returns a [PsiPackage] instance for the given package name.
   *
   * If it does not exist in the cache, a new one is created.
   *
   * @param packageName The qualified package name
   * @return A [PsiPackage] that represents the given qualified name
   */
  @Synchronized
  fun getOrCreatePsiPackage(packageName: String): PsiPackage {
    return layoutBindingPsiPackages.computeIfAbsent(packageName) {
      object : PsiPackageImpl(PsiManager.getInstance(project), packageName) {
        override fun isValid(): Boolean = true
      }
    }
  }

  override fun getModificationCount() = ModuleManager.getInstance(project).modificationCount
}
