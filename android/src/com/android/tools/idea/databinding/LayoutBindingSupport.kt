/*
 * Copyright (C) 2019 The Android Open Source Project
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
package com.android.tools.idea.databinding

import com.intellij.openapi.extensions.ExtensionPointName
import com.intellij.openapi.util.ModificationTracker
import org.jetbrains.android.facet.AndroidFacet

/** Extension point for supporting Data Binding / View Binding methods. */
interface LayoutBindingSupport {
  companion object {
    @JvmField
    val EP_NAME =
      ExtensionPointName<LayoutBindingSupport>(
        "com.android.tools.idea.databinding.layoutBindingSupport"
      )
  }

  /**
   * Return a [ModificationTracker] that is incremented every time the setting for whether data
   * binding is enabled or disabled changes.
   */
  val dataBindingEnabledTracker: ModificationTracker

  /**
   * Return a [ModificationTracker] that is incremented every time the setting for whether view
   * binding is enabled or disabled changes.
   */
  val viewBindingEnabledTracker: ModificationTracker

  fun getDataBindingMode(facet: AndroidFacet): DataBindingMode
}
