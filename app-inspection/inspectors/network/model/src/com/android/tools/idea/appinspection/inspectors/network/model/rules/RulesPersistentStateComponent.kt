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
package com.android.tools.idea.appinspection.inspectors.network.model.rules

import com.intellij.openapi.components.PersistentStateComponent
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.State
import com.intellij.openapi.components.Storage
import com.intellij.openapi.components.StoragePathMacros

@Service
@State(name = "NetworkInspectorRules", storages = [Storage(StoragePathMacros.WORKSPACE_FILE)])
class RulesPersistentStateComponent : PersistentStateComponent<RuleDataState> {
  var myRuleDataState: RuleDataState = RuleDataState()

  override fun getState(): RuleDataState = myRuleDataState

  override fun loadState(ruleDataState: RuleDataState) {
    myRuleDataState = ruleDataState
  }
}

data class RuleDataState(var rulesList: MutableList<RuleData> = mutableListOf())
