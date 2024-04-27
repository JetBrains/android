/*
 * Copyright (C) 2021 The Android Open Source Project
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
package com.android.tools.idea.databinding.project

import com.android.tools.idea.databinding.util.DataBindingUtil
import com.android.tools.idea.databinding.util.getViewBindingEnabledTracker
import com.android.tools.idea.databinding.util.isViewBindingEnabled
import com.android.tools.idea.projectsystem.isMainModule
import com.intellij.facet.ProjectFacetManager
import com.intellij.openapi.components.Service
import com.intellij.openapi.module.ModuleManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.ModificationTracker
import com.intellij.psi.util.CachedValue
import com.intellij.psi.util.CachedValueProvider
import com.intellij.psi.util.CachedValuesManager
import org.jetbrains.android.facet.AndroidFacet

/**
 * A cache of all facets in the current project that currently have data binding / view binding
 * enabled on them.
 *
 * This class also serves as a [ModificationTracker] which is incremented whenever data binding
 * and/or view binding is enabled / disabled for any module in the current project.
 */
@Service
class LayoutBindingEnabledFacetsProvider(val project: Project) : ModificationTracker {
  companion object {
    @JvmStatic
    fun getInstance(project: Project) =
      project.getService(LayoutBindingEnabledFacetsProvider::class.java)!!
  }

  private val allBindingEnabledModules: CachedValue<List<AndroidFacet>>
  private val dataBindingEnabledModules: CachedValue<List<AndroidFacet>>
  private val viewBindingEnabledModules: CachedValue<List<AndroidFacet>>

  private val moduleManager
    get() = ModuleManager.getInstance(project)

  private val dataBindingTracker
    get() = DataBindingUtil.getDataBindingEnabledTracker()

  private val viewBindingTracker
    get() = project.getViewBindingEnabledTracker()

  init {
    val cachedValuesManager = CachedValuesManager.getManager(project)

    allBindingEnabledModules =
      cachedValuesManager.createCachedValue(
        {
          val facets =
            ProjectFacetManager.getInstance(project).getFacets(AndroidFacet.ID).filter { facet ->
              // In addition to main module, we should be getting the unitTest and androidTest
              // modules too,
              // but due to issues within resource repository, we can't do that yet because all the
              // modules
              // are currently backed by the same set of resources. In other words, getting
              // resources from
              // the androidTest module will give us resources from the main module and vice-versa.
              facet.module.isMainModule() &&
                (DataBindingUtil.isDataBindingEnabled(facet) || facet.isViewBindingEnabled())
            }

          CachedValueProvider.Result.create(
            facets,
            dataBindingTracker,
            viewBindingTracker,
            moduleManager
          )
        },
        false
      )

    dataBindingEnabledModules =
      cachedValuesManager.createCachedValue(
        {
          val facets =
            allBindingEnabledModules.value.filter { facet ->
              DataBindingUtil.isDataBindingEnabled(facet)
            }
          CachedValueProvider.Result.create(facets, dataBindingTracker, moduleManager)
        },
        false
      )

    viewBindingEnabledModules =
      cachedValuesManager.createCachedValue(
        {
          val facets =
            allBindingEnabledModules.value.filter { facet -> facet.isViewBindingEnabled() }
          CachedValueProvider.Result.create(facets, viewBindingTracker, moduleManager)
        },
        false
      )
  }

  fun getAllBindingEnabledFacets(): List<AndroidFacet> = allBindingEnabledModules.value

  fun getDataBindingEnabledFacets(): List<AndroidFacet> = dataBindingEnabledModules.value

  fun getViewBindingEnabledFacets(): List<AndroidFacet> = viewBindingEnabledModules.value

  override fun getModificationCount() =
    dataBindingTracker.modificationCount +
      viewBindingTracker.modificationCount +
      moduleManager.modificationCount
}
