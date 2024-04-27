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
package com.android.tools.property.panel.impl.model

import com.android.tools.adtui.common.ColoredIconGenerator
import com.android.tools.adtui.model.stdui.ValueChangedListener
import com.android.tools.property.panel.api.HelpSupport
import com.android.tools.property.panel.api.InspectorLineModel
import com.android.tools.property.panel.api.PropertyEditorModel
import com.android.tools.property.panel.api.PropertyItem
import com.android.tools.property.panel.api.TableExpansionState
import com.android.tools.property.panel.api.TableSupport
import com.intellij.openapi.actionSystem.DataProvider
import com.intellij.ui.ExperimentalUI
import com.intellij.util.ui.ColorIcon
import com.intellij.util.ui.UIUtil
import java.awt.Color
import javax.swing.Icon
import kotlin.properties.Delegates

/**
 * A base implementation of a [PropertyEditorModel].
 *
 * Provides implementations of the following properties of an editor model.
 *
 * @property property The property being edited
 * @property value The computed value of the property
 * @property visible Controls the visibility of the editor
 * @property hasFocus Shows if an editor has focus. Setting this to true will cause focus to be
 *   requested to the editor.
 */
abstract class BasePropertyEditorModel(initialProperty: PropertyItem) :
  PropertyEditorModel, DataProvider {
  private val valueChangeListeners = mutableListOf<ValueChangedListener>()

  override var property: PropertyItem by
    Delegates.observable(initialProperty) { _, _, _ -> fireValueChanged() }

  override var value: String
    get() = property.value.orEmpty()
    set(value) {
      property.value = if (value.isEmpty()) null else value
    }

  override var visible = true
    get() = field && lineModel?.hidden != true
    set(value) {
      field = value
      fireValueChanged()
    }

  override var readOnly by Delegates.observable(false) { _, _, _ -> fireValueChanged() }

  final override var hasFocus = false
    private set

  /**
   * A focus request was made.
   *
   * We cannot call a method in the UI to request focus. Instead [focusRequest] is temporarily set
   * to true, and the Ui is requested to update itself. See the [requestFocus] function.
   */
  var focusRequest = false
    private set

  override fun requestFocus() {
    if (!hasFocus) {
      focusRequest = true
      fireValueChanged()
      focusRequest = false
    }
  }

  override var isUsedInRendererWithSelection: Boolean by
    Delegates.observable(false) { _, _, _ -> fireValueChanged() }

  fun displayedIcon(icon: Icon?): Icon? =
    if (icon != null && icon !is ColorIcon && isUsedInRendererWithSelection && !ExperimentalUI.isNewUI())
      ColoredIconGenerator.generateWhiteIcon(icon)
    else icon

  fun displayedForeground(foreground: Color): Color =
    if (isUsedInRendererWithSelection) UIUtil.getTableForeground(true, true) else foreground

  fun displayedBackground(background: Color): Color =
    if (isUsedInRendererWithSelection) UIUtil.getTableBackground(true, true) else background

  override var isExpandedTableItem: Boolean by
    Delegates.observable(false) { _, _, _ -> fireValueChanged() }

  override var tableExpansionState: TableExpansionState by
    Delegates.observable(TableExpansionState.NORMAL) { _, _, _ -> fireValueChanged() }

  override var isCustomHeight = false

  override var tableSupport: TableSupport? = null

  /**
   * Toggle to a known value.
   *
   * A noop for most editors. Boolean editors should override this method.
   */
  override fun toggleValue() {}

  val tooltip: String
    get() = property.tooltipForValue

  override var lineModel: InspectorLineModel? = null

  override fun cancelEditing(): Boolean {
    refresh()
    return true
  }

  override fun refresh() {
    updateValueFromProperty()
    fireValueChanged()
  }

  /**
   * The property value may have changed.
   *
   * Implementations should override this method and update their internal state after the value of
   * the property we are editing may have changed outside of the control of the editor.
   */
  open fun updateValueFromProperty() {}

  /** UI components can delegate to this base model for help support. */
  override fun getData(dataId: String): Any? {
    if (HelpSupport.PROPERTY_ITEM.`is`(dataId)) {
      return property
    }
    return null
  }

  open fun focusGained() {
    hasFocus = true
  }

  open fun focusLost() {
    hasFocus = false
  }

  override fun addListener(listener: ValueChangedListener) {
    valueChangeListeners.add(listener)
  }

  override fun removeListener(listener: ValueChangedListener) {
    valueChangeListeners.remove(listener)
  }

  protected var blockUpdates = false
    set(value) {
      field = value
      fireValueChanged()
    }

  protected fun fireValueChanged() {
    if (!blockUpdates) {
      valueChangeListeners.toTypedArray().forEach { it.valueChanged() }
    }
  }
}
