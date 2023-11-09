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

import com.intellij.icons.AllIcons
import icons.StudioIcons

object TaskBasedUxIcons {
  data class TaskBasedUxIcon(val path: String, val iconClass: Class<*>)

  /** The following are the StudioIcons used in the Task-Based UX Profiler. **/
  private const val TOOLBAR_ICON_BASE_PATH = "studio/icons/shell/toolbar"

  // Profiler icons for buttons used for rebuilding and relaunching the main process.
  val PROFILEABLE_PROFILER_ICON = TaskBasedUxIcon("$TOOLBAR_ICON_BASE_PATH/profiler-low-overhead.svg", StudioIcons::class.java)
  val DEBUGGABLE_PROFILER_ICON = TaskBasedUxIcon("$TOOLBAR_ICON_BASE_PATH/profiler-detailed.svg", StudioIcons::class.java)
  // The arrow down and arrow up icons are used for the import and export button respectively button.
  val IMPORT_RECORDING_ICON = TaskBasedUxIcon("studio/icons/layout-editor/toolbar/arrow-down.svg", StudioIcons::class.java)
  val EXPORT_RECORDING_ICON = TaskBasedUxIcon("studio/icons/layout-editor/toolbar/arrow-up.svg", StudioIcons::class.java)

  /** The following are the AllIcons used in the Task-Based UX Profiler. **/
  val RESTART_ICON = TaskBasedUxIcon("actions/restart.svg", AllIcons::class.java)
}