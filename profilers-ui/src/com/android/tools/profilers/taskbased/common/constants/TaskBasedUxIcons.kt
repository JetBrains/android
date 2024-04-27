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

import com.android.tools.profilers.tasks.ProfilerTaskType
import com.intellij.icons.AllIcons
import icons.StudioIcons
import javax.swing.Icon

object TaskBasedUxIcons {
  data class TaskBasedUxIcon(val path: String, val iconClass: Class<*>, val swingIcon: Icon)

  /** The following are the StudioIcons used in the Task-Based UX Profiler. **/
  private const val TASK_ICON_BASE_PATH = "studio/icons/profiler"

  val DISABLED_TASK_ICON = TaskBasedUxIcon("$TASK_ICON_BASE_PATH/sidebar/issue.svg", StudioIcons::class.java,
                                           StudioIcons.Profiler.Sidebar.ISSUE)
  private val CPU_TASK_ICON = TaskBasedUxIcon("$TASK_ICON_BASE_PATH/sessions/cpu.svg", StudioIcons::class.java,
                                              StudioIcons.Profiler.Sessions.CPU)
  private val ALLOCATIONS_TASK_ICON = TaskBasedUxIcon("$TASK_ICON_BASE_PATH/sessions/allocations.svg", StudioIcons::class.java,
                                                      StudioIcons.Profiler.Sessions.ALLOCATIONS)
  private val HEAP_DUMP_TASK_ICON = TaskBasedUxIcon("$TASK_ICON_BASE_PATH/sessions/heap.svg", StudioIcons::class.java,
                                                    StudioIcons.Profiler.Sessions.HEAP)
  private val LIVE_VIEW_TASK_ICON = TaskBasedUxIcon("studio/icons/shell/filetree/library-unknown.svg", StudioIcons::class.java,
                                                    StudioIcons.Shell.Filetree.LIBRARY_UNKNOWN)

  fun getTaskIcon(taskType: ProfilerTaskType): TaskBasedUxIcon {
    return when (taskType) {
      ProfilerTaskType.UNSPECIFIED -> DISABLED_TASK_ICON
      ProfilerTaskType.CALLSTACK_SAMPLE -> CPU_TASK_ICON
      ProfilerTaskType.SYSTEM_TRACE -> CPU_TASK_ICON
      ProfilerTaskType.JAVA_KOTLIN_METHOD_TRACE -> CPU_TASK_ICON
      ProfilerTaskType.JAVA_KOTLIN_METHOD_SAMPLE -> CPU_TASK_ICON
      ProfilerTaskType.HEAP_DUMP -> HEAP_DUMP_TASK_ICON
      ProfilerTaskType.NATIVE_ALLOCATIONS -> ALLOCATIONS_TASK_ICON
      ProfilerTaskType.JAVA_KOTLIN_ALLOCATIONS -> ALLOCATIONS_TASK_ICON
      ProfilerTaskType.LIVE_VIEW -> LIVE_VIEW_TASK_ICON
    }
  }

  private const val TOOLBAR_ICON_BASE_PATH = "studio/icons/shell/toolbar"

  // Profiler icons for buttons used for rebuilding and relaunching the main process.
  val PROFILEABLE_PROFILER_ICON = TaskBasedUxIcon("$TOOLBAR_ICON_BASE_PATH/profiler-low-overhead.svg", StudioIcons::class.java,
                                                  StudioIcons.Shell.Toolbar.PROFILER_LOW_OVERHEAD)
  val DEBUGGABLE_PROFILER_ICON = TaskBasedUxIcon("$TOOLBAR_ICON_BASE_PATH/profiler-detailed.svg", StudioIcons::class.java,
                                                 StudioIcons.Shell.Toolbar.PROFILER_DETAILED)

  // The settings icon is used as a button to invoke the task configuration dialog.
  val TASK_CONFIG_ICON = TaskBasedUxIcon("studio/icons/common/settings.svg", StudioIcons::class.java, StudioIcons.Common.SETTINGS)

  // The arrow down and arrow up icons are used for the import and export button respectively button.
  val IMPORT_RECORDING_ICON = TaskBasedUxIcon("studio/icons/layout-editor/toolbar/arrow-down.svg", StudioIcons::class.java,
                                              StudioIcons.LayoutEditor.Toolbar.ARROW_UP)
  val EXPORT_RECORDING_ICON = TaskBasedUxIcon("studio/icons/layout-editor/toolbar/arrow-up.svg", StudioIcons::class.java,
                                              StudioIcons.LayoutEditor.Toolbar.ARROW_DOWN)

  /** The following are the AllIcons used in the Task-Based UX Profiler. **/
  val RESTART_ICON = TaskBasedUxIcon("actions/restart.svg", AllIcons::class.java, AllIcons.Actions.Restart)
}