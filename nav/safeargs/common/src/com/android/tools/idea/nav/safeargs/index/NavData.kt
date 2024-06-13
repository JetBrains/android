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
package com.android.tools.idea.nav.safeargs.index

import com.android.tools.idea.nav.safeargs.psi.java.toUpperCamelCase
import com.intellij.util.containers.addIfNotNull

/**
 * An argument parameter for a navigation destination.
 *
 * A destination may have zero or more arguments. Actions may also have arguments, which act as
 * default values for their destinations.
 *
 * In most cases, users will provide [type] explicitly, but if not specified, it can be inferred
 * from [defaultValue]. If neither [type] nor [defaultValue] are set, this argument data is invalid.
 */
interface NavArgumentData {
  val name: String
  val type: String?
  val defaultValue: String?
  val nullable: String?

  fun isNonNull(): Boolean {
    return nullable != "true" && defaultValue != "@null"
  }
}

/**
 * A navigation action, which allows the navigation framework to transition from one destination to
 * another.
 *
 * A destination may have zero or more actions.
 *
 * An action itself should point to a single target destination, which is usually set by
 * `destination`, but could also just be set by `popUpTo` (which essentially means the user wants to
 * navigate backwards to a destination that's in the back stack)
 */
interface NavActionData {
  val id: String
  val destination: String?
  val popUpTo: String?
  val arguments: List<NavArgumentData>

  fun resolveDestination(): String? {
    return destination ?: popUpTo
  }
}

/** A useful abstraction across multiple destination types, e.g. <activity> and <fragment> */
interface NavDestinationData {
  val id: String
  val name: String
  val arguments: List<NavArgumentData>
  val actions: List<NavActionData>
}

interface MaybeNavDestinationData {
  fun toDestination(): NavDestinationData?
}

/**
 * A navigation is a container of destinations (fragments, nested navigations, etc.)
 *
 * Every navigation XML file has a root navigation tag.
 */
interface NavNavigationData : MaybeNavDestinationData {
  val id: String?
  val startDestination: String
  val actions: List<NavActionData>
  val arguments: List<NavArgumentData>
  val navigations: List<NavNavigationData>

  /**
   * We can't predict all possible tag types, because navigation allows custom tags. Instead, we
   * catch all remaining tags and assume they are destinations, but even if they aren't, we will
   * still successfully complete parsing (and callers can strip out invalid destinations later by
   * checking if [MaybeNavDestinationData.toDestination] returns null.
   */
  val potentialDestinations: List<MaybeNavDestinationData>

  override fun toDestination(): NavDestinationData? {
    val id = this.id ?: return null
    return object : NavDestinationData {
      override val id = id
      override val name =
        ".${id.toUpperCamelCase()}" // The prefix '.' means this class should be scoped in the
      // current module
      override val arguments = this@NavNavigationData.arguments
      override val actions = this@NavNavigationData.actions
    }
  }
}

/**
 * Data class for storing the indexed content nav XML files, useful for generating relevant safe
 * args classes.
 */
data class NavXmlData(val root: NavNavigationData) {
  /**
   * Returns a list of all destinations with global actions updated.
   * (https://developer.android.com/guide/navigation/navigation-global-action)
   *
   * Global actions are collected along the path while traversing, and duplicates are resolved like
   * actions overrides.
   */
  val resolvedDestinations: List<NavDestinationData> by lazy {
    root.traverse(emptyList(), mutableListOf())
  }

  private fun NavNavigationData.traverse(
    globalActions: List<NavActionData>,
    allDestinations: MutableList<NavDestinationData>,
  ): List<NavDestinationData> {
    allDestinations.addIfNotNull(this.toDestination()?.withGlobalActions(globalActions))

    val newGlobalActions = (this.actions + globalActions).distinctBy { it.id }
    this.potentialDestinations
      .mapNotNull { it.toDestination()?.withGlobalActions(newGlobalActions) }
      .let { allDestinations.addAll(it) }

    this.navigations.map { it.traverse(newGlobalActions, allDestinations) }

    return allDestinations
  }

  private fun NavDestinationData.withGlobalActions(
    globalActions: List<NavActionData>
  ): NavDestinationData {
    if (globalActions.isEmpty()) return this

    return object : NavDestinationData by this {
      override val actions = (this@withGlobalActions.actions + globalActions).distinctBy { it.id }
    }
  }
}
