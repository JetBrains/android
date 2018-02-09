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
package com.android.tools.idea.common.property2.impl.model

import com.android.tools.adtui.model.stdui.ValueChangedListener
import com.android.tools.idea.common.property2.api.*
import com.intellij.openapi.util.text.StringUtil

/**
 * A base implementation of a [PropertyEditorModel].
 *
 * Provides implementations of the following properties of an editor model.
 * @property property The property being edited
 * @property formModel The form model with settings for all editors
 * @property value The computed value of the property
 * @property visible Controls the visibility of the editor
 * @property focus Shows if an editor has focus. Setting this to true will cause focus to be requested to the editor.
 */
abstract class BasePropertyEditorModel(override val property: PropertyItem, override val formModel: FormModel) : PropertyEditorModel {
  private val valueChangeListeners = mutableListOf<ValueChangedListener>()

  override var value: String
    get() = computeValue(formModel.showResolvedValues)
    set(value) {
      property.value = StringUtil.nullize(value)
      refresh()
    }

  override var visible = true
    get() = field && line?.hidden != true
    set(value) {
      field = value
      fireValueChanged()
    }

  final override var focus = false
    private set

  override var focusRequest = false
    set(value) {
      if (value && !focus) {
        field = true
        fireValueChanged()
        field = false
      }
    }

  override var line: InspectorLineModel? = null

  open fun enterKeyPressed() {
    val line = line ?: return
    formModel.moveToNextLineEditor(line)
  }

  open fun f1KeyPressed() {
    (property as? HelpSupport)?.help()
  }

  open fun shiftF1KeyPressed() {
    (property as? HelpSupport)?.secondaryHelp()
  }

  override fun refresh() {
    updateValueFromProperty()
    fireValueChanged()
  }

  /**
   * The property value may have changed.
   *
   * Implementations should override this method and update their
   * internal state after the value of the property we are editing
   * may have changed outside of the control of the editor.
   */
  open fun updateValueFromProperty() {
  }

  fun focusGained() {
    focus = true
  }

  fun focusLost(editedValue: String) {
    focus = false
    if (editedValue != value) {
      value = editedValue
    }
  }

  private fun computeValue(showResolved: Boolean): String {
    val value = if (showResolved) property.resolvedValue else property.value
    return value.orEmpty()
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
      valueChangeListeners.forEach { it.valueChanged() }
    }
  }
}
