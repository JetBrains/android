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

/**
 * Interface to handle property editing. Implemented by [LayoutInspectorEditHandler]
 */
interface EditHandler {
  fun editProperty(property: ViewProperty, newValue: String)

  fun isEditable(fieldName: String): Boolean
}

/**
 * Default handler used by [com.android.tools.idea.editors.layoutInspector.ptable.LITableItem] when not editing.
 */
class DefaultNoEditHandler: EditHandler {
  override fun isEditable(fieldName: String): Boolean = false

  override fun editProperty(property: ViewProperty, newValue: String) {
    //no op
  }
}
