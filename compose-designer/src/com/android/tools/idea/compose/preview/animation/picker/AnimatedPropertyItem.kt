/*
 * Copyright (C) 2022 The Android Open Source Project
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
package com.android.tools.idea.compose.preview.animation.picker

import com.android.tools.adtui.model.stdui.EDITOR_NO_ERROR
import com.android.tools.adtui.model.stdui.EditingValidation
import com.android.tools.idea.compose.pickers.base.property.MemoryParameterPropertyItem
import com.android.tools.idea.compose.pickers.base.property.PsiPropertyItem

/**
 * A [PsiPropertyItem] that only exists on memory. Listeners should be added to subscribe on changes
 * for this property.
 */
class AnimatedPropertyItem(
  name: String,
  defaultValue: String?,
  inputValidation: EditingValidation = { EDITOR_NO_ERROR },
  override val namespace: String
) : MemoryParameterPropertyItem(name, defaultValue, inputValidation) {

  // TODO(b/256584578) Add a property validation.

  override var value: String? = defaultValue
    set(value) {
      field = value
      listeners.forEach { it() }
    }

  private val listeners: MutableList<() -> Unit> = mutableListOf()

  fun addListener(listener: () -> Unit) {
    listeners.add(listener)
  }
}
