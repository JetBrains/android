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
package com.android.tools.idea.editors.layoutInspector

import com.android.layoutinspector.model.ViewProperty
import java.util.*

class LayoutInspectorEditHandler : EditHandler {
  companion object {
    private val EDITABLE_FIELDS = Arrays.asList(
      "mPaddingLeft",
      "mPaddingTop",
      "mPaddingRight",
      "mPaddingBottom",
      "paddingBottom",
      "paddingLeft",
      "paddingTop",
      "paddingRight"
    )
  }

  override fun editProperty(property: ViewProperty, newValue: String) {
    // TODO(kelvinhanma) actually apply edit
    println("editing: " + property.fullName + " to value " + newValue)
  }

  override fun isEditable(fieldName: String): Boolean {
    return EDITABLE_FIELDS.contains(fieldName)
  }
}
