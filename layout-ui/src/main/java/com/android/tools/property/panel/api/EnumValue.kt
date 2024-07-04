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
package com.android.tools.property.panel.api

import com.android.tools.adtui.model.stdui.CommonElementSelectability
import com.android.tools.property.panel.impl.support.AnActionEnumValue
import com.android.tools.property.panel.impl.support.BaseActionEnumValue
import com.android.tools.property.panel.impl.support.EmptyDisplayEnumValue
import com.android.tools.property.panel.impl.support.IndentedItemEnumValue
import com.android.tools.property.panel.impl.support.IndentedItemWithDisplayEnumValue
import com.android.tools.property.panel.impl.support.ItemEnumValue
import com.android.tools.property.panel.impl.support.ItemWithDisplayEnumValue
import com.android.tools.property.panel.impl.ui.EnumValueListCellRenderer
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.DataKey
import javax.swing.Icon
import javax.swing.ListCellRenderer

/**
 * Representation of values for the builtin [EnumSupport].
 *
 * This interface supports groups and separators in a popup. Headers and separators will implement
 * [CommonElementSelectability] which enables certain lists to skip the selection of these elements.
 *
 * Example:
 *
 * Android option1 option2 AppCompat option1 option2
 * ----------
 * option3
 */
interface EnumValue {
  /** The actual value to read/write to a [PropertyItem]. */
  val value: String?
    get() = null

  /**
   * The value to display in a ComboBox popup control.
   *
   * This value may be identical to [value] or may be a user-friendly representation of it.
   */
  val display: String
    get() = value ?: "none"

  /** If true, indent this value in the ComboBox popup. */
  val indented: Boolean
    get() = false

  /**
   * Specifies the operation done when this value is selected.
   *
   * The default operation simply updates the value on the property. This method could be overridden
   * to do something different like as opening a dialog.
   *
   * Use [newEnumValue] to let the UI controls see the new value before changing the property value.
   *
   * A return value of true means the value of the [EnumValue] was assigned. A return value of false
   * means the property was updated with other means e.g. from a dialog or an action.
   */
  fun select(property: PropertyItem, newEnumValue: NewEnumValueCallback): Boolean {
    newEnumValue.newValue(value)
    property.value = value
    return true
  }

  /** Convenience method for creating a variant [EnumValue] with indentation. */
  fun withIndentation(): EnumValue = this

  /** Default implementations of [EnumValue]s */
  companion object {
    fun item(value: String): EnumValue = ItemEnumValue(value)

    fun item(value: String, display: String): EnumValue = ItemWithDisplayEnumValue(value, display)

    fun indented(value: String): EnumValue = IndentedItemEnumValue(value)

    fun indented(value: String, display: String): EnumValue =
      IndentedItemWithDisplayEnumValue(value, display)

    fun action(action: AnAction): BaseActionEnumValue = AnActionEnumValue(action)

    fun header(header: String): EnumValue = HeaderEnumValue(header)

    fun header(header: String, icon: Icon?): EnumValue = HeaderEnumValue(header, icon)

    fun empty(display: String) = EmptyDisplayEnumValue(display)

    val DEFAULT_RENDERER: ListCellRenderer<EnumValue> = EnumValueListCellRenderer()
    val EMPTY: EnumValue = ItemEnumValue(null)
    val SEPARATOR: EnumValue = object : EnumValue, CommonElementSelectability {}
    val LOADING: EnumValue =
      object : EnumValue, CommonElementSelectability {
        override val display = "Loading..."
      }
    val PROPERTY_ITEM_KEY = DataKey.create<PropertyItem>("PROPERTY_ITEM")
    val NEW_ENUM_VALUE_CALLBACK_KEY =
      DataKey.create<NewEnumValueCallback>("NEW_ENUM_VALUE_CALLBACK")
  }
}

/** Representation of an enum value an action value. */
interface ActionEnumValue : EnumValue {

  /** The action represented with this enum value. */
  val action: AnAction
}

/**
 * A header to be displayed in the ComboBox popup.
 *
 * This element is not selectable.
 */
class HeaderEnumValue(val header: String, val headerIcon: Icon? = null) :
  EnumValue, CommonElementSelectability

/** A callback for notifying the UI controls about a new selected enum value. */
fun interface NewEnumValueCallback {
  fun newValue(value: String?)
}
