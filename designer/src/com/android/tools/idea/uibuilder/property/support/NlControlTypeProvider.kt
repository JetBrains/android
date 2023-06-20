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

import com.android.SdkConstants.ATTR_INPUT_TYPE
import com.android.tools.idea.uibuilder.property.NlFlagsPropertyItem
import com.android.tools.idea.uibuilder.property.NlPropertyItem
import com.android.tools.idea.uibuilder.property.NlPropertyType
import com.android.tools.property.panel.api.ControlType
import com.android.tools.property.panel.api.ControlTypeProvider
import com.android.tools.property.panel.api.EnumSupportProvider

/**
 * [ControlType] provider of a value editor for a [NlPropertyItem].
 */
open class NlControlTypeProvider(val enumSupportProvider: EnumSupportProvider<NlPropertyItem>) : ControlTypeProvider<NlPropertyItem> {

  override fun invoke(actual: NlPropertyItem): ControlType {
    val property = actual.delegate ?: actual
    return when {
      // Note: For InputType properties do not use the standard flag editor. This property has too complex rules.
      // Instead display an action on the left of a text editor just like we do for color properties.
      // The action has a custom implementation in InputTypePropertyItem.
      property is NlFlagsPropertyItem ->
        if (property.name == ATTR_INPUT_TYPE) ControlType.COLOR_EDITOR else ControlType.FLAG_EDITOR

      enumSupportProvider(property) != null ->
        when (property.type) {
          NlPropertyType.DESTINATION,
          NlPropertyType.CLASS_NAME,
          NlPropertyType.NAVIGATION -> ControlType.DROPDOWN
          else -> ControlType.COMBO_BOX
        }

      property.type == NlPropertyType.THREE_STATE_BOOLEAN ->
        ControlType.THREE_STATE_BOOLEAN

      property.type == NlPropertyType.BOOLEAN ->
        ControlType.BOOLEAN

      property.type == NlPropertyType.DRAWABLE ||
      property.type == NlPropertyType.COLOR ||
      property.type == NlPropertyType.COLOR_STATE_LIST ->
        ControlType.COLOR_EDITOR

      else ->
        ControlType.TEXT_EDITOR
    }
  }
}
