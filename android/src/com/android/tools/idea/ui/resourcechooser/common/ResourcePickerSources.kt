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
package com.android.tools.idea.ui.resourcechooser.common

import com.android.resources.ResourceType
import com.android.tools.idea.configurations.Configuration

/**
 * An enum for the different possible sources where resources could be loaded from for the Resource Picker.
 */
enum class ResourcePickerSources(val displayableName: String) {
  /**
   * For all local resources, this is the resources from the current module and all the local modules it depends on.
   */
  PROJECT("Project"),
  /**
   * For resources from all the external libraries available for the current module.
   */
  LIBRARY("Libraries"),
  /**
   * For resources that are part of the Android Framework.
   */
  ANDROID("Android"),
  /**
   * For all [ResourceType.ATTR] resources that have a valid mapping to a resource of desired [ResourceType].
   *
   * Depends on the selected theme in the [Configuration] of the current file.
   */
  THEME_ATTR("Theme Attributes");

  override fun toString(): String {
    return displayableName
  }

  companion object {
    /**
     * Convenience function to return all available sources in a list.
     */
    @JvmStatic
    fun allSources() = values().toList()
  }
}