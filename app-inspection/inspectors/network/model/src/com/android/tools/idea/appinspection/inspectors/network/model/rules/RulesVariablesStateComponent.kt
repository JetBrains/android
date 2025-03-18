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
package com.android.tools.idea.appinspection.inspectors.network.model.rules

import com.android.tools.idea.flags.StudioFlags
import com.intellij.openapi.components.PersistentStateComponent
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.Service.Level.PROJECT
import com.intellij.openapi.components.State
import com.intellij.openapi.components.Storage
import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project

/** A [PersistentStateComponent] that holds the Rule Variables for a project */
@Service(PROJECT)
@State(name = "RuleVariablesState", storages = [Storage("ruleVariablesState.xml")])
class RuleVariablesStateComponent : PersistentStateComponent<RuleVariableState> {
  private var state = RuleVariableState()

  override fun getState() =
    if (StudioFlags.NETWORK_INSPECTOR_RULE_VARIABLES.get()) state else RuleVariableState()

  override fun loadState(state: RuleVariableState) {
    this.state = state
  }

  companion object {
    fun getInstance(project: Project): RuleVariablesStateComponent = project.service()
  }
}

data class RuleVariableState(var ruleVariables: MutableList<RuleVariable> = mutableListOf())
