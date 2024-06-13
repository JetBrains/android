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

import com.android.tools.idea.databinding.index.BindingXmlIndexModificationTracker
import com.android.tools.idea.res.StudioResourceRepositoryManager
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.ModificationTracker

/**
 * Modification tracker which changes if any layout resource file across the whole project changes.
 *
 * If you need to know the modification count for a single module, just use
 * `ResourceRepositoryManager.getModuleResources(facet).modificationCount` directly.
 */
@Service(Service.Level.PROJECT)
class ProjectLayoutResourcesModificationTracker(private val project: Project) :
  ModificationTracker {
  companion object {
    @JvmStatic
    fun getInstance(project: Project): ProjectLayoutResourcesModificationTracker = project.service()
  }

  override fun getModificationCount(): Long {
    // Note: LocalResourceRepository and BindingXmlIndex are updated at different times,
    // so we must incorporate both into the modification count (see b/283753328).
    val resourceModificationCount: Long =
      LayoutBindingEnabledFacetsProvider.getInstance(project).getAllBindingEnabledFacets().sumOf {
        facet ->
        StudioResourceRepositoryManager.getModuleResources(facet).modificationCount
      }
    val bindingIndexModificationCount =
      BindingXmlIndexModificationTracker.getInstance(project).modificationCount
    return resourceModificationCount + bindingIndexModificationCount
  }
}
