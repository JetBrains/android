/*
 * Copyright (C) 2021 The Android Open Source Project
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
package com.android.tools.idea.gradle.project.sync.idea.svs

import com.android.builder.model.AndroidProject
import org.gradle.tooling.BuildAction
import org.gradle.tooling.BuildController

internal interface GradleInjectedSyncActionRunner {
  fun <T> runActions(actions: List<(BuildController) -> T>): List<T>
  fun <T> runAction(action: (BuildController) -> T): T
  val parallelActionsSupported: Boolean
}

internal fun createActionRunner(controller: BuildController, flags: GradleSyncStudioFlags): GradleInjectedSyncActionRunner {
  return when (flags.studioFlagParallelSyncEnabled) {
    true -> ParallelSyncActionRunner(controller)
    false -> SequentialSyncActionRunner(controller)
  }
}

private class ParallelSyncActionRunner(private val controller: BuildController) : GradleInjectedSyncActionRunner {
  override fun <T> runActions(actions: List<(BuildController) -> T>): List<T> {
    return when (actions.size) {
      0 -> emptyList()
      1 -> listOf(runAction(actions[0]))
      else ->
        @Suppress("UNCHECKED_CAST", "UnstableApiUsage")
        controller.run(actions.map { action -> BuildAction { action(it) } }) as List<T>
    }
  }

  override fun <T> runAction(action: (BuildController) -> T): T {
    return action(controller)
  }

  @Suppress("UnstableApiUsage")
  override val parallelActionsSupported: Boolean
    get() = controller.getCanQueryProjectModelInParallel(AndroidProject::class.java)
}

private class SequentialSyncActionRunner(private val controller: BuildController) : GradleInjectedSyncActionRunner {
  override fun <T> runActions(actions: List<(BuildController) -> T>): List<T> {
    return actions.map { runAction(it) }
  }

  override fun <T> runAction(action: (BuildController) -> T): T {
    return action(controller)
  }

  override val parallelActionsSupported: Boolean
    get() = false
}
