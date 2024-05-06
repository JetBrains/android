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

import com.intellij.openapi.components.PersistentStateComponent
import com.intellij.openapi.components.State
import com.intellij.openapi.components.Storage
import com.intellij.util.xmlb.XmlSerializerUtil
import com.intellij.openapi.project.Project

/**
 * Project-level service for storing user preferences for how test results are displayed in the Test Matrix.
 * AndroidTestResultsUserPreferencesManager should be used to interact with this class.
 */
@State(name="AndroidTestResultsUserPreferences", storages = [Storage("androidTestResultsUserPreferences.xml")])
class AndroidTestResultsUserPreferences private constructor(): PersistentStateComponent<AndroidTestResultsUserPreferences> {
  // Maps a hash of the current test's AndroidTestRunConfiguration and the deviceIds of selected devices to details about the user's
  // preferred state of the AndroidTestResultsTable.
  @JvmField
  var androidTestResultsTableState: HashMap<Int, AndroidTestResultsTableState> = HashMap()

  override fun getState(): AndroidTestResultsUserPreferences {
    return this
  }

  override fun loadState(state: AndroidTestResultsUserPreferences) {
    XmlSerializerUtil.copyBean(state, this)
  }

  companion object {
    @JvmStatic
    fun getInstance(project: Project): AndroidTestResultsUserPreferences {
      return project.getService(AndroidTestResultsUserPreferences::class.java)
    }
  }
}

/** Data class containing information about the user's preferences for the state of the AndroidTestResultsTable. */
data class AndroidTestResultsTableState(var preferredColumnWidths: HashMap<String, Int> = HashMap())