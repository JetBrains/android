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
package com.android.tools.idea.nav.safeargs.module

import com.android.ide.common.rendering.api.ResourceNamespace
import com.android.resources.ResourceType
import com.android.tools.idea.nav.safeargs.index.NavXmlData
import com.android.tools.idea.nav.safeargs.index.NavXmlIndex
import com.android.tools.idea.res.ResourceRepositoryManager
import com.android.tools.idea.res.getSourceAsVirtualFile
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.module.Module
import com.intellij.openapi.project.DumbService
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import net.jcip.annotations.GuardedBy
import org.jetbrains.android.facet.AndroidFacet
import org.jetbrains.kotlin.progress.ProgressIndicatorAndCompilationCanceledStatus.checkCanceled

/**
 * A module service which stores the querying results from [NavXmlIndex] for the purpose of generating safe args kt
 * descriptors from them.
 */
class SafeArgsResourceForKtDescriptors(val module: Module) {
  private val LOG = Logger.getInstance(SafeArgsResourceForKtDescriptors::class.java)
  private val lock = Any()

  @GuardedBy("lock")
  private var lastTimeSeen = -1L

  class NavEntryKt(
    val project: Project,
    val file: VirtualFile,
    val data: NavXmlData
  )

  companion object {
    @JvmStatic
    fun getInstance(module: Module) = module.getService(
      SafeArgsResourceForKtDescriptors::class.java)!!
  }

  @GuardedBy("lock")
  var navResourceCache: Collection<NavEntryKt> = emptyList()

  fun getNavResource(): Collection<NavEntryKt> {
    checkCanceled()

    if (DumbService.isDumb(module.project)) {
      LOG.warn("Safe Arg classes may by temporarily stale due to indices not being ready right now.")
      return navResourceCache
    }

    synchronized(lock) {
      val now = ModuleNavigationResourcesModificationTracker.getInstance(module).modificationCount

      if (lastTimeSeen != now) {
        lastTimeSeen = now
        navResourceCache = getNavResourceFromIndex()
      }

      return navResourceCache
    }
  }

  private fun getNavResourceFromIndex(): List<NavEntryKt> {
    val facet = AndroidFacet.getInstance(module) ?: return emptyList()
    val moduleResources = ResourceRepositoryManager.getModuleResources(facet)
    val navResources = moduleResources.getResources(ResourceNamespace.RES_AUTO, ResourceType.NAVIGATION)

    return navResources.values()
      .mapNotNull { resource ->
        val file = resource.getSourceAsVirtualFile() ?: return@mapNotNull null
        val project = facet.module.project
        val data = NavXmlIndex.getDataForFile(project, file) ?: return@mapNotNull null
        NavEntryKt(project, file, data)
      }
  }
}