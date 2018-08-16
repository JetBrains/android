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
package com.android.tools.idea.common.property2.api

import com.android.tools.adtui.model.stdui.EditingSupport
import com.android.tools.adtui.ptable2.PTableItem
import javax.swing.Icon

/**
 * Defines basic information about a property.
 */
interface PropertyItem : PTableItem {
  /**
   * The namespace of the property e.g. "http://schemas.android.com/apk/res/android"
   */
  val namespace: String

  /**
   * Optional icon to indicate a namespace
   */
  val namespaceIcon: Icon?
    get() = null

  /**
   * The name of the property e.g. "gravity"
   */
  override val name: String

  /**
   * The property value
   */
  override var value: String?

  /**
   * If [value] is a reference then resolve the reference, otherwise this is the same as [value].
   */
  val resolvedValue: String?
    get() = value

  /**
   * Whether the original [value] is a reference value
   */
  val isReference: Boolean

  /**
   * An editor may display a button on the right
   *
   * The use is implementation defined, but is usually used to
   * provide a dialog where possible values can be selected.
   */
  val browseButton: ActionIconButton?
    get() = null

  /**
   * A color control may display an icon on the left
   *
   * An implementation should use this to provide custom
   * representation and editing color values. This value
   * is used for [ControlType.COLOR_EDITOR] controls.
   */
  val colorButton: ActionIconButton?
    get() = null

  /**
   * The tooltip for this property name
   */
  val tooltipForName: String
    get() = ""

  /**
   * The tooltip for the value of this property
   */
  val tooltipForValue: String
    get() = ""

  /**
   * Editing support while editing this property
   */
  val editingSupport: EditingSupport
    get() = EditingSupport.INSTANCE

  /**
   * The matching design property, i.e. tools attribute
   */
  val designProperty: PropertyItem
    get() = throw IllegalStateException()
}
