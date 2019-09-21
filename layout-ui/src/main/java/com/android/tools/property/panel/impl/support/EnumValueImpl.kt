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
package com.android.tools.property.panel.impl.support

import com.android.tools.property.panel.api.ActionEnumValue
import com.android.tools.property.panel.api.EnumValue
import com.android.tools.property.panel.api.PropertyItem
import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.DataContext
import com.intellij.openapi.application.ApplicationManager

/**
 * Implementation of [EnumValue]
 *
 * This class and its derived classes provides implementations of [EnumValue].
 * The aim is to keep the size of the object small.
 * The value should always be the raw string value of the attribute, but the display
 * value may be something else.
 */
sealed class EnumValueImpl : EnumValue {
  override fun withHeader(header: String): EnumValue {
    return GenericEnumValue(value, display, header, indented, separator)
  }

  override fun withSeparator(): EnumValue {
    return GenericEnumValue(value, display, header, indented, true)
  }

  override fun withIndentation(): EnumValue {
    return GenericEnumValue(value, display, header, true, separator)
  }
}

data class ItemEnumValue(override val value: String?) : EnumValueImpl() {
  override fun toString() = value ?: ""
}

data class IndentedItemEnumValue(override val value: String) : EnumValueImpl() {
  override val indented
    get() = true

  override fun toString() = value
}

data class ItemWithDisplayEnumValue(override val value: String, override val display: String) : EnumValueImpl() {
  override fun toString() = value
}

data class IndentedItemWithDisplayEnumValue(override val value: String, override val display: String) : EnumValueImpl() {
  override val indented
    get() = true

  override fun toString() = value
}

internal data class GenericEnumValue(override val value: String?, override val display: String, override val header: String,
                                     override val indented: Boolean, override val separator: Boolean) : EnumValueImpl() {
  override fun toString() = value ?: ""
}

sealed class BaseActionEnumValue(override val action: AnAction) : ActionEnumValue {
  override val value = ""

  override val display
    get() = action.templatePresentation.text ?: "action"

  override fun toString() = display

  override fun hashCode() = display.hashCode()

  // AnAction does not override hashCode and equals...
  override fun equals(other: Any?): Boolean {
    val otherActionValue = other as? BaseActionEnumValue ?: return false
    return action.javaClass == otherActionValue.action.javaClass
        && display == otherActionValue.display
        && header == otherActionValue.header
        && indented == otherActionValue.indented
        && separator == otherActionValue.separator
  }

  override fun select(property: PropertyItem): Boolean {
    ApplicationManager.getApplication().invokeLater {
      val propertyContext = DataContext { property }
      val event = AnActionEvent(null, propertyContext, "", action.templatePresentation.clone(), ActionManager.getInstance(), 0)
      action.actionPerformed(event)
    }
    return false
  }

  override fun withHeader(header: String): EnumValue {
    return GenericActionEnumValue(action, header, indented, separator)
  }

  override fun withSeparator(): EnumValue {
    return GenericActionEnumValue(action, header, indented, true)
  }

  override fun withIndentation(): EnumValue {
    return GenericActionEnumValue(action, header, true, separator)
  }
}

class AnActionEnumValue(action: AnAction) : BaseActionEnumValue(action)

internal class GenericActionEnumValue(action: AnAction, override val header: String,
                                      override val indented: Boolean, override val separator: Boolean) : BaseActionEnumValue(action)
