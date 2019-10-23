/*
 * Copyright (C) 2018 The Android Open Source Project
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

import com.android.tools.idea.databinding.analytics.api.DataBindingTracker
import com.android.tools.idea.gradle.project.sync.GradleSyncListener
import com.android.tools.idea.gradle.project.sync.GradleSyncState
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
import java.util.ArrayList
import java.util.concurrent.atomic.AtomicLong

/**
 * Data binding utilities the apply across a whole project
 */
class DataBindingProjectComponent(val project: Project) : ModificationTracker {
  private val dataBindingEnabledModules: CachedValue<Array<AndroidFacet>>
  private val modificationCount = AtomicLong(0)
  private val dataBindingPsiPackages = Maps.newConcurrentMap<String, PsiPackage>()
  private val dataBindingTracker = DataBindingTracker.getInstance(project)

  init {
    dataBindingEnabledModules = CachedValuesManager.getManager(project).createCachedValue({
      val modules = ModuleManager.getInstance(project).modules
      val facets = ArrayList<AndroidFacet>()
      for (module in modules) {
        val facet = AndroidFacet.getInstance(module) ?: continue
        if (DataBindingUtil.isDataBindingEnabled(facet)) {
          facets.add(facet)
        }
      }

      modificationCount.incrementAndGet()
      CachedValueProvider.Result.create(
        facets.toTypedArray(),
        DataBindingUtil.getDataBindingEnabledTracker(),
        ModuleManager.getInstance(project))
    }, false)

    GradleSyncState.subscribe(project, object : GradleSyncListener {
      override fun syncSucceeded(project: Project) {
        dataBindingTracker.trackPolledMetaData()
      }

      override fun syncFailed(project: Project, errorMessage: String) {
        dataBindingTracker.trackPolledMetaData()
      }
    })
  }

  fun hasAnyDataBindingEnabledFacet(): Boolean = getDataBindingEnabledFacets().isNotEmpty()

  fun getDataBindingEnabledFacets(): Array<AndroidFacet> = dataBindingEnabledModules.value

  override fun getModificationCount(): Long = modificationCount.toLong()

  /**
   * Returns a [PsiPackage] instance for the given package name.
   *
   * If it does not exist in the cache, a new one is created.
   *
   * @param packageName The qualified package name
   * @return A [PsiPackage] that represents the given qualified name
   */
  @Synchronized
  fun getOrCreateDataBindingPsiPackage(packageName: String): PsiPackage {
    return dataBindingPsiPackages.computeIfAbsent(packageName) {
      object : PsiPackageImpl(PsiManager.getInstance(project), packageName) {
        override fun isValid(): Boolean = true
      }
    }
  }
}
