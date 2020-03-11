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

import com.google.common.base.CaseFormat

/**
 * An argument parameter for a navigation destination.
 *
 * A destination may have zero or more arguments. Actions may also have arguments, which act as
 * default values for their destinations.
 */
interface NavArgumentData {
  val name: String
  val type: String
}

/**
 * A navigation action, which allows the navigation framework to transition from one destination to
 * another.
 *
 * A destination may have zero or more actions.
 */
interface NavActionData {
  val id: String
  val destination: String
  val arguments: List<NavArgumentData>
}

/**
 * A useful abstraction across multiple destination types
 */
interface NavDestinationData {
  val id: String
  val name: String
  val arguments: List<NavArgumentData>
  val actions: List<NavActionData>
}

/**
 * An entry representing a fragment destination.
 */
interface NavFragmentData {
  val id: String
  val name: String
  val arguments: List<NavArgumentData>
  val actions: List<NavActionData>

  fun toDestination() = object : NavDestinationData {
    override val id: String = this@NavFragmentData.id
    override val name: String = this@NavFragmentData.name
    override val arguments: List<NavArgumentData> = this@NavFragmentData.arguments
    override val actions: List<NavActionData> = this@NavFragmentData.actions
  }
}

/**
 * A navigation is a container of destinations (fragments, nested navigations, etc.)
 *
 * Every navigation XML file has a root navigation tag.
 */
interface NavNavigationData {
  val id: String?
  val startDestination: String
  val actions: List<NavActionData>
  val fragments: List<NavFragmentData>
  val navigations: List<NavNavigationData>

  fun toDestination(): NavDestinationData? {
    val id = this.id ?: return null
    return object : NavDestinationData {
      override val id = id
      override val name = ".${CaseFormat.LOWER_UNDERSCORE.to(CaseFormat.UPPER_CAMEL, id)}"
      override val arguments = emptyList<NavArgumentData>()
      override val actions = this@NavNavigationData.actions
    }
  }

  val allFragments: List<NavFragmentData>
    get() = fragments + navigations.flatMap { nested -> nested.fragments }

  val allNavigations: List<NavNavigationData>
    get() {
      val result = mutableListOf(this)
      result.addAll(navigations.flatMap { nested -> nested.allNavigations })
      return result
    }

  val allDestinations: List<NavDestinationData>
    get() = allFragments.map { it.toDestination() } + allNavigations.mapNotNull { it.toDestination() }
}

/**
 * Data class for storing the indexed content nav XML files, useful for generating relevant
 * safe args classes.
 */
data class NavXmlData(val root: NavNavigationData)