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
package com.android.tools.idea.execution.common

import com.intellij.execution.ExecutionException
import com.intellij.execution.ExecutionResult
import com.intellij.execution.Executor
import com.intellij.execution.configurations.RunConfiguration
import com.intellij.execution.configurations.RunProfileState
import com.intellij.execution.runners.ExecutionEnvironment
import com.intellij.execution.runners.ProgramRunner
import com.intellij.execution.ui.RunContentDescriptor
import com.intellij.openapi.extensions.ExtensionPointName
import com.intellij.openapi.progress.ProgressIndicator
import org.jetbrains.annotations.VisibleForTesting


interface AndroidConfigurationExecutor {
  /**
   * An extension point to provide custom [AndroidConfigurationExecutor]
   */
  interface Provider {
    @Throws(ExecutionException::class)
    fun createAndroidConfigurationExecutor(env: ExecutionEnvironment): AndroidConfigurationExecutor?

    companion object {
      @JvmField
      val EP_NAME: ExtensionPointName<Provider> = ExtensionPointName.create("com.android.tools.idea.execution.common.androidConfigurationExecutorProvider")
    }
  }


  val configuration: RunConfiguration

  @Throws(ExecutionException::class)
  fun run(indicator: ProgressIndicator): RunContentDescriptor

  @Throws(ExecutionException::class)
  fun debug(indicator: ProgressIndicator): RunContentDescriptor

  @Throws(ExecutionException::class)
  fun applyChanges(indicator: ProgressIndicator): RunContentDescriptor {
    throw RuntimeException("Unsupported operation")
  }

  @Throws(ExecutionException::class)
  fun applyCodeChanges(indicator: ProgressIndicator): RunContentDescriptor {
    throw RuntimeException("Unsupported operation")
  }
}

class AndroidConfigurationExecutorRunProfileState(@VisibleForTesting val executor: AndroidConfigurationExecutor) :
  AndroidConfigurationExecutor by executor, RunProfileState {
  override fun execute(executor: Executor?, runner: ProgramRunner<*>): ExecutionResult? {
    throw RuntimeException("Unexpected code path")
  }
}

