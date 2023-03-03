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
package com.android.tools.idea.databinding.project

import com.android.tools.idea.res.StudioResourceRepositoryManager
import com.intellij.openapi.components.Service
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.ModificationTracker
import org.jetbrains.kotlin.utils.addToStdlib.sumByLong

/**
 * Modification tracker which changes if any layout resource file across the whole project changes.
 *
 * If you need to know the modification count for a single module, just use
 * `ResourceRepositoryManager.getModuleResources(facet).modificationCount` directly.
 */
@Service
class ProjectLayoutResourcesModificationTracker(project: Project): ModificationTracker {
  companion object {
    @JvmStatic
    fun getInstance(project: Project): ProjectLayoutResourcesModificationTracker =
      project.getService(ProjectLayoutResourcesModificationTracker::class.java)
  }

  private val enabledFacetsProvider = LayoutBindingEnabledFacetsProvider.getInstance(project)

  override fun getModificationCount(): Long {
    return enabledFacetsProvider.getAllBindingEnabledFacets()
      .sumByLong { facet -> StudioResourceRepositoryManager.getModuleResources(facet).modificationCount }
  }
}
