/*
 * Copyright (C) 2019 The Android Open Source Project
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
package com.android.build.attribution.analyzers

import com.android.testutils.MockitoKt.whenever
import com.android.tools.idea.gradle.project.build.invoker.GradleBuildInvoker
import com.android.tools.idea.gradle.project.build.invoker.GradleInvocationResult
import com.android.tools.idea.gradle.project.sync.snapshots.PreparedTestProject
import com.android.tools.idea.testing.AndroidGradleProjectRule
import com.android.tools.idea.testing.buildAndWait
import com.intellij.openapi.project.Project
import org.gradle.tooling.events.BinaryPluginIdentifier
import org.gradle.tooling.events.FinishEvent
import org.gradle.tooling.events.OperationDescriptor
import org.gradle.tooling.events.PluginIdentifier
import org.gradle.tooling.events.ScriptPluginIdentifier
import org.gradle.tooling.events.SuccessResult
import org.gradle.tooling.events.configuration.ProjectConfigurationFinishEvent
import org.gradle.tooling.events.configuration.ProjectConfigurationOperationDescriptor
import org.gradle.tooling.events.configuration.ProjectConfigurationOperationResult
import org.gradle.tooling.events.configuration.ProjectConfigurationStartEvent
import org.gradle.tooling.events.configuration.ProjectConfigurationSuccessResult
import org.gradle.tooling.events.task.TaskFinishEvent
import org.gradle.tooling.events.task.TaskOperationDescriptor
import org.gradle.tooling.events.task.TaskSuccessResult
import org.gradle.tooling.model.ProjectIdentifier
import org.mockito.Mockito
import java.io.File

fun createBinaryPluginIdentifierStub(displayName: String, className: String): BinaryPluginIdentifier {
  val pluginIdentifier = Mockito.mock(BinaryPluginIdentifier::class.java)
  whenever(pluginIdentifier.displayName).thenReturn(displayName)
  whenever(pluginIdentifier.className).thenReturn(className)
  return pluginIdentifier
}

fun createScriptPluginIdentifierStub(pluginName: String): ScriptPluginIdentifier {
  val pluginIdentifier = Mockito.mock(ScriptPluginIdentifier::class.java)
  whenever(pluginIdentifier.displayName).thenReturn(pluginName)
  return pluginIdentifier
}

fun createOperationDescriptorStub(name: String,
                                  displayName: String = name,
                                  parent: OperationDescriptor? = null): OperationDescriptor {
  val descriptor = Mockito.mock(OperationDescriptor::class.java)
  whenever(descriptor.name).thenReturn(name)
  whenever(descriptor.displayName).thenReturn(displayName)
  whenever(descriptor.parent).thenReturn(parent)
  return descriptor
}

fun createFinishEventStub(displayName: String, startTime: Long, endTime: Long, descriptor: OperationDescriptor? = null): FinishEvent {
  val event = Mockito.mock(FinishEvent::class.java)
  val result = Mockito.mock(SuccessResult::class.java)
  whenever(result.startTime).thenReturn(startTime)
  whenever(result.endTime).thenReturn(endTime)
  whenever(event.displayName).thenReturn(displayName)
  whenever(event.result).thenReturn(result)
  whenever(event.descriptor).thenReturn(descriptor)
  return event
}

fun createTaskOperationDescriptorStub(taskPath: String,
                                      originPlugin: PluginIdentifier,
                                      dependencies: List<TaskOperationDescriptor>): TaskOperationDescriptor {
  val taskOperationDescriptor = Mockito.mock(TaskOperationDescriptor::class.java)
  whenever(taskOperationDescriptor.taskPath).thenReturn(taskPath)
  whenever(taskOperationDescriptor.originPlugin).thenReturn(originPlugin)
  whenever(taskOperationDescriptor.dependencies).thenReturn(dependencies.toMutableSet())
  return taskOperationDescriptor
}

fun createTaskSuccessResultStub(taskExecutionStartTime: Long,
                                taskExecutionEndTime: Long): TaskSuccessResult {
  val taskSuccessResult = Mockito.mock(TaskSuccessResult::class.java)
  whenever(taskSuccessResult.startTime).thenReturn(taskExecutionStartTime)
  whenever(taskSuccessResult.endTime).thenReturn(taskExecutionEndTime)
  return taskSuccessResult
}

fun createTaskFinishEventStub(taskPath: String,
                              originPlugin: PluginIdentifier,
                              dependencies: List<TaskFinishEvent>,
                              taskExecutionStartTime: Long,
                              taskExecutionEndTime: Long): TaskFinishEvent {
  val taskFinishEvent = Mockito.mock(TaskFinishEvent::class.java)
  val descriptor = createTaskOperationDescriptorStub(taskPath, originPlugin, dependencies.map { it.descriptor })
  val result = createTaskSuccessResultStub(taskExecutionStartTime, taskExecutionEndTime)
  whenever(taskFinishEvent.descriptor).thenReturn(descriptor)
  whenever(taskFinishEvent.result).thenReturn(result)
  return taskFinishEvent
}

fun createProjectConfigurationStartEventStub(projectPath: String): ProjectConfigurationStartEvent {
  val projectConfigurationStartEvent = Mockito.mock(ProjectConfigurationStartEvent::class.java)

  val project = Mockito.mock(ProjectIdentifier::class.java)
  whenever(project.projectPath).thenReturn(projectPath)

  val descriptor = Mockito.mock(ProjectConfigurationOperationDescriptor::class.java)
  whenever(descriptor.project).thenReturn(project)

  whenever(projectConfigurationStartEvent.descriptor).thenReturn(descriptor)
  return projectConfigurationStartEvent
}

fun createProjectConfigurationFinishEventStub(
  projectPath: String,
  projectConfigurationStartTime: Long,
  projectConfigurationEndTime: Long,
  appliedPlugins: List<PluginIdentifier>
): ProjectConfigurationFinishEvent {
  val projectConfigurationFinishEvent = Mockito.mock(ProjectConfigurationFinishEvent::class.java)

  val descriptor = createProjectConfigurationOperationDescriptor(projectPath)

  val result = Mockito.mock(ProjectConfigurationSuccessResult::class.java)
  whenever(result.startTime).thenReturn(projectConfigurationStartTime)
  whenever(result.endTime).thenReturn(projectConfigurationEndTime)
  val pluginApplicationResults = appliedPlugins.map { pluginIdentifier ->
    Mockito.mock(ProjectConfigurationOperationResult.PluginApplicationResult::class.java).apply {
      whenever(plugin).thenReturn(pluginIdentifier)
    }
  }
  whenever(result.pluginApplicationResults).thenReturn(pluginApplicationResults)

  whenever(projectConfigurationFinishEvent.descriptor).thenReturn(descriptor)
  whenever(projectConfigurationFinishEvent.result).thenReturn(result)
  return projectConfigurationFinishEvent
}

fun createProjectConfigurationOperationDescriptor(projectPath: String) =
  Mockito.mock(ProjectConfigurationOperationDescriptor::class.java).apply {
  val project = Mockito.mock(ProjectIdentifier::class.java)
  whenever(project.projectPath).thenReturn(projectPath)
  whenever(this.project).thenReturn(project)
}

fun AndroidGradleProjectRule.invokeTasksRethrowingErrors(vararg tasks: String): GradleInvocationResult {
  val invocationResult = invokeTasks(tasks = tasks)
  invocationResult.buildError?.let { throw it }
  return invocationResult
}

interface TestContext {
  val project: Project
  val projectDir: File
  fun invokeTasks(vararg tasks: String): GradleInvocationResult
}

fun PreparedTestProject.runTest(testAction: TestContext.() -> Unit) {
  val projectDir = root
  open { project ->
    testAction(
      object : TestContext {
        override val project: Project = project
        override val projectDir: File = projectDir
        override fun invokeTasks(vararg tasks: String): GradleInvocationResult {
          val invocationResult = project.buildAndWait {
            it.executeTasks(GradleBuildInvoker.Request.builder(project, projectDir, *tasks).build())
          }
          invocationResult.buildError?.let { throw it }
          return invocationResult
        }
      }
    )
  }
}