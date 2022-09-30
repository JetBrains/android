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
import com.intellij.util.xmlb.Converter
import com.intellij.util.xmlb.annotations.OptionTag

@Service
@State(name = "NetworkInspectorRules")
class RulesPersistentStateComponent: PersistentStateComponent<RuleDataState> {
  var myRuleDataState: RuleDataState = RuleDataState()

  override fun getState(): RuleDataState = myRuleDataState

  override fun loadState(ruleDataState: RuleDataState) {
    myRuleDataState = ruleDataState
  }

}

data class RuleDataState(
  // Add custom converter to avoid XMLException being thrown by default converter
  @OptionTag(converter = RuleDataConverter::class)
  var rulesList: MutableList<RuleData> = mutableListOf()
)

/*
 * Custom converter to convert [MutableList<RuleData>] to/from string representation.
 * Used to suppress XMLExceptions arising from default converter
 */
class RuleDataConverter : Converter<MutableList<RuleData>>() {
  override fun toString(ignored: MutableList<RuleData>) = ""
  override fun fromString(ignored: String) = mutableListOf<RuleData>()
}



