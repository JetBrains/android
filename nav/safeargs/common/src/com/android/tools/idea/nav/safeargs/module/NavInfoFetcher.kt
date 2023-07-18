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
import com.android.tools.idea.nav.safeargs.project.NAVIGATION_RESOURCES_CHANGED
import com.android.tools.idea.nav.safeargs.project.NavigationResourcesChangeListener
import com.android.tools.idea.nav.safeargs.psi.findNavigationVersion
import com.android.tools.idea.nav.safeargs.safeArgsMode
import com.android.tools.idea.projectsystem.PROJECT_SYSTEM_SYNC_TOPIC
import com.android.tools.idea.projectsystem.ProjectSystemSyncManager
import com.android.tools.idea.projectsystem.getModuleSystem
import com.android.tools.idea.res.StudioResourceRepositoryManager
import com.android.tools.idea.res.getSourceAsVirtualFile
import com.google.common.annotations.VisibleForTesting
import com.intellij.openapi.Disposable
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.module.Module
import com.intellij.openapi.project.DumbService
import com.intellij.openapi.util.ModificationTracker
import com.intellij.openapi.util.SimpleModificationTracker
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.PsiManager
import com.intellij.psi.xml.XmlFile
import kotlin.reflect.KProperty
import net.jcip.annotations.GuardedBy
import org.jetbrains.android.facet.AndroidFacet

/** Information about a single navigation file. */
data class NavEntry(
  /** The [AndroidFacet] for the module containing this navigation file. */
  val facet: AndroidFacet,
  /** The [ResourceItem] for the nav XML item. */
  val resource: ResourceItem,
  /** The source file of the nav XML item. */
  val file: VirtualFile,
  /** The [NavXmlData] for the nav XML item. */
  val data: NavXmlData,
) {
  val backingXmlFile: XmlFile? by lazy {
    PsiManager.getInstance(facet.module.project).findFile(file) as? XmlFile
  }
}

/** The current navigation state of a module. */
data class NavInfo(
  /** The [AndroidFacet] for the module. */
  val facet: AndroidFacet,
  /** The package name for the generated classes. */
  val packageName: String,
  /** A list of [NavEntry] objects, one for each navigation XML file. */
  val entries: List<NavEntry>,
  /** The configured Jetpack Navigation version for which this [NavInfo] is valid. */
  val navVersion: Version,
  /**
   * The modification count, from the source [NavInfoFetcher], at which this [NavInfo] was valid.
   */
  val modificationCount: Long,
)

enum class NavInfoChangeReason {
  NAVIGATION_RESOURCE_CHANGED,
  SAFE_ARGS_MODE_CHANGED,
  GRADLE_SYNC,
  DUMB_MODE_CHANGED,
}

@VisibleForTesting
interface NavInfoFetcherBase : ModificationTracker {
  val isEnabled: Boolean

  fun getCurrentNavInfo(): NavInfo?
}

/** An object that retrieves the current state of Jetpack Navigation for a module. */
class NavInfoFetcher(
  /** The [Disposable] that owns the lifecycle of this fetcher. */
  parent: Disposable,
  /** The [Module] for which to fetch navigation state. */
  private val module: Module,
  /**
   * The [SafeArgsMode] for which the navigation state is valid. [getCurrentNavInfo] will return
   * `null` for projects in other modes.
   */
  private val mode: SafeArgsMode,
  /** A callback that will be called when the result of [getCurrentNavInfo] may have changed. */
  private val onChange: (NavInfoChangeReason) -> Unit = {},
) : NavInfoFetcherBase, ModificationTracker {

  private val modificationTracker = SimpleModificationTracker()

  init {
    // Invalidate whenever the dependencies of [getCurrentNavInfo] may have changed.
    module.project.messageBus.connect(parent).apply {
      // Track changes to resource XML files.
      subscribe(
        NAVIGATION_RESOURCES_CHANGED,
        NavigationResourcesChangeListener { changedModule ->
          if (changedModule == null || changedModule == module) {
            invalidate(NavInfoChangeReason.NAVIGATION_RESOURCE_CHANGED)
          }
        },
      )
      // Track changes to module SafeArgs mode.
      subscribe(
        SafeArgsModeModuleService.MODE_CHANGED,
        SafeArgsModeModuleService.SafeArgsModeChangedListener { changedModule, _ ->
          if (changedModule == module) {
            invalidate(NavInfoChangeReason.SAFE_ARGS_MODE_CHANGED)
          }
        },
      )
      // Invalidate on project sync, in case nav version changes.
      subscribe(
        PROJECT_SYSTEM_SYNC_TOPIC,
        ProjectSystemSyncManager.SyncResultListener { invalidate(NavInfoChangeReason.GRADLE_SYNC) },
      )
      subscribe(
        DumbService.DUMB_MODE,
        object : DumbService.DumbModeListener {
          override fun enteredDumbMode() {
            invalidate(NavInfoChangeReason.DUMB_MODE_CHANGED)
          }

          override fun exitDumbMode() {
            invalidate(NavInfoChangeReason.DUMB_MODE_CHANGED)
          }
        },
      )
    }
    // Ensure listeners are loaded and sending events.
    SafeArgsModeModuleService.getInstance(module)
  }

  private val androidFacetIfEnabled: AndroidFacet?
    get() = getAndroidFacetIfSafeArgsEnabled(module, mode)

  /**
   * Whether the project is currently enabled (SafeArgs enabled for project, and [SafeArgsMode]
   * matches filter).
   */
  override val isEnabled: Boolean
    get() = isSafeArgsModule(module, mode)

  /**
   * The current modification count of the navigation resources.
   *
   * [NavInfo] objects generated by this [NavInfoFetcher] are only valid so long as their
   * [NavInfo.modificationCount] property continues to match this property.
   */
  override fun getModificationCount(): Long = modificationTracker.modificationCount

  private fun invalidate(reason: NavInfoChangeReason) {
    modificationTracker.incModificationCount()
    onChange(reason)
  }

  /**
   * Gets the [NavInfo] for the current state of the project.
   *
   * Returns `null` if Jetpack Navigation is [disabled](isEnabled) for the project, the project is
   * in the wrong [SafeArgsMode], or the project indices are not yet ready for querying.
   */
  override fun getCurrentNavInfo(): NavInfo? {
    val facet = androidFacetIfEnabled ?: return null
    val modulePackage = facet.getModuleSystem().getPackageName() ?: return null

    if (DumbService.getInstance(module.project).isDumb) {
      Logger.getInstance(this.javaClass)
        .warn(
          "Safe Args classes may be temporarily stale or unavailable due to indices not being ready right now."
        )
      return null
    }

    // Save version and modification count _before_ reading resources - in the event of a change,
    // this ensures that we don't match up the
    // current modification count with stale data.
    val navVersion = facet.findNavigationVersion()
    val modificationCount = modificationCount

    val moduleResources = StudioResourceRepositoryManager.getModuleResources(facet)
    val navResources =
      moduleResources.getResources(ResourceNamespace.RES_AUTO, ResourceType.NAVIGATION)

    val entries =
      navResources.values().mapNotNull { resource ->
        val file = resource.getSourceAsVirtualFile() ?: return@mapNotNull null
        val data = NavXmlIndex.getDataForFile(module.project, file) ?: return@mapNotNull null
        NavEntry(facet, resource, file, data)
      }

    return NavInfo(facet, modulePackage, entries, navVersion, modificationCount)
  }

  companion object {
    private fun getAndroidFacetIfSafeArgsEnabled(module: Module, requiredMode: SafeArgsMode) =
      AndroidFacet.getInstance(module)?.takeIf {
        it.isSafeArgsEnabled() && it.safeArgsMode == requiredMode
      }

    fun isSafeArgsModule(module: Module, requiredMode: SafeArgsMode) =
      getAndroidFacetIfSafeArgsEnabled(module, requiredMode) != null
  }
}

/**
 * An object that creates and caches arbitrary status objects that depend on the current state of
 * Jetpack Navigation.
 */
class NavStatusCache<TStatus : Any>
@VisibleForTesting
constructor(
  private val onCacheInvalidate: (NavInfoChangeReason) -> Unit,
  private val update: (NavInfo) -> TStatus,
  navInfoFetcherFactory: ((NavInfoChangeReason) -> Unit) -> NavInfoFetcherBase,
) {
  constructor(
    /** The [Disposable] which controls the lifetime of this cache. */
    parent: Disposable,
    /** The [Module] for which to generate status objects. */
    module: Module,
    /** The [SafeArgsMode] for which generated status objects are valid. */
    mode: SafeArgsMode,
    /**
     * Called after the generated cached value is no longer valid.
     *
     * A call to [currentStatus] after this point will call [update] to rebuild the cache.
     */
    onCacheInvalidate: (NavInfoChangeReason) -> Unit = {},
    /**
     * Generates the status corresponding to the navigation state at [navInfo].
     *
     * Will be called with a lock held, and will be called at most once per new [navInfo] generated.
     */
    update: (NavInfo) -> TStatus,
  ) : this(
    onCacheInvalidate,
    update,
    { invalidate -> NavInfoFetcher(parent, module, mode, invalidate) },
  )

  private val lock = Any()

  private val fetcher = navInfoFetcherFactory { invalidateReason ->
    // Invalidate cached status.
    synchronized(lock) { lastStatusValid = false }
    onCacheInvalidate(invalidateReason)
  }

  @GuardedBy("lock") private var lastStatus: TStatus? = null

  @GuardedBy("lock") private var lastStatusValid = false

  /**
   * Gets the current status, as generated by [update] for the current state of Jetpack Navigation.
   *
   * If [NavInfoFetcher.getCurrentNavInfo] returns `null`, returns a previously-cached result if
   * available.
   */
  val currentStatus: TStatus?
    get() =
      synchronized(lock) {
        // Don't return cached data if this entire service is no longer enabled (mode change).
        if (!fetcher.isEnabled) return null
        if (!lastStatusValid) {
          // A null from getCurrentNavInfo() here means either SafeArgs is disabled, or we're
          // in dumb mode. The former case is handled by the isEnabled check above.
          // If we're in dumb mode, stale data is the best we can do, so return it anyway.
          // We'll get a cache-invalidate event from NavInfoFetcher when we become smart, and
          // our caller will know to query us again.
          val newNavInfo = fetcher.getCurrentNavInfo() ?: return lastStatus
          val newStatus = update(newNavInfo)
          lastStatus = newStatus
          lastStatusValid = true
        }
        return lastStatus
      }

  /** A [ModificationTracker] that will update when provided cached data needs to be invalidated. */
  val modificationTracker: ModificationTracker = fetcher

  /** Convenience method to allow [NavStatusCache] to be used as a property delegate. */
  operator fun getValue(thisRef: Any?, property: KProperty<*>): TStatus? = currentStatus
}
