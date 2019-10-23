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
package com.android.tools.property.panel.impl.model.util

import com.android.tools.property.panel.api.NewPropertyItem

class FakeNewPropertyItem(val properties: Map<String, FakePropertyItem> = emptyMap()) :
  FakePropertyItem("", "", null, null, null), NewPropertyItem {

  override var name: String = ""
    set(value) {
      field = value
      delegate = properties[value]
    }

  override var delegate: FakePropertyItem? = null

  // All "New" properties are considered equal, since only 1 should appear in a model
  override fun equals(other: Any?) = other is FakeNewPropertyItem

  override fun hashCode() = 12345

  override fun isSameProperty(qualifiedName: String): Boolean {
    return qualifiedName == name
  }
}
