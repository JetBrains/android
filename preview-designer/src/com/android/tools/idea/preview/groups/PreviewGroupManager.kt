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
package com.android.tools.idea.preview.groups

import com.android.tools.idea.preview.actions.GroupSwitchAction
import com.android.tools.preview.PreviewElement
import com.intellij.openapi.actionSystem.DataKey
import kotlinx.coroutines.flow.StateFlow

/**
 * Interface used for Preview Representations that support [PreviewGroup]s. It allows filtering
 * [PreviewElement]s based on a list of available [PreviewGroup]s or [PreviewGroup.All] when no
 * filtering should be applied.
 *
 * @see [GroupSwitchAction]
 */
interface PreviewGroupManager {
  /**
   * [StateFlow] of available named groups in this preview. The editor can contain multiple groups
   * and only one will be displayed at a given time.
   */
  val availableGroupsFlow: StateFlow<Set<PreviewGroup.Named>>

  /**
   * Currently selected group from [availableGroupsFlow] or [PreviewGroup.All] if none is selected.
   */
  var groupFilter: PreviewGroup

  companion object {
    val KEY = DataKey.create<PreviewGroupManager>("PreviewGroupManager")
  }
}
