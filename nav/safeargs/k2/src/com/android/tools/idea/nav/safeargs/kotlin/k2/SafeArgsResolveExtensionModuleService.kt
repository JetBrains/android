/*
 * Copyright (C) 2024 The Android Open Source Project
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
package com.android.tools.idea.nav.safeargs.kotlin.k2

import com.android.SdkConstants
import com.android.tools.idea.nav.safeargs.SafeArgsMode
import com.android.tools.idea.nav.safeargs.index.NavDestinationData
import com.android.tools.idea.nav.safeargs.module.NavEntry
import com.android.tools.idea.nav.safeargs.module.NavInfo
import com.android.tools.idea.nav.safeargs.module.NavInfoChangeReason
import com.android.tools.idea.nav.safeargs.module.NavInfoFetcher
import com.android.tools.idea.nav.safeargs.module.NavStatusCache
import com.android.tools.idea.nav.safeargs.psi.xml.findXmlTagById
import com.android.tools.idea.projectsystem.FilenameConstants
import com.intellij.openapi.Disposable
import com.intellij.openapi.diagnostic.thisLogger
import com.intellij.openapi.module.Module
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.psi.search.GlobalSearchScopesCore
import com.intellij.psi.xml.XmlTag
import org.jetbrains.kotlin.analysis.api.KaExperimentalApi
import org.jetbrains.kotlin.analysis.api.platform.modification.KotlinModificationTopics
import org.jetbrains.kotlin.analysis.api.resolve.extensions.KaResolveExtension
import org.jetbrains.kotlin.analysis.api.resolve.extensions.KaResolveExtensionFile
import org.jetbrains.kotlin.idea.base.util.parentsWithSelf
import org.jetbrains.kotlin.idea.util.sourceRoots
import org.jetbrains.kotlin.name.ClassId
import org.jetbrains.kotlin.name.FqName

@OptIn(KaExperimentalApi::class)
class SafeArgsResolveExtensionModuleService(private val module: Module) :
  KaResolveExtension(), Disposable.Default {
  private data class Status(
    val args: List<ArgsClassResolveExtensionFile>,
    val directions: List<DirectionsClassResolveExtensionFile>,
  )

  private val currentStatus by
    NavStatusCache(
      parent = this,
      module = module,
      mode = SafeArgsMode.KOTLIN,
      onCacheInvalidate = ::fireInvalidateEvent,
    ) {
      logger.info("Updating status for module $module")
      Status(createArgsFiles(it), createDirectionsFiles(it))
    }

  private val args: List<ArgsClassResolveExtensionFile>
    get() = currentStatus?.args ?: emptyList()

  private val directions: List<DirectionsClassResolveExtensionFile>
    get() = currentStatus?.directions ?: emptyList()

  private val allClasses: List<KaResolveExtensionFile>
    get() = args + directions

  override fun getKtFiles(): List<KaResolveExtensionFile> = allClasses

  override fun getContainedPackages(): Set<FqName> =
    allClasses.map { it.getFilePackageName() }.toSet()

  override fun getShadowedScope(): GlobalSearchScope {
    if (currentStatus == null) {
      return GlobalSearchScope.EMPTY_SCOPE
    }

    // Filter out any source roots under "generated/source/navigation-args".
    val matchingRoots =
      module.sourceRoots.filter {
        it.hasPathComponents(
          FilenameConstants.GENERATED,
          SdkConstants.FD_SOURCE_GEN,
          FilenameConstants.SAFE_ARG_CLASS_SOURCES,
        )
      }

    return GlobalSearchScopesCore.directoriesScope(
      module.project,
      /* withSubdirectories: */ true,
      *matchingRoots.toTypedArray(),
    )
  }

  private fun fireInvalidateEvent(reason: NavInfoChangeReason) {
    if (
      reason.shouldRaiseOutOfBlockModification()
      // Only fire events if we're enabled and would be generating declarations here.
      // This prevents excessive churn if we were enabled at one point, but became
      // disabled later due to a change in module SafeArgs mode - since we're a
      // module service, we'll stick around and continue to receive callbacks from
      // NavStatusCache even if we're not currently acting as a KaResolveExtension.
      && NavInfoFetcher.isSafeArgsModule(module, SafeArgsMode.KOTLIN)
    ) {
      module.fireEvent(KotlinModificationTopics.MODULE_OUT_OF_BLOCK_MODIFICATION) { onModification(it) }
    }
  }

  private fun NavInfoChangeReason.shouldRaiseOutOfBlockModification(): Boolean =
    when (this) {
      // These events raise a module-change event in ChangeListenerProjectService.
      NavInfoChangeReason.SAFE_ARGS_MODE_CHANGED,
      NavInfoChangeReason.GRADLE_SYNC -> false
      // List out everything else explicitly, to force this file to be changed if another reason is
      // added to NavInfoChangeReason.
      NavInfoChangeReason.NAVIGATION_RESOURCE_CHANGED,
      NavInfoChangeReason.DUMB_MODE_CHANGED -> true
    }

  private fun VirtualFile.hasPathComponents(vararg names: String): Boolean {
    val namesReversed = names.reversed()
    return parentsWithSelf.map { it.name }.windowed(size = names.size).any { it == namesReversed }
  }

  private fun createArgsFiles(navInfo: NavInfo): List<ArgsClassResolveExtensionFile> =
    navInfo.entries.flatMap { navEntry ->
      navEntry.data.resolvedDestinations
        .filter { it.arguments.isNotEmpty() }
        .map { destination ->
          ArgsClassResolveExtensionFile(
            navInfo,
            destination,
            destination.resolveClassId(navInfo, "Args"),
            destination.resolveTag(navEntry),
          )
        }
    }

  private fun createDirectionsFiles(navInfo: NavInfo): List<DirectionsClassResolveExtensionFile> =
    navInfo.entries.flatMap { navEntry ->
      navEntry.data.resolvedDestinations
        .filter { it.actions.isNotEmpty() }
        .map { destination ->
          DirectionsClassResolveExtensionFile(
            navInfo,
            navEntry,
            destination,
            destination.resolveClassId(navInfo, "Directions"),
            destination.resolveTag(navEntry),
          )
        }
    }

  private fun NavDestinationData.resolveClassId(navInfo: NavInfo, suffix: String): ClassId {
    val packageNamePrefix = if (name.startsWith('.')) navInfo.packageName else ""
    return ClassId.topLevel(FqName("${packageNamePrefix}$name${suffix}"))
  }

  private fun NavDestinationData.resolveTag(navEntry: NavEntry): XmlTag? =
    navEntry.backingXmlFile?.run { findXmlTagById(id) ?: rootTag }

  companion object {
    private val logger = thisLogger()

    @JvmStatic
    fun getInstance(module: Module) =
      module.getService(SafeArgsResolveExtensionModuleService::class.java)!!
  }

  // Stub: Introduced in AS Koala Canary 8 merge
  override fun dispose() = Unit
}
