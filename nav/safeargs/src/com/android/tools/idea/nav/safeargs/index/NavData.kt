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
 *
 * In most cases, users will provide [type] explicitly, but if not specified, it can be
 * inferred from [defaultValue]. If neither [type] nor [defaultValue] are set, this
 * argument data is invalid.
 */
interface NavArgumentData {
  val name: String
  val type: String?
  val defaultValue: String?
  val nullable: String?
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
 * A useful abstraction across multiple destination types, e.g. <activity> and <fragment>
 */
interface NavDestinationData {
  val id: String
  val name: String
  val arguments: List<NavArgumentData>
  val actions: List<NavActionData>
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
  val activities: List<NavDestinationData>
  val dialogs: List<NavDestinationData>
  val fragments: List<NavDestinationData>
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

  private val allNavigations: List<NavNavigationData>
    get() = listOf(this) + navigations.flatMap { it.allNavigations }

  val allDestinations: List<NavDestinationData>
    get() {
      val allNavigations = allNavigations // Avoid recalculating over and over
      return allNavigations.mapNotNull { it.toDestination() } +
        allNavigations.flatMap { it.activities } +
        allNavigations.flatMap { it.dialogs } +
        allNavigations.flatMap { it.fragments }
    }
}

/**
 * Data class for storing the indexed content nav XML files, useful for generating relevant
 * safe args classes.
 */
data class NavXmlData(val root: NavNavigationData)