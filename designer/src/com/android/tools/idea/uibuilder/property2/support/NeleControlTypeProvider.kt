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
package com.android.tools.idea.uibuilder.property2.support

import com.android.tools.idea.common.property2.api.ControlType
import com.android.tools.idea.common.property2.api.ControlTypeProvider
import com.android.tools.idea.common.property2.api.EnumSupport
import com.android.tools.idea.uibuilder.property2.NeleFlagsPropertyItem
import com.android.tools.idea.uibuilder.property2.NelePropertyItem
import com.android.tools.idea.uibuilder.property2.NelePropertyType

/**
 * Computation of the [ControlType] of a [NelePropertyItem].
 */
class NeleControlTypeProvider : ControlTypeProvider<NelePropertyItem> {

  override fun invoke(property: NelePropertyItem, enumSupport: EnumSupport?) =
    when {
      property is NeleFlagsPropertyItem ->
        ControlType.FLAG_EDITOR

      enumSupport != null ->
        ControlType.COMBO_BOX

      property.type == NelePropertyType.BOOLEAN ->
        ControlType.THREE_STATE_BOOLEAN

      else ->
        ControlType.TEXT_EDITOR
    }
}
