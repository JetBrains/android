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
import org.mockito.Mockito.`when`

fun createBinaryPluginIdentifierStub(displayName: String, className: String): BinaryPluginIdentifier {
  val pluginIdentifier = Mockito.mock(BinaryPluginIdentifier::class.java)
  `when`(pluginIdentifier.displayName).thenReturn(displayName)
  `when`(pluginIdentifier.className).thenReturn(className)
  return pluginIdentifier
}

fun createScriptPluginIdentifierStub(pluginName: String): ScriptPluginIdentifier {
  val pluginIdentifier = Mockito.mock(ScriptPluginIdentifier::class.java)
  `when`(pluginIdentifier.displayName).thenReturn(pluginName)
  return pluginIdentifier
}

fun createOperationDescriptorStub(name: String,
                                  displayName: String = name,
                                  parent: OperationDescriptor? = null): OperationDescriptor {
  val descriptor = Mockito.mock(OperationDescriptor::class.java)
  `when`(descriptor.name).thenReturn(name)
  `when`(descriptor.displayName).thenReturn(displayName)
  `when`(descriptor.parent).thenReturn(parent)
  return descriptor
}

fun createFinishEventStub(displayName: String, startTime: Long, endTime: Long, descriptor: OperationDescriptor? = null): FinishEvent {
  val event = Mockito.mock(FinishEvent::class.java)
  val result = Mockito.mock(SuccessResult::class.java)
  `when`(result.startTime).thenReturn(startTime)
  `when`(result.endTime).thenReturn(endTime)
  `when`(event.displayName).thenReturn(displayName)
  `when`(event.result).thenReturn(result)
  `when`(event.descriptor).thenReturn(descriptor)
  return event
}

fun createTaskOperationDescriptorStub(taskPath: String,
                                      originPlugin: PluginIdentifier,
                                      dependencies: List<TaskOperationDescriptor>): TaskOperationDescriptor {
  val taskOperationDescriptor = Mockito.mock(TaskOperationDescriptor::class.java)
  `when`(taskOperationDescriptor.taskPath).thenReturn(taskPath)
  `when`(taskOperationDescriptor.originPlugin).thenReturn(originPlugin)
  `when`(taskOperationDescriptor.dependencies).thenReturn(dependencies.toMutableSet())
  return taskOperationDescriptor
}

fun createTaskSuccessResultStub(taskExecutionStartTime: Long,
                                taskExecutionEndTime: Long): TaskSuccessResult {
  val taskSuccessResult = Mockito.mock(TaskSuccessResult::class.java)
  `when`(taskSuccessResult.startTime).thenReturn(taskExecutionStartTime)
  `when`(taskSuccessResult.endTime).thenReturn(taskExecutionEndTime)
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
  `when`(taskFinishEvent.descriptor).thenReturn(descriptor)
  `when`(taskFinishEvent.result).thenReturn(result)
  return taskFinishEvent
}

fun createProjectConfigurationStartEventStub(projectPath: String): ProjectConfigurationStartEvent {
  val projectConfigurationStartEvent = Mockito.mock(ProjectConfigurationStartEvent::class.java)

  val project = Mockito.mock(ProjectIdentifier::class.java)
  `when`(project.projectPath).thenReturn(projectPath)

  val descriptor = Mockito.mock(ProjectConfigurationOperationDescriptor::class.java)
  `when`(descriptor.project).thenReturn(project)

  `when`(projectConfigurationStartEvent.descriptor).thenReturn(descriptor)
  return projectConfigurationStartEvent
}

fun createProjectConfigurationFinishEventStub(
  projectPath: String,
  projectConfigurationStartTime: Long,
  projectConfigurationEndTime: Long,
  appliedPlugins: List<PluginIdentifier>
): ProjectConfigurationFinishEvent {
  val projectConfigurationFinishEvent = Mockito.mock(ProjectConfigurationFinishEvent::class.java)

  val project = Mockito.mock(ProjectIdentifier::class.java)
  `when`(project.projectPath).thenReturn(projectPath)

  val descriptor = Mockito.mock(ProjectConfigurationOperationDescriptor::class.java)
  `when`(descriptor.project).thenReturn(project)

  val result = Mockito.mock(ProjectConfigurationSuccessResult::class.java)
  `when`(result.startTime).thenReturn(projectConfigurationStartTime)
  `when`(result.endTime).thenReturn(projectConfigurationEndTime)
  val pluginApplicationResults = appliedPlugins.map { pluginIdentifier ->
    Mockito.mock(ProjectConfigurationOperationResult.PluginApplicationResult::class.java).apply {
      `when`(plugin).thenReturn(pluginIdentifier)
    }
  }
  `when`(result.pluginApplicationResults).thenReturn(pluginApplicationResults)

  `when`(projectConfigurationFinishEvent.descriptor).thenReturn(descriptor)
  `when`(projectConfigurationFinishEvent.result).thenReturn(result)
  return projectConfigurationFinishEvent
}
