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

import com.android.tools.idea.common.property2.api.PropertyItem
import com.intellij.openapi.actionSystem.AnAction
import javax.swing.Icon

/**
 * Model of a TextField control with a customizable button on the left for editing a property.
 *
 * @property editable True if the value is editable with a text editor.
 */
open class TextFieldWithLeftButtonEditorModel(property: PropertyItem,
                                              editable: Boolean) : TextFieldPropertyEditorModel(property, editable) {

  /**
   * The icon displayed on the left button if any.
   */
  open fun getLeftButtonIcon(focused: Boolean): Icon? {
    return null
  }

  /**
   * The action performed when the user clicks the left button.
   */
  open val buttonAction: AnAction?
    get() = null
}
