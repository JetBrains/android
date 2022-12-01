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
package com.android.tools.idea.uibuilder.visual

/**
 * The helper class provides the general utility functions of visualization tool (a.k.a. Validation Tool).
 */
object VisualizationUtil {

  /**
   * Helper function to get the custom configuration set for the given id.
   */
  fun getCustomConfigurationSet(id: String): CustomConfigurationSet? {
    return VisualizationToolSettings.getInstance().globalState.customConfigurationSets.getOrDefault(id, null)
  }

  /**
   * Helper function to update custom configuration set for the given id.
   */
  fun setCustomConfigurationSet(id: String, customConfigurationSet: CustomConfigurationSet?) {
    val sets = VisualizationToolSettings.getInstance().globalState.customConfigurationSets
    if (customConfigurationSet == null) {
      sets.remove(id)
    }
    else {
      sets[id] = customConfigurationSet
    }
  }

  fun getUserMadeConfigurationSets(): List<ConfigurationSet> {
    val sets = mutableListOf<ConfigurationSet>()
    val configurationSets = VisualizationToolSettings.getInstance().globalState.customConfigurationSets
    for ((id, configSet) in configurationSets) {
      if (configSet != null) {
        val customSet = UserDefinedCustom(id, configSet)
        sets.add(customSet)
      }
    }
    return sets
  }
}
