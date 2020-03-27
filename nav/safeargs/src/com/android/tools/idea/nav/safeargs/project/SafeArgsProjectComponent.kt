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
package com.android.tools.idea.nav.safeargs.project

import com.android.tools.idea.nav.safeargs.isSafeArgsEnabled
import com.android.tools.idea.nav.safeargs.safeArgsModeTracker
import com.intellij.openapi.module.ModuleManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.ModificationTracker
import com.intellij.psi.util.CachedValue
import com.intellij.psi.util.CachedValueProvider
import com.intellij.psi.util.CachedValuesManager
import org.jetbrains.android.facet.AndroidFacet

/**
 * Project-wide properties / utilities for safe args.
 */
class SafeArgsProjectComponent(val project: Project) : ModificationTracker {
  private val modulesUsingSafeArgsCache: CachedValue<List<AndroidFacet>>
  val modulesUsingSafeArgs: List<AndroidFacet>
    get() = modulesUsingSafeArgsCache.value

  init {
    val cachedValuesManager = CachedValuesManager.getManager(project)
    val moduleManager = ModuleManager.getInstance(project)

    modulesUsingSafeArgsCache = cachedValuesManager.createCachedValue(
      {
        val facets = moduleManager.modules
            .mapNotNull { module -> AndroidFacet.getInstance(module) }
            .filter { facet -> facet.isSafeArgsEnabled() }

        CachedValueProvider.Result.create(facets, this)
      }, false)
  }

  override fun getModificationCount() = ModuleManager.getInstance(project).modificationCount + project.safeArgsModeTracker.modificationCount
}
