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
package com.android.tools.idea.uibuilder.property2.model

import com.android.annotations.VisibleForTesting
import com.android.tools.idea.common.property2.api.FormModel
import com.android.tools.idea.common.property2.api.PropertyItem
import com.android.tools.idea.common.property2.impl.model.BasePropertyEditorModel
import javax.swing.Icon

/**
 * A model controlling a [com.android.tools.idea.uibuilder.property2.ui.ToggleButtonPropertyEditor]
 *
 * Which is a toggle button i.e. the user can select/un-select the button.
 * The model implements a [selected] property which uses the values specified
 * for [trueValue] and [falseValue].
 */
class ToggleButtonPropertyEditorModel(
  val description: String,
  val icon: Icon,
  @VisibleForTesting
  val trueValue: String,
  @VisibleForTesting
  val falseValue: String,
  property: PropertyItem,
  formModel: FormModel
) : BasePropertyEditorModel(property, formModel) {

  var selected: Boolean
    get() = property.resolvedValue == trueValue
    set(value) {
      this.value = if (value) trueValue else falseValue
    }
}
