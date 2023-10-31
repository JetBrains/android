/*
 * Copyright (C) 2023 The Android Open Source Project
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

import com.android.ide.common.gradle.Version
import com.android.ide.common.rendering.api.ResourceNamespace
import com.android.ide.common.resources.ResourceItem
import com.android.resources.ResourceType
import com.android.tools.idea.nav.safeargs.SafeArgsMode
import com.android.tools.idea.nav.safeargs.index.NavXmlData
import com.android.tools.idea.nav.safeargs.index.NavXmlIndex
import com.android.tools.idea.nav.safeargs.isSafeArgsEnabled
import com.android.tools.idea.nav.safeargs.psi.GRADLE_VERSION_ZERO
import com.android.tools.idea.nav.safeargs.psi.findNavigationVersion
import com.android.tools.idea.nav.safeargs.safeArgsMode
import com.android.tools.idea.projectsystem.getModuleSystem
import com.android.tools.idea.res.StudioResourceRepositoryManager
import com.android.tools.idea.res.getSourceAsVirtualFile
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.module.Module
import com.intellij.openapi.project.DumbService
import com.intellij.openapi.vfs.VirtualFile
import net.jcip.annotations.GuardedBy
import org.jetbrains.android.facet.AndroidFacet
import kotlin.reflect.KProperty

/** Information about a single navigation file. */
data class NavEntry(
  /** The [ResourceItem] for the nav XML item. */
  val resource: ResourceItem,
  /** The source file of the nav XML item. */
  val file: VirtualFile,
  /** The [NavXmlData] for the nav XML item. */
  val data: NavXmlData,
)

/** The current navigation state of the project. */
data class NavInfo(
  /** The [AndroidFacet] for the project. */
  val facet: AndroidFacet,
  /** The package name for the generated classes. */
  val packageName: String,
  /** A list of [NavEntry] objects, one for each navigation XML file. */
  val entries: List<NavEntry>,
  /** The configured Jetpack Navigation version for which this [NavInfo] is valid. */
  val navVersion: Version,
  /** The modification count (from [ModuleNavigationResourcesModificationTracker]) at which this [NavInfo] was valid. */
  val modificationCount: Long,
)

/** An object that retrieves the current state of Jetpack Navigation for a module. */
class NavInfoFetcher(
  /** The [Module] for which to fetch navigation state. */
  private val module: Module,
  /** The [SafeArgsMode] for which the navigation state is valid. [getCurrentNavInfo] will return `null` for projects in other modes. */
  private val mode: SafeArgsMode) {

  private val androidFacetIfEnabled: AndroidFacet?
    get() = AndroidFacet.getInstance(module)?.takeIf { it.isSafeArgsEnabled() && it.safeArgsMode == mode }

  /** Whether the project is currently enabled (SafeArgs enabled for project, and [SafeArgsMode] matches filter). */
  val isEnabled: Boolean
    get() = androidFacetIfEnabled != null

  /**
   * The current modification count of the navigation resources.
   *
   * Generated [NavInfo] objects are only valid if their `modificationCount` property matches this property.
   */
  val modificationCount: Long
    get() = ModuleNavigationResourcesModificationTracker.getInstance(module).modificationCount

  /**
   * The current configured Jetpack Navigation version.
   *
   * Generated [NavInfo] objects are only valid if their `navVersion` property matches this property.
   */
  val navVersion: Version
    get() = androidFacetIfEnabled?.findNavigationVersion() ?: GRADLE_VERSION_ZERO

  /**
   * Gets the [NavInfo] for the current state of the project.
   *
   * Returns `null` if Jetpack Navigation is [disabled](isEnabled) for the project, the project is in the wrong [SafeArgsMode], or the
   * project indices are not yet ready for querying.
   */
  fun getCurrentNavInfo(): NavInfo? {
    val facet = androidFacetIfEnabled ?: return null
    val modulePackage = facet.getModuleSystem().getPackageName() ?: return null

    if (DumbService.getInstance(module.project).isDumb) {
      Logger.getInstance(this.javaClass)
        .warn("Safe Args classes may be temporarily stale or unavailable due to indices not being ready right now.")
      return null
    }

    // Save version and modification count _before_ reading resources - in the event of a change, this ensures that we don't match up the
    // current modification count with stale data.
    val navVersion = navVersion
    val modificationCount = modificationCount

    val moduleResources = StudioResourceRepositoryManager.getModuleResources(facet)
    val navResources = moduleResources.getResources(ResourceNamespace.RES_AUTO, ResourceType.NAVIGATION)

    val entries = navResources.values().mapNotNull { resource ->
      val file = resource.getSourceAsVirtualFile() ?: return@mapNotNull null
      val data = NavXmlIndex.getDataForFile(module.project, file) ?: return@mapNotNull null
      NavEntry(resource, file, data)
    }

    return NavInfo(facet, modulePackage, entries, navVersion, modificationCount)
  }
}

/** An object that creates and caches arbitrary status objects that depend on the current state of Jetpack Navigation. */
class NavStatusCache<TStatus : Any>(
  /** The [Module] for which to generate status objects. */
  module: Module,
  /** The [SafeArgsMode] for which generated status objects are valid. */
  private val mode: SafeArgsMode,
  /**
   * Generates the status corresponding to the navigation state at [navInfo].
   *
   * Will be called with a lock held, and will be called at most once per new [navInfo] generated.
   */
  private val update: (NavInfo) -> TStatus) {

  private val fetcher = NavInfoFetcher(module, mode)
  private val lock = Any()

  @GuardedBy("lock")
  private var lastNavInfo: NavInfo? = null

  @GuardedBy("lock")
  private var lastStatus: TStatus? = null

  /**
   * Gets the current status, as generated by [update] for the current state of Jetpack Navigation.
   *
   * If [NavInfoFetcher.getCurrentNavInfo] returns `null`, returns a previously-cached result if available.
   */
  val currentStatus: TStatus?
    get() = synchronized(lock) {
      // Don't return cached data if this entire service is no longer enabled (mode change).
      if (!fetcher.isEnabled) return null
      if (!lastNavInfo.isValid) {
        val newNavInfo = fetcher.getCurrentNavInfo() ?: return lastStatus
        val newStatus = update(newNavInfo)
        lastNavInfo = newNavInfo
        lastStatus = newStatus
      }
      return lastStatus
    }

  /** Convenience method to allow [NavStatusCache] to be used as a property delegate. */
  operator fun getValue(thisRef: Any?, property: KProperty<*>): TStatus? = currentStatus

  private val NavInfo?.isValid: Boolean
    get() = (this != null &&
             mode == facet.safeArgsMode &&
             navVersion == fetcher.navVersion &&
             modificationCount == fetcher.modificationCount)
}
