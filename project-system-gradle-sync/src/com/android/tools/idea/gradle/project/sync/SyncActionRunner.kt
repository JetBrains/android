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
package com.android.tools.idea.gradle.project.sync

import org.gradle.tooling.BuildAction
import org.gradle.tooling.BuildController

internal interface GradleInjectedSyncActionRunner {
  fun <T> runActions(actionsToRun: List<ActionToRun<T>>): List<T>
  fun <T> runAction(action: (BuildController) -> T): T
}

data class ActionToRun<T>(val action: (BuildController) -> T, val canRunInParallel: Boolean)

class SyncActionRunner(
  private val controller: BuildController,
  val parallelActionsSupported: Boolean
  ): GradleInjectedSyncActionRunner {
  override fun <T> runActions(actionsToRun: List<ActionToRun<T>>): List<T> {
    return when (actionsToRun.size) {
      0 -> emptyList()
      1 -> listOf(runAction(actionsToRun[0].action))
      else ->
        if (parallelActionsSupported) {
          val indexedActions = actionsToRun.mapIndexed { index, action -> index to action}.toMap()
          val parallelActions = indexedActions.filter { it.value.canRunInParallel }
          val sequentialAction = indexedActions.filter { !it.value.canRunInParallel }
          val executionResults =
            parallelActions.keys.zip(
              @Suppress("UNCHECKED_CAST", "UnstableApiUsage")
              controller.run(parallelActions.map { indexedActionToRun -> BuildAction { indexedActionToRun.value.action(it) } }) as List<T>
            ).toMap() +
            sequentialAction.map { it.key to runAction(it.value.action) }.toMap()

          executionResults.toSortedMap().values.toList()
        }
        else {
          actionsToRun.map { runAction(it.action) }
        }
    }
  }

  override fun <T> runAction(action: (BuildController) -> T): T {
    return action(controller)
  }
}
