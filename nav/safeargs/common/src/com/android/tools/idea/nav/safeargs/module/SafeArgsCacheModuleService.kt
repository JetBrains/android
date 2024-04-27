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
import com.intellij.openapi.Disposable
import com.intellij.openapi.module.Module
import net.jcip.annotations.ThreadSafe
import org.jetbrains.android.facet.AndroidFacet

/**
 * A module service which keeps track of navigation XML file changes and generates safe args light
 * classes from them.
 *
 * This service can be thought of the central cache for safe args, upon which all other parts are
 * built on top of.
 */
@ThreadSafe
class SafeArgsCacheModuleService private constructor(module: Module) : Disposable.Default {
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
    navEntry: NavEntry
  ): Collection<LightDirectionsClass> {
    return navEntry.data.resolvedDestinations
      .filter { destination -> destination.actions.isNotEmpty() }
      .map { destination -> LightDirectionsClass(navInfo, navEntry, destination) }
      .toSet()
  }

  private fun createLightArgsClasses(
    navInfo: NavInfo,
    navEntry: NavEntry
  ): Collection<LightArgsClass> {
    return navEntry.data.resolvedDestinations
      .filter { destination -> destination.arguments.isNotEmpty() }
      .map { destination -> LightArgsClass(navInfo, navEntry, destination) }
      .toSet()
  }

  companion object {
    @JvmStatic
    fun getInstance(facet: AndroidFacet): SafeArgsCacheModuleService {
      // service registered in android plugin
      return facet.module.getService(SafeArgsCacheModuleService::class.java)!!
    }
  }
}
