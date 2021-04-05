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

import com.android.tools.property.panel.api.ControlType
import com.android.tools.property.panel.api.EnumSupportProvider
import com.android.tools.idea.uibuilder.property.NlPropertyItem

class NlTwoStateBooleanControlTypeProvider(enumSupportProvider: EnumSupportProvider<NlPropertyItem>)
  : NlControlTypeProvider(enumSupportProvider) {

  override fun invoke(actual: NlPropertyItem): ControlType {
    val type = super.invoke(actual)
    return if (type != ControlType.THREE_STATE_BOOLEAN) type else ControlType.BOOLEAN
  }
}
