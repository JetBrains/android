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
package com.android.tools.idea.naveditor.property.ui

import com.android.tools.idea.common.model.NlComponent
import com.android.tools.idea.naveditor.model.argumentName
import com.android.tools.idea.naveditor.model.defaultValue
import com.android.tools.idea.naveditor.model.nullable
import com.android.tools.idea.naveditor.model.typeAttr
import icons.StudioIcons.NavEditor.Properties.ARGUMENT
import javax.swing.JList

class ArgumentCellRenderer : NavListCellRenderer(ARGUMENT) {
  override fun customizeCellRenderer(
    list: JList<out NlComponent>,
    value: NlComponent?,
    index: Int,
    selected: Boolean,
    hasFocus: Boolean,
  ) {
    super.customizeCellRenderer(list, value, index, selected, hasFocus)

    val name = value?.argumentName ?: "<missing name>"
    val type = value?.typeAttr ?: "<inferred type>"
    val nullable = value?.nullable ?: false
    val default = value?.defaultValue?.let { "(${it})" }
    val title = "${name}: ${type}${if (nullable) "?" else ""} ${default ?: ""}"
    append(title)
  }
}
