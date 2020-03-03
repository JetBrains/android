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
import com.android.ide.common.resources.ResourceItem
import com.android.resources.ResourceType
import com.android.tools.idea.nav.safeargs.index.NavXmlData
import com.android.tools.idea.nav.safeargs.index.NavXmlIndex
import com.android.tools.idea.nav.safeargs.isSafeArgsEnabled
import com.android.tools.idea.nav.safeargs.psi.LightArgsClass
import com.android.tools.idea.nav.safeargs.psi.LightDirectionsClass
import com.android.tools.idea.res.ResourceRepositoryManager
import com.android.tools.idea.res.getSourceAsVirtualFile
import com.intellij.openapi.module.Module
import com.intellij.openapi.module.ModuleServiceManager
import net.jcip.annotations.GuardedBy
import net.jcip.annotations.ThreadSafe
import org.jetbrains.android.dom.manifest.getPackageName
import org.jetbrains.android.facet.AndroidFacet

/**
 * A module service which keeps track of navigation XML file changes and generates safe args light
 * classes from them.
 *
 * This service can be thought of the central cache for safe args, upon which all other parts are
 * built on top of.
 */
@ThreadSafe
class SafeArgsCacheModuleService private constructor(private val module: Module) {
  private class NavEntry(val resource: ResourceItem, val data: NavXmlData)

  companion object {
    @JvmStatic
    fun getInstance(facet: AndroidFacet): SafeArgsCacheModuleService {
      // service registered in android plugin
      return ModuleServiceManager.getService(facet.module, SafeArgsCacheModuleService::class.java)!!
    }
  }

  private val lock = Any()


  /**
   * A modification tracker for module resources.
   *
   * We keep track of it to know when to regenerate [LightDirectionsClass] instances, since they depend on
   * resources.
   */
  @GuardedBy("lock")
  private var lastResourcesModificationCount = Long.MIN_VALUE

  @GuardedBy("lock")
  private var _directions = emptyList<LightDirectionsClass>()
  val directions: List<LightDirectionsClass>
    get() {
      refreshSafeArgsLightClassesIfNecessary()
      return _directions
    }

  @GuardedBy("lock")
  private var _args = emptyList<LightArgsClass>()
  val args: List<LightArgsClass>
    get() {
      refreshSafeArgsLightClassesIfNecessary()
      return _args
    }

  private fun refreshSafeArgsLightClassesIfNecessary() {
    val facet = AndroidFacet.getInstance(module)?.takeIf { it.isSafeArgsEnabled() } ?: return
    val modulePackage = getPackageName(facet) ?: return

    synchronized(lock) {
      val modificationCount = NavigationResourcesModificationTracker.getInstance(module).modificationCount
      if (modificationCount != lastResourcesModificationCount) {
        lastResourcesModificationCount = modificationCount

        val moduleResources = ResourceRepositoryManager.getModuleResources(facet)
        val navResources = moduleResources.getResources(ResourceNamespace.RES_AUTO, ResourceType.NAVIGATION)

        val entries = navResources.values()
          .mapNotNull { resource ->
            val file = resource.getSourceAsVirtualFile() ?: return@mapNotNull null
            val data = NavXmlIndex.getDataForFile(facet.module.project, file) ?: return@mapNotNull null
            NavEntry(resource, data)
          }

        _directions = entries
          .flatMap { entry -> createLightDirectionsClasses(facet, modulePackage, entry) }
          .toList()

        _args = entries
          .flatMap { entry -> createLightArgsClasses(facet, modulePackage, entry) }
          .toList()
      }
    }
  }

  private fun createLightDirectionsClasses(facet: AndroidFacet, modulePackage: String, entry: NavEntry): Collection<LightDirectionsClass> {
    return entry.data.root.allDestinations
      .map { destination -> LightDirectionsClass(facet, modulePackage, entry.resource, entry.data, destination) }
      .toSet()
  }

  private fun createLightArgsClasses(facet: AndroidFacet, modulePackage: String, entry: NavEntry): Collection<LightArgsClass> {
    return entry.data.root.allFragments
      .filter { fragment -> fragment.arguments.isNotEmpty() }
      .map { fragment -> LightArgsClass(facet, modulePackage, entry.resource, fragment) }
      .toSet()
  }
}
