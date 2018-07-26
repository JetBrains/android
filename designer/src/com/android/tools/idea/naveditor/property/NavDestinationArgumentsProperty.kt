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
package com.android.tools.idea.naveditor.property

import com.android.SdkConstants
import com.android.tools.idea.common.model.NlComponent
import com.android.tools.idea.naveditor.property.inspector.SimpleProperty
import org.jetbrains.android.dom.navigation.NavigationSchema

class NavDestinationArgumentsProperty(components: List<NlComponent>) : ListProperty("Arguments", components) {
  override fun refreshList() {
    properties.clear()

    components.flatMap { it.children }
      .filter { it.tagName == NavigationSchema.TAG_ARGUMENT }
      .forEach {
        val name = it.resolveAttribute(SdkConstants.ANDROID_URI, SdkConstants.ATTR_NAME)
        val type = it.resolveAttribute(SdkConstants.AUTO_URI, SdkConstants.ATTR_TYPE) ?: "<inferred type>"
        val nullable = it.resolveAttribute(SdkConstants.AUTO_URI, SdkConstants.ATTR_NULLABLE)?.toBoolean() ?: false
        val default = it.resolveAttribute(SdkConstants.ANDROID_URI, NavigationSchema.ATTR_DEFAULT_VALUE)?.let { "(${it})" }
        val title = "${name}: ${type}${if (nullable) "?" else ""} ${default ?: ""}"
        properties.put(title, SimpleProperty(title, listOf(it)))
      }
  }
}