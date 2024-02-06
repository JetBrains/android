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

import org.jetbrains.jewel.bridge.retrieveColorOrUnspecified

object TaskBasedUxColors {
  // Background color of a task grid item once selected.
  val TASK_SELECTION_BACKGROUND_COLOR get() = retrieveColorOrUnspecified("TabbedPane.focusColor")
  // Background color of a task grid item on hover.
  val TASK_HOVER_BACKGROUND_COLOR get() = retrieveColorOrUnspecified("TabbedPane.hoverColor")

  // Process and past recording table colors.
  val TABLE_ROW_SELECTION_BACKGROUND_COLOR get() = retrieveColorOrUnspecified("Table.selectionBackground")
  val TABLE_HEADER_BACKGROUND_COLOR get() = retrieveColorOrUnspecified("TableHeader.background")
  val TABLE_SEPARATOR_COLOR get() = retrieveColorOrUnspecified("TableHeader.separatorColor")
}