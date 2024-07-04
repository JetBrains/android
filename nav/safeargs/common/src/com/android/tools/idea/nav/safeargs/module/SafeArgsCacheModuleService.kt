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

import com.android.tools.idea.nav.safeargs.SafeArgsMode
import com.android.tools.idea.nav.safeargs.psi.java.LightArgsClass
import com.android.tools.idea.nav.safeargs.psi.java.LightDirectionsClass
import com.android.tools.idea.nav.safeargs.psi.java.SafeArgsLightBaseClass
import com.intellij.openapi.Disposable
import com.intellij.openapi.module.Module
import com.intellij.openapi.util.Key
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.search.GlobalSearchScope
import net.jcip.annotations.ThreadSafe
import org.jetbrains.android.facet.AndroidFacet

/**
 * Key used to mark the [VirtualFile]s backing any light classes created in this cache, so that they
 * can be recognized elsewhere and included in the search scope when necessary.
 */
private val BACKING_FILE_MARKER: Key<Any> = Key("SAFE_ARGS_CLASS_BACKING_FILE_MARKER")

/**
 * A module service which keeps track of navigation XML file changes and generates safe args light
 * classes from them.
 *
 * This service can be thought of the central cache for safe args, upon which all other parts are
 * built on top of.
 */
@ThreadSafe
class SafeArgsCacheModuleService private constructor(module: Module) : Disposable.Default {

  /** Value to be stored with [BACKING_FILE_MARKER], unique to this module. */
  private val moduleBindingClassMarker = Any()

  /**
   * Search scope which includes any light binding classes generated in this cache for the current
   * module.
   */
  val safeArgsClassSearchScope: GlobalSearchScope =
    SafeArgsClassSearchScope(moduleBindingClassMarker)

  private class Status(val directions: List<LightDirectionsClass>, val args: List<LightArgsClass>)

  private val currentStatus by
    NavStatusCache(this, module, SafeArgsMode.JAVA) { navInfo ->
      val directions =
        navInfo.entries.flatMap { entry -> createLightDirectionsClasses(navInfo, entry) }.toList()

      val args =
        navInfo.entries.flatMap { entry -> createLightArgsClasses(navInfo, entry) }.toList()

      Status(directions, args)
    }

  val directions: List<LightDirectionsClass>
    get() = currentStatus?.directions ?: emptyList()

  val args: List<LightArgsClass>
    get() = currentStatus?.args ?: emptyList()

  private fun createLightDirectionsClasses(
    navInfo: NavInfo,
    navEntry: NavEntry,
  ): Collection<LightDirectionsClass> {
    return navEntry.data.resolvedDestinations
      .filter { destination -> destination.actions.isNotEmpty() }
      .map { destination ->
        LightDirectionsClass(navInfo, navEntry, destination).withMarkedBackingFile()
      }
      .toSet()
  }

  private fun createLightArgsClasses(
    navInfo: NavInfo,
    navEntry: NavEntry,
  ): Collection<LightArgsClass> {
    return navEntry.data.resolvedDestinations
      .filter { destination -> destination.arguments.isNotEmpty() }
      .map { destination -> LightArgsClass(navInfo, navEntry, destination).withMarkedBackingFile() }
      .toSet()
  }

  companion object {
    @JvmStatic
    fun getInstance(facet: AndroidFacet): SafeArgsCacheModuleService {
      // service registered in android plugin
      return facet.module.getService(SafeArgsCacheModuleService::class.java)!!
    }
  }

  private fun <T : SafeArgsLightBaseClass> T.withMarkedBackingFile() = apply {
    containingFile.viewProvider.virtualFile.putUserData(
      BACKING_FILE_MARKER,
      moduleBindingClassMarker,
    )
  }
}

/** Search scope which recognizes any safe args classes created with the given marker. */
private class SafeArgsClassSearchScope(private val bindingClassMarker: Any) : GlobalSearchScope() {
  override fun contains(file: VirtualFile): Boolean {
    return file.getUserData(BACKING_FILE_MARKER) === bindingClassMarker
  }

  override fun isSearchInModuleContent(aModule: Module) = true

  override fun isSearchInLibraries() = false
}
