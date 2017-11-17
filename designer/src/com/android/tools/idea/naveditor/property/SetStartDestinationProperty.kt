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
package com.android.tools.idea.naveditor.property

import com.android.SdkConstants.AUTO_URI
import com.android.tools.idea.common.model.NlComponent
import com.android.tools.idea.common.model.NlModel
import com.android.tools.idea.naveditor.property.inspector.SimpleProperty
import com.intellij.openapi.command.WriteCommandAction
import org.jetbrains.android.dom.navigation.NavigationSchema

const val SET_START_DESTINATION_PROPERTY_NAME = "StartDestination"

class SetStartDestinationProperty(components: List<NlComponent>) : SimpleProperty(SET_START_DESTINATION_PROPERTY_NAME, components) {

  override fun getValue(): String? {
    val actualStart = NlComponent.stripId(myComponents[0].parent?.getAttribute(AUTO_URI, NavigationSchema.ATTR_START_DESTINATION))
    return if (actualStart != null && actualStart == myComponents[0].id) "true" else null
  }

  override fun setValue(value: Any?) {
    WriteCommandAction.runWriteCommandAction(myComponents[0].model.project) {
      myComponents[0].parent?.setAttribute(AUTO_URI, NavigationSchema.ATTR_START_DESTINATION, "@id/" + myComponents[0].id)
    }
    myComponents[0].model.notifyModified(NlModel.ChangeType.EDIT)
  }
}