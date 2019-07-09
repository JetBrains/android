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

import com.android.build.attribution.data.PluginData
import com.android.build.attribution.data.TaskData
import org.gradle.tooling.events.BinaryPluginIdentifier
import org.gradle.tooling.events.PluginIdentifier
import org.gradle.tooling.events.task.TaskFinishEvent
import org.gradle.tooling.events.task.TaskOperationDescriptor
import org.gradle.tooling.events.task.TaskSuccessResult
import org.mockito.Mockito
import org.mockito.Mockito.`when`

fun createBinaryPluginIdentifierStub(pluginName: String): BinaryPluginIdentifier {
  val pluginIdentifier = Mockito.mock(BinaryPluginIdentifier::class.java)
  `when`(pluginIdentifier.displayName).thenReturn(pluginName)
  return pluginIdentifier
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

fun createTaskData(taskFinishEvent: TaskFinishEvent): TaskData {
  return TaskData(taskFinishEvent.descriptor.taskPath, PluginData(taskFinishEvent.descriptor.originPlugin))
}
