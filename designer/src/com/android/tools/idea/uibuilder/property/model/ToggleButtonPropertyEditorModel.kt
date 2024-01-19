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
package com.android.tools.idea.uibuilder.property.model

import com.android.tools.idea.uibuilder.property.NlPropertyItem
import com.android.tools.property.panel.impl.model.BasePropertyEditorModel
import com.google.common.annotations.VisibleForTesting
import javax.swing.Icon

/**
 * A model controlling a [com.android.tools.idea.uibuilder.property.ui.ToggleButtonPropertyEditor]
 *
 * Which is a toggle button i.e. the user can select/un-select the button. The model implements a
 * [selected] property which uses the values specified for [trueValue] and [falseValue].
 */
class ToggleButtonPropertyEditorModel(
  val description: String,
  val icon: Icon,
  @get:VisibleForTesting val trueValue: String,
  @get:VisibleForTesting val falseValue: String,
  private val nlProperty: NlPropertyItem,
) : BasePropertyEditorModel(nlProperty) {

  var selected: Boolean
    get() = nlProperty.resolvedValue == trueValue
    set(value) {
      this.value = if (value) trueValue else falseValue
    }
}
