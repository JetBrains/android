/*
 * Copyright (C) 2020 The Android Open Source Project
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
package com.android.tools.adtui.model.stdui

/**
 * Interface that elements in a [CommonComboBoxModel] can implement.
 *
 * This will affect how the element is treated in a combo box.
 */
interface CommonElementSelectability {

  /**
   * If false, the element in the ComboBox will not be selectable
   * from keyboard/mouse. This would typically be used for header
   * and separator elements that don't represent an actual value.
   */
  val isSelectable: Boolean
    get() = false
}
