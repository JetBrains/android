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
import com.intellij.execution.configurations.RunConfiguration
import com.intellij.execution.runners.ExecutionEnvironment
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.ProjectManager
import com.intellij.xdebugger.XDebugSession
import org.jetbrains.android.facet.AndroidFacet
import org.jetbrains.concurrency.Promise
import org.jetbrains.concurrency.resolvedPromise
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
      applicationIdProvider, env.project
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
  override fun attachToClient(project: Project, client: Client, debugState: AndroidDebuggerState?): Promise<XDebugSession> {
    val debugPort = getClientDebugPort(client)

    // Try to find existing debug session
    val existingDebugSession = getExistingDebugSession(project, debugPort)
    if (existingDebugSession != null) {
      activateDebugSessionWindow(project, existingDebugSession.runContentDescriptor)
      return resolvedPromise(existingDebugSession)
    }
    return attachJavaDebuggerToClientAndShowTab(project, client)
  }

  companion object {
    const val ID = "Java"

    private fun getExistingDebugSession(project: Project, debugPort: String): XDebugSession? {
      val sessions: MutableList<XDebugSession> = ArrayList()
      val openProjects = ProjectManager.getInstance().openProjects

      // Scan through open project to find if this port has been opened in any session.
      for (openProject in openProjects) {
        val debuggerSession = findJdwpDebuggerSession(openProject, debugPort)
        if (debuggerSession != null) {
          debuggerSession.xDebugSession?.let { sessions.add(it) }
        }
      }
      return sessions.firstOrNull()
    }
  }
}