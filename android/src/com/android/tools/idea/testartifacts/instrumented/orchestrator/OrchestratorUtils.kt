/*
 * Copyright (C) 2019 The Android Open Source Project
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
package com.android.tools.idea.testartifacts.instrumented.orchestrator

import com.android.ide.common.gradle.model.IdeTestOptions
import com.android.tools.idea.run.tasks.ConnectDebuggerTask
import com.android.tools.idea.run.tasks.ReattachingDebugConnectorTask
import com.android.tools.idea.run.tasks.createReattachingDebugConnectorTaskWithMasterAndroidProcessName

/**
 * A map of [IdeTestOptions.Execution] and the master android process name.
 */
val MAP_EXECUTION_TYPE_TO_MASTER_ANDROID_PROCESS_NAME = mapOf(
  IdeTestOptions.Execution.ANDROID_TEST_ORCHESTRATOR to "android.support.test.orchestrator",
  IdeTestOptions.Execution.ANDROIDX_TEST_ORCHESTRATOR to "androidx.test.orchestrator"
)

/**
 * Creates [ReattachingDebugConnectorTask] based on the given [baseConnector] for the given [executionType].
 */
fun createReattachingDebugConnectorTask(
  baseConnector: ConnectDebuggerTask,
  executionType: IdeTestOptions.Execution
): ReattachingDebugConnectorTask {
  return createReattachingDebugConnectorTaskWithMasterAndroidProcessName(
    baseConnector,
    MAP_EXECUTION_TYPE_TO_MASTER_ANDROID_PROCESS_NAME.getValue(executionType))
}