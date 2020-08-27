/*
 * Copyright (C) 2020 The Android Open Source Project
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
package com.android.tools.idea.appinspection.inspectors.workmanager.view

import androidx.work.inspection.WorkManagerInspectorProtocol.WorkInfo.State
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

fun Long.toFormattedTimeString(): String {
  val formatter = SimpleDateFormat("h:mm:ss a", Locale.getDefault())
  return if (this == -1L) "-" else formatter.format(Date(this))
}

fun State.capitalizedName() = name[0] + name.substring(1).toLowerCase(Locale.getDefault())
