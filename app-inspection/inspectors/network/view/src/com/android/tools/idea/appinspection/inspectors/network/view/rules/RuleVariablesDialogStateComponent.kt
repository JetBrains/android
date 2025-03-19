/*
 * Copyright (C) 2025 The Android Open Source Project
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
package com.android.tools.idea.appinspection.inspectors.network.view.rules

import com.android.tools.adtui.table.ConfigColumnTableAspect
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.PersistentStateComponent
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.State
import com.intellij.openapi.components.Storage

/** Persistence of the [RuleVariablesDialog] state */
@Service
@State(name = "RuleVariablesDialogState", storages = [Storage("ruleVariablesDialogState.xml")])
internal class RuleVariablesDialogStateComponent :
  PersistentStateComponent<RuleVariablesDialogState> {
  private var state = RuleVariablesDialogState()

  override fun getState() = state

  override fun loadState(state: RuleVariablesDialogState) {
    this.state = state
  }

  companion object {
    fun getInstance(): RuleVariablesDialogStateComponent =
      ApplicationManager.getApplication().getService(RuleVariablesDialogStateComponent::class.java)
  }
}

internal data class RuleVariablesDialogState(
  var columns: MutableList<ConfigColumnTableAspect.ColumnInfo> =
    RuleVariablesDialog.columnConfig.toMutableList(),
  var dialogWidth: Int = 600,
  var dialogHeight: Int = 200,
)
