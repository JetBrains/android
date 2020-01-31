/*
 * Copyright (C) 2018 The Android Open Source Project
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
package com.android.tools.idea.ui.resourcemanager.model

import org.jetbrains.annotations.TestOnly
import kotlin.properties.Delegates

/**
 * Options to filter the resources shown in the resource explorer
 */
class FilterOptions internal constructor(
  private val isShowResourcesChanged: () -> Unit = {},
  private val searchStringChanged: (String) -> Unit = {},
  moduleDependenciesInitialValue: Boolean,
  librariesInitialValue: Boolean,
  val isShowSampleData: Boolean,
  showAndroidResources: Boolean,
  showThemeAttributes: Boolean) {

  /**
   * If true, the resources from the dependent modules will be shown.
   */
  var isShowModuleDependencies: Boolean
    by Delegates.observable(moduleDependenciesInitialValue) { _, old, new -> if (new != old) isShowResourcesChanged() }

  /**
   * If true, the resources from the dependent libraries will be shown.
   */
  var isShowLibraries: Boolean
    by Delegates.observable(librariesInitialValue) { _, old, new -> if (new != old) isShowResourcesChanged() }

  /**
   * If true, the Android Framework resources will be shown.
   */
  var isShowFramework: Boolean
    by Delegates.observable(showAndroidResources) { _, old, new -> if (new != old) isShowResourcesChanged() }

  /**
   * If true, Theme Attributes resources will be shown.
   */
  var isShowThemeAttributes: Boolean
    by Delegates.observable(showThemeAttributes) { _, old, new -> if (new != old) isShowResourcesChanged() }

  /**
   * The search string to use to filter resources
   */
  var searchString: String by Delegates.observable("") { _, old, new -> if (new != old) searchStringChanged(new) }

  companion object {
    /** Helper function to instantiate [FilterOptions] with an initial state. */
    fun create(isShowResourcesChanged: () -> Unit,
               searchStringChanged: (String) -> Unit,
               initialParams: FilterOptionsParams)
      = FilterOptions(isShowResourcesChanged,
                      searchStringChanged,
                      moduleDependenciesInitialValue =  initialParams.moduleDependenciesInitialValue,
                      librariesInitialValue = initialParams.librariesInitialValue,
                      isShowSampleData = initialParams.showSampleData,
                      showAndroidResources = initialParams.androidResourcesInitialValue,
                      showThemeAttributes = initialParams.themeAttributesInitialValue)

    /** Instantiate [FilterOptions] with no callback implementation and with all values initialized as false. */
    @TestOnly
    fun createDefault() = FilterOptions(moduleDependenciesInitialValue = false,
                                        librariesInitialValue = false,
                                        isShowSampleData = false,
                                        showAndroidResources = false,
                                        showThemeAttributes = false)
  }
}

/** Params to define the initial state of [FilterOptions]. */
data class FilterOptionsParams(
  val moduleDependenciesInitialValue: Boolean,
  val librariesInitialValue: Boolean,
  val showSampleData: Boolean,
  val androidResourcesInitialValue: Boolean,
  val themeAttributesInitialValue: Boolean
)