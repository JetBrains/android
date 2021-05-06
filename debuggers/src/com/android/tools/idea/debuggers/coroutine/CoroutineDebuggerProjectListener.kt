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

import com.intellij.execution.Executor
import com.intellij.execution.configurations.ConfigurationFactory
import com.intellij.execution.configurations.ConfigurationType
import com.intellij.execution.configurations.RunConfiguration
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
 * ProjectListener that registers a [XDebuggerManagerListener] every time a project is open.
 * The [XDebuggerManagerListener] instantiates the Coroutine Debugger panel each time a debugger session starts.
 */
class CoroutineDebuggerProjectListener : ProjectManagerListener {
  private var connection: MessageBusConnection? = null

  override fun projectOpened(project: Project) {
    if (!FlagController.isCoroutineDebuggerEnabled) {
      return
    }

    val connection = project.messageBus.connect()
    connection.subscribe(XDebuggerManager.TOPIC, CoroutineDebuggerListener(project))
  }

  override fun projectClosed(project: Project) {
    connection?.disconnect()
  }

  private class CoroutineDebuggerListener(private val project: Project) : XDebuggerManagerListener {
    override fun processStarted(debugProcess: XDebugProcess) {
      if (!FlagController.isCoroutineDebuggerEnabled) {
        return
      }

      // forward the "processStarted" call to the Kotlin plugin DebuggerConnection component,
      // which is responsible for creating the Coroutines Debugger panel
      val fakeConfiguration = FakeExternalSystemRunConfiguration(project)
      val debuggerConnection = DebuggerConnection(project, fakeConfiguration, null, false)
      debuggerConnection.processStarted(debugProcess)
    }
  }

  /**
   * Currently [DebuggerConnection]'s constructor requires a ExternalSystemRunConfiguration.
   * This fake configuration is a temporary workaround to enable us to create [DebuggerConnection].
   * It will be removed once we merge intellij-kotlin 1.5 or manually path the kotlin plugin to make the
   * ExternalSystemRunConfiguration optional.
   */
  // TODO(b/182023182) remove these fake classes, once we update to intellij-kotlin 1.5,
  //  or by manually patching the kotlin plugin, if we want to flip the flag before the update happens.
  private class FakeExternalSystemRunConfiguration(
    project: Project,
    projectSystemId: ProjectSystemId = ProjectSystemId("fake"),
    configurationFactory: ConfigurationFactory = FakeFactory(FakeConfigType())
  ) : ExternalSystemRunConfiguration(projectSystemId, project, configurationFactory, null)

  private class FakeFactory(configType: ConfigurationType) : ConfigurationFactory(configType) {
    override fun createTemplateConfiguration(project: Project): RunConfiguration {
      return object : RunConfiguration {
        override fun getState(executor: Executor, environment: ExecutionEnvironment) = TODO("Not yet implemented")
        override fun getName() = ""
        override fun getIcon() = TODO("Not yet implemented")
        override fun clone() = this
        override fun getFactory() = TODO("Not yet implemented")
        override fun setName(name: String?) { }
        override fun getConfigurationEditor() = TODO("Not yet implemented")
        override fun getProject() = project
      }
    }
  }

  private class FakeConfigType : ConfigurationType {
    override fun getDisplayName() = ""
    override fun getConfigurationTypeDescription() = ""
    override fun getIcon() = TODO("Not yet implemented")
    override fun getId() = ""
    override fun getConfigurationFactories() = TODO("Not yet implemented")
  }
}