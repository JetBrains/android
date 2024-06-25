/*
 * Copyright (C) 2024 The Android Open Source Project
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
package com.android.tools.idea.common.model

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class DisplaySettings {
  private val _displayName = MutableStateFlow<String?>(null)

  /** Model name. This can be used when multiple models are displayed at the same time */
  val modelDisplayName: StateFlow<String?> = _displayName.asStateFlow()

  fun setDisplayName(value: String?) {
    _displayName.value = value
  }

  private val _tooltip = MutableStateFlow<String?>(null)

  /** Text to display when displaying a tooltip related to this model */
  val tooltip: StateFlow<String?> = _tooltip.asStateFlow()

  fun setTooltip(value: String?) {
    _tooltip.value = value
  }
}
