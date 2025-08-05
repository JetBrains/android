/*
 * Copyright (C) 2025 The Android Open Source Project
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
package com.android.tools.idea.layoutinspector.stateinspection

import com.intellij.openapi.extensions.ExtensionPointName
import com.intellij.openapi.project.Project

/** Provides a way to customize the text shown in the first line of a state read trace. */
interface LayoutInspectorStateReadRewriter {

  /** Given the first line of a state [read] return a different line to use instead. */
  fun rewriteStateRead(project: Project, read: String): String

  companion object {
    internal val EP_NAME: ExtensionPointName<LayoutInspectorStateReadRewriter> =
      ExtensionPointName.create(
        "com.android.tools.idea.layoutinspector.stateinspection.stateReadRewriter"
      )
  }
}
