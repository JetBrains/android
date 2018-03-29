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
package com.android.tools.idea.common.property2.impl.model

import com.android.tools.adtui.model.stdui.CommonTextFieldModel
import com.android.tools.idea.common.property2.api.PropertyItem

/**
 * Model for properties that use a Text Editor.
 */
class TextFieldPropertyEditorModel(property: PropertyItem, override val editable: Boolean) :
  BasePropertyEditorModel(property), CommonTextFieldModel {

  override var text: String = ""

  override fun validate(editedValue: String) = property.validate(editedValue)

  fun enter(editedValue: String) {
    if (editedValue != value) {
      value = editedValue
    }
    super.enterKeyPressed()
  }

  fun escape() {
    fireValueChanged()
  }
}
