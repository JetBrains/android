/*
 * Copyright 2016 The Bazel Authors. All rights reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.google.idea.blaze.base.run.confighandler

import com.google.idea.blaze.base.command.BlazeCommandName
import com.google.idea.blaze.base.run.BlazeCommandRunConfiguration
import com.google.idea.blaze.base.run.state.BlazeCommandRunConfigurationCommonState
import com.intellij.execution.ExecutionException
import com.intellij.execution.Executor
import com.intellij.execution.configurations.RunProfile
import com.intellij.execution.configurations.RunProfileState
import com.intellij.execution.configurations.WrappingRunConfiguration
import com.intellij.execution.executors.DefaultDebugExecutor
import com.intellij.execution.runners.ExecutionEnvironment
import com.intellij.openapi.util.Key

/**
 * Supports the execution of [BlazeCommandRunConfiguration]s.
 *
 * Provides rule-specific RunProfileState and before-run-tasks.
 */
interface BlazeCommandRunConfigurationRunner {
  /** Returns the [RunProfileState] corresponding to the given environment.*/
  @Throws(ExecutionException::class)
  fun getRunProfileState(executor: Executor, environment: ExecutionEnvironment): RunProfileState?

  /**
   * Executes any required before run tasks.
   *
   * Returns true if no task exists or the task was successfully completed. Otherwise, returns false
   * if the task either failed or was cancelled.
   */
  fun executeBeforeRunTask(environment: ExecutionEnvironment): Boolean

  companion object {
    @JvmStatic
    fun isDebugging(environment: ExecutionEnvironment): Boolean = environment.executor is DefaultDebugExecutor

    @JvmStatic
    fun getConfiguration(environment: ExecutionEnvironment): BlazeCommandRunConfiguration {
      return getBlazeConfig(environment.runProfile) ?: error("No Bazel configuration for ${environment.runProfile}")
    }

    @JvmStatic
    fun getBlazeConfig(runProfile: RunProfile): BlazeCommandRunConfiguration? {
      return when (runProfile) {
        is WrappingRunConfiguration<*> -> runProfile.peer
        else -> runProfile
      } as? BlazeCommandRunConfiguration
    }

    @JvmStatic
    fun getBlazeCommand(environment: ExecutionEnvironment): BlazeCommandName? {
      return getConfiguration(environment)
        .getHandlerStateIfType(BlazeCommandRunConfigurationCommonState::class.java)
        ?.commandState
        ?.command
    }

    /** Used to store a runner to an [ExecutionEnvironment].  */
    @JvmField
    val RUNNER_KEY: Key<BlazeCommandRunConfigurationRunner> = Key.create("blaze.run.config.runner")
  }
}
