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
import com.android.tools.idea.naveditor.model.isArgument
import com.android.tools.idea.naveditor.model.nullable
import com.android.tools.idea.naveditor.model.typeAttr
import com.android.tools.idea.uibuilder.model.createChild
import com.intellij.openapi.command.WriteCommandAction
import org.jetbrains.android.dom.navigation.NavigationSchema.TAG_ARGUMENT

private const val ADD_ARGUMENT_COMMAND_NAME = "Add New Argument"
private const val ADD_ARGUMENT_GROUP_ID = "ADD_ARGUMENT_GROUP_ID"

class DefaultValueModel(argument: NlComponent, private val parent: NlComponent) {
  val name = argument.argumentName
  val type = argument.typeAttr
  private val nullableValue = argument.nullable
  var defaultValue: String
    get() = getComponent()?.defaultValue ?: ""
    set(newValue) {
      val component = getComponent()

      if (component == null && newValue.isBlank()) {
        return
      }

      WriteCommandAction.runWriteCommandAction(parent.model.project, ADD_ARGUMENT_COMMAND_NAME, ADD_ARGUMENT_GROUP_ID, Runnable {
        if (component == null) {
          parent.createChild(TAG_ARGUMENT)?.apply {
            argumentName = name
            defaultValue = newValue
            if (newValue == "@null") {
              typeAttr = type
              nullable = true
            } else {
              nullable = nullableValue
            }
          }
        }
        else {
          component.defaultValue = newValue
          if (newValue == "@null") {
            component.typeAttr = type
            component.nullable = true
          } else {
            component.nullable = nullableValue
          }
        }
      })
    }

  private fun getComponent() = parent.children.firstOrNull { it.isArgument && it.argumentName == name }
}
