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
package com.android.tools.profilers.taskbased.tasks

import com.android.tools.profilers.tasks.ProfilerTaskType
import javax.swing.Icon

/**
 * Module for individual task grid item UI.
 * Used to represent each item in the Task-Based UX home tab's grid of tasks.
 */
data class TaskGridItemModel(
  val type: ProfilerTaskType,
  val iconPath: String,
)