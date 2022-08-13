/*
 * Copyright (C) 2015 The Android Open Source Project
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
package com.android.tools.idea.run.editor

import com.android.AndroidProjectTypes
import com.android.annotations.concurrency.Slow
import com.android.ddmlib.Client
import com.android.tools.idea.model.AndroidModel
import com.android.tools.idea.model.TestExecutionOption
import com.android.tools.idea.run.AndroidRunConfiguration
import com.android.tools.idea.run.ApplicationIdProvider
import com.android.tools.idea.run.debug.attachJavaDebuggerToClientAndShowTab
import com.android.tools.idea.run.tasks.ConnectDebuggerTask
import com.android.tools.idea.run.tasks.ConnectJavaDebuggerTask
import com.android.tools.idea.testartifacts.instrumented.orchestrator.createReattachingConnectDebuggerTask
import com.intellij.execution.ExecutionHelper
import com.intellij.execution.configurations.RunConfiguration
import com.intellij.execution.runners.ExecutionEnvironment
import com.intellij.execution.ui.RunContentDescriptor
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.ProjectManager
import org.jetbrains.android.facet.AndroidFacet
import java.util.Optional

class AndroidJavaDebugger : AndroidDebuggerImplBase<AndroidDebuggerState?>() {
  override fun getId(): String {
    return ID
  }

  override fun getDisplayName(): String {
    return "Java Only"
  }

  override fun createState(): AndroidDebuggerState {
    return AndroidDebuggerState()
  }

  override fun createConfigurable(runConfiguration: RunConfiguration): AndroidDebuggerConfigurable<AndroidDebuggerState?> {
    return AndroidDebuggerConfigurable()
  }

  override fun getConnectDebuggerTask(
    env: ExecutionEnvironment,
    applicationIdProvider: ApplicationIdProvider,
    facet: AndroidFacet,
    state: AndroidDebuggerState
  ): ConnectDebuggerTask {
    val baseConnector = ConnectJavaDebuggerTask(
      applicationIdProvider, env.project,
      facet.configuration.projectType == AndroidProjectTypes.PROJECT_TYPE_INSTANTAPP
    )
    if (env.runProfile is AndroidRunConfiguration) {
      return baseConnector
    }
    val executionType = Optional.ofNullable(AndroidModel.get(facet))
      .map { obj: AndroidModel -> obj.testExecutionOption }
      .orElse(TestExecutionOption.HOST)
    return when (executionType) {
      TestExecutionOption.ANDROID_TEST_ORCHESTRATOR, TestExecutionOption.ANDROIDX_TEST_ORCHESTRATOR -> createReattachingConnectDebuggerTask(
        baseConnector,
        executionType
      )

      else -> baseConnector
    }
  }

  override fun supportsProject(project: Project): Boolean {
    return true
  }

  @Slow
  override fun attachToClient(project: Project, client: Client, debugState: AndroidDebuggerState?) {
    val debugPort = getClientDebugPort(client)
    val runConfigName = getRunConfigurationName(debugPort)

    // Try to find existing debug session
    if (hasExistingDebugSession(project, debugPort, runConfigName)) {
      return
    }
    attachJavaDebuggerToClientAndShowTab(project, client)
  }

  companion object {
    const val ID = "Java"
    private const val RUN_CONFIGURATION_NAME_PATTERN = "Android Debugger (%s)"
    fun getRunConfigurationName(debugPort: String): String {
      return String.format(RUN_CONFIGURATION_NAME_PATTERN, debugPort)
    }

    fun hasExistingDebugSession(
      project: Project,
      debugPort: String,
      runConfigName: String
    ): Boolean {
      var descriptors: Collection<RunContentDescriptor?>? = null
      val openProjects = ProjectManager.getInstance().openProjects
      var targetProject: Project?

      // Scan through open project to find if this port has been opened in any session.
      for (openProject in openProjects) {
        targetProject = openProject

        // First check the titles of the run configurations.
        descriptors = ExecutionHelper.findRunningConsoleByTitle(targetProject) { title -> runConfigName == title }

        // If it can't find a matching title, check the debugger sessions.
        if (descriptors.isEmpty()) {
          val debuggerSession = findJdwpDebuggerSession(targetProject, debugPort)
          if (debuggerSession != null) {
            val session = debuggerSession.xDebugSession
            if (session != null) {
              descriptors = listOf(session.runContentDescriptor)
            }
            else {
              // Detach existing session.
              debuggerSession.process.stop(false)
            }
          }
        }
        if (!descriptors!!.isEmpty()) {
          break
        }
      }
      return if (descriptors != null && !descriptors.isEmpty()) {
        activateDebugSessionWindow(project, descriptors.iterator().next()!!)
      }
      else false
    }
  }
}