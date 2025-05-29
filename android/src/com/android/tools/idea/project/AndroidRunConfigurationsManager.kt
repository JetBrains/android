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
package com.android.tools.idea.project

import com.android.tools.idea.concurrency.coroutineScope
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.diagnostic.debug
import com.intellij.openapi.project.Project
import com.intellij.platform.ide.progress.withBackgroundProgress
import com.intellij.util.concurrency.ThreadingAssertions
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import org.jetbrains.annotations.TestOnly

private val LOG: Logger by lazy { Logger.getInstance(AndroidRunConfigurationsManager::class.java) }

class AndroidRunConfigurationsManager(private val project: Project) {

  private val operationsStates= mutableListOf<Job>()

  fun createProjectRunConfigurations() {
    LOG.debug { "AndroidRunConfigurationsManager.createProjectRunConfigurations" }
    project.coroutineScope.launch {
      withBackgroundProgress(project, "Setting up run configurations...") {
        AndroidRunConfigurations.instance.createRunConfigurations(project)
      }
    }.also {
      if (ApplicationManager.getApplication().isUnitTestMode) {
        synchronized(operationsStates) {
          operationsStates.removeIf { it.isCompleted }
          operationsStates.add(it)
        }
      }
    }
  }

  @TestOnly
  @Throws(Exception::class)
  fun consumeBulkOperationsState(stateConsumer: (Job) -> Unit) {
    ThreadingAssertions.assertEventDispatchThread()
    assert(ApplicationManager.getApplication().isUnitTestMode)

    // operationsStates could be modified in separate thread
    // create list copy to iterate on
    val statesCopy = synchronized(operationsStates) { operationsStates.toList() }
    for (operationsState in statesCopy) {
      stateConsumer.invoke(operationsState)
    }
  }

  companion object {
    @JvmStatic
    fun getInstance(project: Project): AndroidRunConfigurationsManager {
      return project.getService(AndroidRunConfigurationsManager::class.java)
    }
  }
}
