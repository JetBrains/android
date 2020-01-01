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
import com.android.tools.idea.nav.safeargs.isSafeArgsEnabled
import com.android.tools.idea.nav.safeargs.psi.LightDirectionsClass
import com.android.tools.idea.res.ResourceRepositoryManager
import com.android.tools.idea.res.getSourceAsVirtualFile
import com.android.tools.idea.nav.safeargs.index.NavXmlIndex
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
      val facet = AndroidFacet.getInstance(module)?.takeIf { it.isSafeArgsEnabled() } ?: return emptyList()

      synchronized(lock) {
        val moduleResources = ResourceRepositoryManager.getModuleResources(facet)
        val modificationCount = moduleResources.modificationCount
        if (modificationCount != lastResourcesModificationCount) {
          lastResourcesModificationCount = modificationCount

          val navResources = moduleResources.getResources(ResourceNamespace.RES_AUTO, ResourceType.NAVIGATION)
          _directions = navResources.values()
            .flatMap { resource -> createLightDirectionsClasses(facet, resource) }
            .toList()
        }
        return _directions
      }
    }

  private fun createLightDirectionsClasses(facet: AndroidFacet, resource: ResourceItem): Collection<LightDirectionsClass> {
    val modulePackage = getPackageName(facet) ?: return emptySet()
    val file = resource.getSourceAsVirtualFile() ?: return emptySet()
    val data = NavXmlIndex.getDataForFile(facet.module.project, file) ?: return emptySet()
    return data.root.allDestinations
      .map { destination -> LightDirectionsClass(facet, modulePackage, resource, destination) }
      .toSet()
  }
}
