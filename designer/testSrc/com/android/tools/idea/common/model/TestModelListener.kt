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
package com.android.tools.idea.common.model

class TestModelListener : ModelListener {
  private val callLog = StringBuilder()

  /** Returns a string logging all the calls that have been made since the last [clear] call. */
  fun callLogToString(): String = callLog.toString()

  /** Clears the call log. */
  fun clear() = callLog.clear()

  override fun modelDerivedDataChanged(model: NlModel) {
    callLog.appendLine("modelDerivedDataChanged (${model.modelDisplayName.value})")
  }

  override fun modelChanged(model: NlModel) {
    callLog.appendLine("modelChanged (${model.modelDisplayName.value})")
  }

  override fun modelLiveUpdate(model: NlModel, animate: Boolean) {
    callLog.appendLine("modelLiveUpdate (${model.modelDisplayName.value}, animate=$animate)")
  }

  override fun modelChangedOnLayout(model: NlModel, animate: Boolean) {
    callLog.appendLine("modelChangedOnLayout (${model.modelDisplayName.value}, animate=$animate)")
  }

  override fun modelActivated(model: NlModel) {
    callLog.appendLine("modelActivated (${model.modelDisplayName.value})")
  }
}
