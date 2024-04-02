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

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.progress.Task
import com.intellij.openapi.project.Project
import com.intellij.util.concurrency.ThreadingAssertions
import java.util.concurrent.CompletableFuture
import java.util.concurrent.Future
import org.jetbrains.annotations.TestOnly

class AndroidRunConfigurationsManager(private val project: Project) {

  private val operationsStates = mutableListOf<Future<*>>()

  fun createProjectRunConfigurations() {
    val futureTaskOperation = CompletableFuture<Task>()
    val taskCreateRunConfigurations =
      object : Task.Backgroundable(project, "Setting up run configurations...") {
        override fun run(indicator: ProgressIndicator) {
          AndroidRunConfigurations.instance.createRunConfigurations(project)
        }

        override fun onFinished() {
          super.onFinished()
          futureTaskOperation.complete(this)
        }

        override fun isHeadless(): Boolean {
          return false
        }
      }

    if (ApplicationManager.getApplication().isUnitTestMode) {

      // Note: it is deadlock-safe to call "isDone" below as we know that isDone function is from
      // CompletableFuture class - with simple implementation and no calls to "external" code
      synchronized(operationsStates) {
        operationsStates.removeIf { it.isDone }
        operationsStates.add(futureTaskOperation)
      }
    }

    ProgressManager.getInstance().run(taskCreateRunConfigurations)
  }

  @TestOnly
  @Throws(Exception::class)
  fun consumeBulkOperationsState(stateConsumer: (Future<*>) -> Unit) {
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
