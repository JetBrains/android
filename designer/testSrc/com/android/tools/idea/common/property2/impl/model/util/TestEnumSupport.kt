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
package com.android.tools.idea.common.property2.impl.model.util

import com.android.tools.idea.common.property2.api.EnumSupport
import com.android.tools.idea.common.property2.api.EnumValue
import com.android.tools.idea.common.property2.impl.support.ItemEnumValue
import com.android.tools.idea.common.property2.impl.ui.EnumValueListCellRenderer
import javax.swing.ListCellRenderer

class TestEnumSupport(vararg values: String) : EnumSupport {
  override val values: List<EnumValue> = values.map { ItemEnumValue(it) }

  override val renderer: ListCellRenderer<EnumValue> by lazy {
    EnumValueListCellRenderer()
  }
}
