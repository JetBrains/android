/*
 * Copyright (C) 2017 The Android Open Source Project
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
import com.android.SdkConstants.ATTR_URI
import com.android.tools.idea.common.model.NlComponent

/**
 * Property representing all the actions (possibly zero) for a destinations.
 */
class NavDeeplinkProperty(components: List<NlComponent>) : ListProperty("Deeplinks", components) {

  init {
    refreshList()
  }

  override fun refreshList() {
    properties.clear()

    for (component in components) {
      for (child in component.children ?: listOf()) {
        if (child.tagName == SdkConstants.TAG_DEEPLINK) {
          child.resolveAttribute(SdkConstants.AUTO_URI, ATTR_URI)?.let {
            properties.put(it, ListPropertyItem(it, listOf(child)))
          }
        }
      }
    }
  }
}