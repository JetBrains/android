/*
 * Copyright (C) 2021 The Android Open Source Project
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
package com.android.tools.idea.testartifacts.instrumented.testsuite.view.state

import com.android.tools.idea.testartifacts.instrumented.AndroidTestRunConfiguration
import java.util.Objects

/**
 * Manages interactions with AndroidTestResultsUserPreferences.
 *
 * @param runConfiguration, the current test's AndroidTestRunConfiguration, used to calculate a hash value key.
 * @param deviceIds, a set of the selected devices' deviceIds, used to calculate a hash value key.
 */
class AndroidTestResultsUserPreferencesManager(private val runConfiguration: AndroidTestRunConfiguration, private val deviceIds: HashSet<String>) {
  /**
   * Gets the preferred width of a given test column, or returns the default width if there is no preference saved.
   *
   * @oaram columnName is used to identify the column.
   * @param defaultWidth is used to update the user preferences and returned when there is no preference saved.
   */
  fun getUserPreferredColumnWidth(columnName: String, defaultWidth: Int): Int {
    val androidTestResultsTableState: HashMap<Int, AndroidTestResultsTableState> = AndroidTestResultsUserPreferences.getInstance(runConfiguration.project).androidTestResultsTableState
    val key = Objects.hash(runConfiguration.TESTING_TYPE, runConfiguration.PACKAGE_NAME, runConfiguration.CLASS_NAME, runConfiguration.METHOD_NAME, deviceIds)
    return if (androidTestResultsTableState.containsKey(key)) {
      val columnPreferences: HashMap<String, Int> = androidTestResultsTableState[key]!!.preferredColumnWidths
      if (columnPreferences.containsKey(columnName)) {
        columnPreferences[columnName]!!
      } else {
        columnPreferences[columnName] = defaultWidth
        defaultWidth
      }
    } else {
      val preferredWidths = HashMap<String, Int>()
      preferredWidths[columnName] = defaultWidth
      androidTestResultsTableState[key] = AndroidTestResultsTableState(preferredWidths)
      defaultWidth
    }
  }

  /**
   * Sets the preferred width of a given test column to the provided width.
   *
   * @oaram columnName is used to identify the column.
   * @param width is the width to set the preferred width of this column to.
   */
  fun setUserPreferredColumnWidth(columnName: String, width: Int) {
    val androidTestResultsTableState: HashMap<Int, AndroidTestResultsTableState> = AndroidTestResultsUserPreferences.getInstance(runConfiguration.project).androidTestResultsTableState
    val key = Objects.hash(runConfiguration.TESTING_TYPE, runConfiguration.PACKAGE_NAME, runConfiguration.CLASS_NAME, runConfiguration.METHOD_NAME, deviceIds)
    if (androidTestResultsTableState.containsKey(key)) {
      val preferredWidths = androidTestResultsTableState[key]!!.preferredColumnWidths
      preferredWidths[columnName] = width
    } else {
      val preferredWidths: HashMap<String, Int> = HashMap()
      preferredWidths[columnName] = width
      androidTestResultsTableState[key] = AndroidTestResultsTableState(preferredWidths)
    }
  }
}