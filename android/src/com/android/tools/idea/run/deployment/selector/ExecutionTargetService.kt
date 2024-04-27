/*
 * Copyright (C) 2020 The Android Open Source Project
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
package com.android.tools.idea.run.deployment.selector

import com.google.common.annotations.VisibleForTesting
import com.intellij.execution.ExecutionTargetManager
import com.intellij.execution.RunManager
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.project.Project
import com.intellij.serviceContainer.NonInjectable

internal class ExecutionTargetService
@VisibleForTesting
@NonInjectable
constructor(
  private val project: Project,
  private val executionTargetManager: (Project) -> ExecutionTargetManager,
  private val runManager: (Project) -> RunManager,
) {
  @Suppress("unused")
  private constructor(
    project: Project
  ) : this(project, ExecutionTargetManager::getInstance, RunManager.Companion::getInstance)

  @get:VisibleForTesting
  var activeTarget: DeviceAndSnapshotComboBoxExecutionTarget
    get() = executionTargetManager(project).activeTarget as DeviceAndSnapshotComboBoxExecutionTarget
    set(target) {
      val executionTargetManager = executionTargetManager(project)
      if (executionTargetManager.activeTarget == target) {
        return
      }

      // In certain test scenarios, this action may get updated in the main test thread instead of
      // the EDT thread (is this correct?).
      // So we'll just make sure the following gets run on the EDT thread and wait for its result.
      ApplicationManager.getApplication().invokeAndWait {
        val runManager = runManager(project)
        val settings = runManager.selectedConfiguration

        // There is a bug in {@link com.intellij.execution.impl.RunManagerImplKt#clear(boolean)}
        // where it's possible the selected setting's
        // RunConfiguration is be non-existent in the RunManager. This happens when temporary/shared
        // RunnerAndConfigurationSettings are
        // cleared from the list of RunnerAndConfigurationSettings, and the selected
        // RunnerAndConfigurationSettings is temporary/shared and
        // left dangling.
        if (settings == null || runManager.findSettings(settings.configuration) == null) {
          return@invokeAndWait
        }
        executionTargetManager.activeTarget = target
      }
    }
}
