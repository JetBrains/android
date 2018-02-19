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

import com.android.tools.idea.common.model.NlComponent
import com.android.tools.idea.naveditor.model.destinationType
import com.android.tools.idea.naveditor.model.schema
import com.android.tools.idea.naveditor.property.inspector.SimpleProperty
import org.jetbrains.android.dom.navigation.NavigationSchema.DestinationType.NAVIGATION

const val TYPE_EDITOR_PROPERTY_LABEL = "Type"

class NavComponentTypeProperty(components: List<NlComponent>) : SimpleProperty(TYPE_EDITOR_PROPERTY_LABEL, components) {
  override fun getValue(): String {
    val first = components[0]
    val schema = first.model.schema
    val types = components.map { it.destinationType }.toSet()
    return if (types.size == 1) {
      schema.getTagLabel(first.tagName, first.isRoot)
    }
    else {
      "Multiple"
    }
  }
}