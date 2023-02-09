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
package com.android.tools.idea.run.configuration.execution

import com.android.tools.idea.run.AndroidRunConfigurationBase
import com.android.tools.idea.run.DeviceFutures
import com.android.tools.idea.run.tasks.LaunchTasksProvider
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
import org.jetbrains.android.facet.AndroidFacet
import org.jetbrains.annotations.VisibleForTesting


interface AndroidConfigurationExecutor {
  /**
   * An extension point to introduce build system dependent [AndroidRunConfigurationBase] based [LaunchTasksProvider] implementations like
   * support for running instrumented tests via Gradle.
   */
  interface Provider {
    fun createAndroidConfigurationExecutor(
      facet: AndroidFacet,
      env: ExecutionEnvironment,
      deviceFutures: DeviceFutures
    ): AndroidConfigurationExecutor?

    companion object {
      @JvmField
      val EP_NAME: ExtensionPointName<Provider> = ExtensionPointName.create("com.android.run.AndroidConfigurationExecutorProvider")
    }
  }


  val configuration: RunConfiguration
  val deviceFutures: DeviceFutures

  @Throws(ExecutionException::class)
  fun run(indicator: ProgressIndicator): RunContentDescriptor

  @Throws(ExecutionException::class)
  fun debug(indicator: ProgressIndicator): RunContentDescriptor

  @Throws(ExecutionException::class)
  fun applyChanges(indicator: ProgressIndicator): RunContentDescriptor

  @Throws(ExecutionException::class)
  fun applyCodeChanges(indicator: ProgressIndicator): RunContentDescriptor
}

class AndroidConfigurationExecutorRunProfileState(@VisibleForTesting val executor: AndroidConfigurationExecutor) :
  AndroidConfigurationExecutor by executor, RunProfileState {
  override fun execute(executor: Executor?, runner: ProgramRunner<*>): ExecutionResult? {
    throw RuntimeException("Unexpected code path")
  }
}

