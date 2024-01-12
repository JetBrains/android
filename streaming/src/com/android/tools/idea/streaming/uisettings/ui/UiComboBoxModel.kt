/*
 * Copyright (C) 2023 The Android Open Source Project
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
package com.android.tools.idea.streaming.uisettings.ui

import com.android.tools.idea.streaming.uisettings.binding.DefaultTwoWayProperty
import com.android.tools.idea.streaming.uisettings.binding.TwoWayProperty
import com.intellij.ui.layout.ComponentPredicate
import javax.swing.DefaultComboBoxModel

/**
 * A MutableComboBoxModel with a [TwoWayProperty] controlling the selection and a predicate for the presence of multiple languages.
 */
internal class UiComboBoxModel<T>: DefaultComboBoxModel<T>() {

  val selection: TwoWayProperty<T?> = object : DefaultTwoWayProperty<T?>(null) {
    override fun setFromUi(newValue: T?) {
      super.setFromUi(newValue)
      this@UiComboBoxModel.selectedItem = newValue
    }
  }

  fun sizeIsAtLeast(count: Int): ComponentPredicate {
    return object : ComponentPredicate() {
      override fun invoke(): Boolean = size >= count
      override fun addListener(listener: (Boolean) -> Unit) {}
    }
  }
}
