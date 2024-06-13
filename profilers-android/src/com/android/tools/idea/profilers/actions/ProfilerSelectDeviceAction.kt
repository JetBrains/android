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
package com.android.tools.idea.profilers.actions

import com.intellij.openapi.actionSystem.AnActionEvent

/**
 * This action will select the first device from TaskHomeTabModel.processListModel.deviceList
 *
 * These profiler task actions are to be performed in a sequential format:
 *
 * ProfilerSelectDeviceAction -> ProfilerSelectProcessAction -> Select Profiler Tasks (System trace, Callstack sample, etc.) ->
 * Select dropdown actions (SetProfilingStartingPointToNowAction or SetProfilingStartingPointToProcessStartAction) ->
 * StartProfilerTaskAction -> StopProfilerTaskAction
 */
class ProfilerSelectDeviceAction : ProfilerTaskActionBase() {
  override fun actionPerformed(e: AnActionEvent) {
    val myTaskHomeModel = getTaskHomeTabModel(e.project!!)

    assert(myTaskHomeModel.processListModel.deviceList.value.isNotEmpty())
    // This Action is designed for E2E testing, and since the test framework can only instantiate one emulator as of 03/15/24,
    // Choosing the first device from the list
    val device = myTaskHomeModel.processListModel.deviceList.value[0]
    myTaskHomeModel.processListModel.onDeviceSelection(device)
  }
}