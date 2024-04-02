/*
 * Copyright (C) 2023 The Android Open Source Project
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
package com.android.tools.profilers.taskbased.common.constants

import androidx.compose.runtime.Composable
import com.android.tools.profilers.tasks.ProfilerTaskType
import com.intellij.icons.AllIcons
import icons.StudioIcons
import icons.StudioIconsCompose
import org.jetbrains.jewel.ui.painter.ResourcePainterProvider
import javax.swing.Icon

object TaskBasedUxIcons {
  data class TaskBasedUxIcon(val path: String, val iconClass: Class<*>)

  /** The following are the StudioIcons used in the Task-Based UX Profiler. **/
  private const val LARGE_TASK_ICON_BASE_PATH = "studio/icons/profiler/taskslarge/"

  private val LARGE_CALLSTACK_SAMPLE_TASK_ICON = TaskBasedUxIcon("$LARGE_TASK_ICON_BASE_PATH/callstack-sample-large.svg",
                                                                 StudioIcons::class.java)
  private val LARGE_SYSTEM_TRACE_TASK_ICON = TaskBasedUxIcon("$LARGE_TASK_ICON_BASE_PATH/system-trace-large.svg", StudioIcons::class.java)
  private val LARGE_JAVA_KOTLIN_METHOD_RECORDING_TASK_ICON = TaskBasedUxIcon("$LARGE_TASK_ICON_BASE_PATH/java-kotlin-method-trace-large.svg",
                                                                         StudioIcons::class.java)
  private val LARGE_HEAP_DUMP_TASK_ICON = TaskBasedUxIcon("$LARGE_TASK_ICON_BASE_PATH/heap-dump-large.svg", StudioIcons::class.java)
  private val LARGE_NATIVE_ALLOCATIONS_TASK_ICON = TaskBasedUxIcon("$LARGE_TASK_ICON_BASE_PATH/native-allocations-large.svg",
                                                                   StudioIcons::class.java)
  private val LARGE_JAVA_KOTLIN_ALLOCATIONS_TASK_ICON = TaskBasedUxIcon("$LARGE_TASK_ICON_BASE_PATH/java-kotlin-allocations-large.svg",
                                                                        StudioIcons::class.java)
  private val LARGE_LIVE_VIEW_TASK_ICON = TaskBasedUxIcon("$LARGE_TASK_ICON_BASE_PATH/live-view-large.svg", StudioIcons::class.java)

  /**
   * Utility to fetch the corresponding large task icon for a task type. To be used in the task selection grid.
   */
  fun getLargeTaskIcon(taskType: ProfilerTaskType): TaskBasedUxIcon {
    return when (taskType) {
      ProfilerTaskType.UNSPECIFIED -> throw IllegalStateException("No task icon is available for the UNSPECIFIED task type.")
      ProfilerTaskType.CALLSTACK_SAMPLE -> LARGE_CALLSTACK_SAMPLE_TASK_ICON
      ProfilerTaskType.SYSTEM_TRACE -> LARGE_SYSTEM_TRACE_TASK_ICON
      ProfilerTaskType.JAVA_KOTLIN_METHOD_RECORDING -> LARGE_JAVA_KOTLIN_METHOD_RECORDING_TASK_ICON
      ProfilerTaskType.HEAP_DUMP -> LARGE_HEAP_DUMP_TASK_ICON
      ProfilerTaskType.NATIVE_ALLOCATIONS -> LARGE_NATIVE_ALLOCATIONS_TASK_ICON
      ProfilerTaskType.JAVA_KOTLIN_ALLOCATIONS -> LARGE_JAVA_KOTLIN_ALLOCATIONS_TASK_ICON
      ProfilerTaskType.LIVE_VIEW -> LARGE_LIVE_VIEW_TASK_ICON
    }
  }

  /**
   * Utility to fetch the corresponding task icon for a task type. To be used for the task tab icon.
   */
  fun getTaskIcon(taskType: ProfilerTaskType): Icon {
    return when (taskType) {
      ProfilerTaskType.UNSPECIFIED -> throw IllegalStateException("No task icon is available for the UNSPECIFIED task type.")
      ProfilerTaskType.CALLSTACK_SAMPLE -> StudioIcons.Profiler.Tasks.CALLSTACK_SAMPLE
      ProfilerTaskType.SYSTEM_TRACE -> StudioIcons.Profiler.Tasks.SYSTEM_TRACE
      ProfilerTaskType.JAVA_KOTLIN_METHOD_RECORDING -> StudioIcons.Profiler.Tasks.JAVA_KOTLIN_METHOD_TRACE
      ProfilerTaskType.HEAP_DUMP -> StudioIcons.Profiler.Tasks.HEAP_DUMP
      ProfilerTaskType.NATIVE_ALLOCATIONS -> StudioIcons.Profiler.Tasks.NATIVE_ALLOCATIONS
      ProfilerTaskType.JAVA_KOTLIN_ALLOCATIONS -> StudioIcons.Profiler.Tasks.JAVA_KOTLIN_ALLOCATIONS
      ProfilerTaskType.LIVE_VIEW -> StudioIcons.Profiler.Tasks.LIVE_VIEW
    }
  }

  // The settings icon is used as a button to invoke the task configuration dialog.
  val TASK_CONFIG_ICON = TaskBasedUxIcon("studio/icons/common/settings.svg", StudioIcons::class.java)

  // The garbage icon is used for the delete recording button.
  val DELETE_RECORDING_ICON = TaskBasedUxIcon("studio/icons/common/delete.svg", StudioIcons::class.java)

  // The android head icon to indicate the preferred process
  val ANDROID_HEAD_ICON = TaskBasedUxIcon("studio/icons/common/android-head.svg", StudioIcons::class.java)

  // Recording screen icons.
  val RECORDING_IN_PROGRESS_ICON = TaskBasedUxIcon("studio/icons/profiler/toolbar/stop-recording.svg", StudioIcons::class.java)

  /** The following are the AllIcons used in the Task-Based UX Profiler. **/
  val IMPORT_RECORDING_ICON = TaskBasedUxIcon("toolbarDecorator/import.svg", AllIcons::class.java)
  val EXPORT_RECORDING_ICON = TaskBasedUxIcon("toolbarDecorator/export.svg", AllIcons::class.java)

  @Composable
  fun getDeviceIconPainter(swingIcon: Icon?): ResourcePainterProvider? {
    return when (swingIcon) {
      // Phone + Tablet
      StudioIcons.DeviceExplorer.VIRTUAL_DEVICE_PHONE -> StudioIconsCompose.DeviceExplorer.VirtualDevicePhone()
      StudioIcons.DeviceExplorer.PHYSICAL_DEVICE_PHONE -> StudioIconsCompose.DeviceExplorer.PhysicalDevicePhone()
      StudioIcons.DeviceExplorer.FIREBASE_DEVICE_PHONE -> StudioIconsCompose.DeviceExplorer.FirebaseDevicePhone()
      // Watch
      StudioIcons.DeviceExplorer.VIRTUAL_DEVICE_WEAR -> StudioIconsCompose.DeviceExplorer.VirtualDeviceWear()
      StudioIcons.DeviceExplorer.PHYSICAL_DEVICE_WEAR -> StudioIconsCompose.DeviceExplorer.PhysicalDeviceWear()
      StudioIcons.DeviceExplorer.FIREBASE_DEVICE_WEAR -> StudioIconsCompose.DeviceExplorer.FirebaseDeviceWear()
      // TV
      StudioIcons.DeviceExplorer.VIRTUAL_DEVICE_TV -> StudioIconsCompose.DeviceExplorer.VirtualDeviceTv()
      StudioIcons.DeviceExplorer.PHYSICAL_DEVICE_TV -> StudioIconsCompose.DeviceExplorer.PhysicalDeviceTv()
      StudioIcons.DeviceExplorer.FIREBASE_DEVICE_TV -> StudioIconsCompose.DeviceExplorer.FirebaseDeviceTv()
      // Auto
      StudioIcons.DeviceExplorer.VIRTUAL_DEVICE_CAR -> StudioIconsCompose.DeviceExplorer.VirtualDeviceCar()
      StudioIcons.DeviceExplorer.PHYSICAL_DEVICE_CAR -> StudioIconsCompose.DeviceExplorer.PhysicalDeviceCar()
      StudioIcons.DeviceExplorer.FIREBASE_DEVICE_CAR -> StudioIconsCompose.DeviceExplorer.FirebaseDeviceCar()
      // Icon not found
      else -> null
    }
  }

  /** The following are values used for enabled and disabled icon buttons. **/
  const val ENABLED_ICON_ALPHA = 1.0f
  const val DISABLED_ICON_ALPHA = 0.5f
}