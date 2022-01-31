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
package com.android.tools.idea.run.configuration

import com.android.tools.idea.run.configuration.execution.AndroidConfigurationExecutorBase
import com.android.tools.idea.run.editor.AndroidDebugger
import com.intellij.execution.ExecutionResult
import com.intellij.execution.Executor
import com.intellij.execution.configurations.RunProfileState
import com.intellij.execution.runners.ProgramRunner

/**
 * Delegates execution of configuration from [RunProfileState.execute] to [AndroidConfigurationExecutorBase.execute].
 *
 * [RunProfileState.execute] doesn't work for Android configurations because we delegate creation of run content descriptors to
 * [AndroidDebugger]s in case on debug.
 *
 * See [AndroidConfigurationExecutorBase.createRunContentDescriptor], [AndroidConfigurationProgramRunner].
 */
internal class AndroidRunProfileStateAdapter(val executor: AndroidConfigurationExecutorBase) : RunProfileState {

  override fun execute(executor: Executor?, runner: ProgramRunner<*>): ExecutionResult? {
    throw RuntimeException("Unexpected code path")
  }
}