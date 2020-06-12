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

import com.android.tools.idea.databinding.util.DataBindingUtil
import com.android.tools.idea.databinding.util.getViewBindingEnabledTracker
import com.android.tools.idea.databinding.util.isViewBindingEnabled
import com.google.common.collect.Maps
import com.intellij.facet.ProjectFacetManager
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
 *
 * This class also serves as a [ModificationTracker] which is incremented whenever data
 * binding and/or view binding is enabled / disabled for any module in the current project.
 */
class LayoutBindingProjectComponent(val project: Project) : ModificationTracker {

  private val allBindingEnabledModules: CachedValue<List<AndroidFacet>>
  private val dataBindingEnabledModules: CachedValue<List<AndroidFacet>>
  private val viewBindingEnabledModules: CachedValue<List<AndroidFacet>>

  private val layoutBindingPsiPackages = Maps.newConcurrentMap<String, PsiPackage>()

  init {
    val cachedValuesManager = CachedValuesManager.getManager(project)
    val moduleManager = ModuleManager.getInstance(project)
    val dataBindingTracker = DataBindingUtil.getDataBindingEnabledTracker()
    val viewBindingTracker = project.getViewBindingEnabledTracker()
    val facetManager = ProjectFacetManager.getInstance(project)

    allBindingEnabledModules = cachedValuesManager.createCachedValue(
      {
        val facets = facetManager.getFacets(AndroidFacet.ID)
            .filter { facet -> DataBindingUtil.isDataBindingEnabled(facet) || facet.isViewBindingEnabled() }

        CachedValueProvider.Result.create(facets, dataBindingTracker, viewBindingTracker, moduleManager)
      }, false)

    dataBindingEnabledModules = cachedValuesManager.createCachedValue(
      {
        val facets = allBindingEnabledModules.value
          .filter { facet -> DataBindingUtil.isDataBindingEnabled(facet) }
        CachedValueProvider.Result.create(facets, dataBindingTracker, moduleManager)
      }, false)

    viewBindingEnabledModules = cachedValuesManager.createCachedValue(
      {
        val facets = allBindingEnabledModules.value
          .filter { facet -> facet.isViewBindingEnabled() }
        CachedValueProvider.Result.create(facets, viewBindingTracker, moduleManager)
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

  companion object {
    @JvmStatic
    fun getInstance(project: Project) : LayoutBindingProjectComponent = project.getService(LayoutBindingProjectComponent::class.java)
  }
}
