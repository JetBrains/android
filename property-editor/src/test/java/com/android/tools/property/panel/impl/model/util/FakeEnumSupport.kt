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

import com.android.tools.property.panel.api.EnumSupport
import com.android.tools.property.panel.api.EnumValue
import com.android.tools.property.panel.impl.ui.EnumValueListCellRenderer
import com.intellij.openapi.actionSystem.AnAction
import javax.swing.ListCellRenderer

class FakeEnumSupport(vararg elements: String, action: AnAction? = null) : EnumSupport {

  override val values = mutableListOf<EnumValue>()

  override val renderer: ListCellRenderer<EnumValue> by lazy {
    EnumValueListCellRenderer()
  }

  init {
    values.addAll(elements.map { EnumValue.item(it) })
    if (action != null) {
      values.add(EnumValue.action(action))
    }
  }
}
