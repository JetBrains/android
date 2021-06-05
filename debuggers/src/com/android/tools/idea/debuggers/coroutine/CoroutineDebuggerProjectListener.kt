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
package com.android.tools.idea.debuggers.coroutine

import com.android.tools.idea.run.AndroidRunConfigurationBase
import com.intellij.execution.ExecutionListener
import com.intellij.execution.ExecutionManager
import com.intellij.execution.Executor
import com.intellij.execution.configurations.ConfigurationFactory
import com.intellij.execution.configurations.ConfigurationType
import com.intellij.execution.configurations.RunConfiguration
import com.intellij.execution.executors.DefaultDebugExecutor
import com.intellij.execution.runners.ExecutionEnvironment
import com.intellij.openapi.externalSystem.model.ProjectSystemId
import com.intellij.openapi.externalSystem.service.execution.ExternalSystemRunConfiguration
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.ProjectManagerListener
import com.intellij.util.messages.MessageBusConnection
import com.intellij.xdebugger.XDebugProcess
import com.intellij.xdebugger.XDebuggerManager
import com.intellij.xdebugger.XDebuggerManagerListener
import org.jetbrains.kotlin.idea.debugger.coroutine.DebuggerConnection

/**
 * Class responsible for setting up to coroutine debugger [DebuggerConnection]
 */
class CoroutineDebuggerProjectListener : ProjectManagerListener {
  private var connection: MessageBusConnection? = null
  private var executionListener = CoroutineDebuggerExecutionListener()

  override fun projectOpened(project: Project) {
    if (!FlagController.isCoroutineDebuggerEnabled) {
      return
    }

    // multiple projects can be opened, which causes multiple ProjectManagerListeners to be created.
    // ProjectManagerListeners#projectOpened is called on every listener every time a project is opened.
    // by checking this flag we prevent the same listener to register the execution listener multiple times.
    if (executionListener.project != null) {
      return
    }

    connection = project.messageBus.connect(project)

    // Using an ExecutionListener to hook into the launch of an android app instead of an AndroidLaunchTaskContributor
    // has the advantage that we need to set this up only once, when the project is opened, instead of every time an app is
    // launched.
    connection!!.subscribe(ExecutionManager.EXECUTION_TOPIC, executionListener)
    executionListener.project = project
  }

  override fun projectClosed(project: Project) {
    if (executionListener.project == project) {
      connection?.disconnect()
    }
  }

  private class CoroutineDebuggerExecutionListener : ExecutionListener {
    var project: Project? = null

    override fun processStarting(executorId: String, env: ExecutionEnvironment) {
      // Checking for AndroidRunConfigurationBase makes sure that the code is executed only for Android apps, not regular JVM or
      // kotlin. It also works for hybrid debugging on Android (native + jvm).
      if (executorId == DefaultDebugExecutor.EXECUTOR_ID && env.runProfile is AndroidRunConfigurationBase) {
        // The creation of DebuggerConnection registers an event listener to DebuggerManager.
        // When a debug process is started, it triggers a callback that creates the coroutine debugger panel UI.
        DebuggerConnection(env.project, null, null, false, alwaysShowPanel = true)
      }
    }
  }
}