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
package com.android.tools.idea.uibuilder.property2

import com.android.SdkConstants.ANDROID_URI
import com.android.SdkConstants.PREFIX_ANDROID
import com.android.tools.idea.common.scene.SceneManager
import com.android.util.PropertiesMap

/**
 * Provider of default property values for NlComponent attributes.
 *
 * Layoutlib will generate a map from each view object to the
 * default value computed by each view class.
 * Currently this is only done for a few view classes example: TextView,
 * but this map could be more extensive in the future.
 */
class NeleDefaultPropertyProvider(sceneManager: SceneManager) {
  val properties: Map<Any?, PropertiesMap> = sceneManager.defaultProperties

  /**
   * Given a [property] return the default value found by layoutlib.
   *
   * Or `null` if such a default value hasn't been reported.
   */
  fun provideDefaultValue(property: NelePropertyItem): String? {
    val key = namespacePrefix(property.namespace) + property.name
    return property.components
      .map { it.snapshot }
      .map { properties[it] }
      .map { it?.get(key) }
      .map { it?.resource }
      .distinct()
      .singleOrNull()
  }

  private fun namespacePrefix(namespace: String): String {
    return if (namespace == ANDROID_URI) PREFIX_ANDROID else ""
  }
}
