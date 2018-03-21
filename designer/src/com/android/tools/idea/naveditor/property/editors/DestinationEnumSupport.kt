/*
 * Copyright (C) 2017 The Android Open Source Project
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
package com.android.tools.idea.naveditor.property.editors

import com.android.SdkConstants
import com.android.tools.idea.common.model.NlComponent
import com.android.tools.idea.common.model.NlComponent.stripId
import com.android.tools.idea.common.property.NlProperty
import com.android.tools.idea.naveditor.model.getUiName
import com.android.tools.idea.uibuilder.property.editors.support.EnumSupport
import com.android.tools.idea.uibuilder.property.editors.support.ValueWithDisplayString

class DestinationEnumSupport(property: NlProperty, private val destinationGetter: (NlComponent) -> List<NlComponent>)
  : EnumSupport(property) {
  override fun getAllValues(): MutableList<ValueWithDisplayString> {
    return destinationGetter(myProperty.components[0])
        .map { getDisplayForDestination(it) }
        .toMutableList()
  }

  private fun getDisplayForDestination(component: NlComponent): ValueWithDisplayString {
    val uiName = component.getUiName(myProperty.resolver)
    val id = component.id
    val displayString = if (uiName == id) id else "$uiName ($id)"
    return ValueWithDisplayString(displayString, component.resolveAttribute(SdkConstants.ANDROID_URI, SdkConstants.ATTR_ID))
  }

  override fun createFromResolvedValue(resolvedValue: String, value: String?, hint: String?): ValueWithDisplayString =
      getDisplayForDestination(
          destinationGetter(myProperty.components[0])
              .first { it.id == stripId(resolvedValue) })

}