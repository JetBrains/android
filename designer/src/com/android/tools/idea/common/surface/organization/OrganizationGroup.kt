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
package com.android.tools.idea.common.surface.organization

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/**
 * Default [OrganizationGroup.isOpened] state of new created [OrganizationGroup]. Note: Default
 * being false currently not supported.
 */
const val DEFAULT_ORGANIZATION_GROUP_STATE = true

/** Information required for each organization group. */
class OrganizationGroup(
  val methodFqn: String,
  displayName: String,
  val saveState: (Boolean) -> Unit = { _ -> },
) {

  private val _isOpened = MutableStateFlow(true)

  /** If group is opened, all previews in this group are visible. */
  val isOpened: StateFlow<Boolean> = _isOpened.asStateFlow()

  /** Name of the organization. */
  val displayName: StateFlow<String> = MutableStateFlow(displayName)

  /** Set group opened or closed. */
  fun setOpened(isOpened: Boolean) {
    _isOpened.update { isOpened }
    saveState(isOpened)
  }
}
