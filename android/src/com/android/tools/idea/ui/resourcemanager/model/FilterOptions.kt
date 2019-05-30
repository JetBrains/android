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

import kotlin.properties.Delegates

/**
 * Options to filter the resources shown in the resource explorer
 */
class FilterOptions(
  private val isShowResourcesChanged: () -> Unit = {},
  private val searchStringChanged: (String) -> Unit = {}) {

  /**
   * If true, the resources from the dependent modules will be shown.
   */
  var isShowModuleDependencies: Boolean by Delegates.observable(false) { _, old, new -> if (new != old) isShowResourcesChanged() }

  /**
   * If true, the resources from the dependent libraries will be shown.
   */
  var isShowLibraries: Boolean by Delegates.observable(false) { _, old, new -> if (new != old) isShowResourcesChanged() }

  /**
   * The search string to use to filter resources
   */
  var searchString: String by Delegates.observable("") { _, old, new -> if (new != old) searchStringChanged(new) }
}
