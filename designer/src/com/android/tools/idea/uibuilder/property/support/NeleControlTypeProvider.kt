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
package com.android.tools.idea.uibuilder.property.support

import com.android.tools.idea.uibuilder.property.NeleFlagsPropertyItem
import com.android.tools.idea.uibuilder.property.NelePropertyItem
import com.android.tools.idea.uibuilder.property.NelePropertyType
import com.android.tools.property.panel.api.ControlType
import com.android.tools.property.panel.api.ControlTypeProvider
import com.android.tools.property.panel.api.EnumSupportProvider

/**
 * [ControlType] provider of a value editor for a [NelePropertyItem].
 */
open class NeleControlTypeProvider(val enumSupportProvider: EnumSupportProvider<NelePropertyItem>) : ControlTypeProvider<NelePropertyItem> {

  override fun invoke(actual: NelePropertyItem): ControlType {
    val property = actual.delegate ?: actual
    return when {
      property is NeleFlagsPropertyItem ->
        ControlType.FLAG_EDITOR

      enumSupportProvider(property) != null ->
        when (property.type) {
          NelePropertyType.DESTINATION,
          NelePropertyType.CLASS_NAME,
          NelePropertyType.NAVIGATION -> ControlType.DROPDOWN
          else -> ControlType.COMBO_BOX
        }

      property.type == NelePropertyType.THREE_STATE_BOOLEAN ->
        ControlType.THREE_STATE_BOOLEAN

      property.type == NelePropertyType.BOOLEAN ->
        ControlType.BOOLEAN

      property.type == NelePropertyType.DRAWABLE ||
      property.type == NelePropertyType.COLOR ||
      property.type == NelePropertyType.COLOR_STATE_LIST ->
        ControlType.COLOR_EDITOR

      else ->
        ControlType.TEXT_EDITOR
    }
  }
}
