/*
 * Copyright (C) 2021 The Android Open Source Project
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
package com.android.tools.idea.appinspection.inspectors.backgroundtask.view

import com.android.tools.inspectors.common.api.stacktrace.StackTraceModel
import javax.swing.JComponent
import org.jetbrains.annotations.VisibleForTesting

class EntryDetailsStackTraceView(uiComponentsProvider: UiComponentsProvider) {
  @VisibleForTesting val stackTraceModel = StackTraceModel(uiComponentsProvider.codeNavigator)
  private val stackTraceView = uiComponentsProvider.createStackTraceView(stackTraceModel)
  val component: JComponent
    get() = stackTraceView.component

  fun updateTrace(stackTrace: String) {
    stackTraceModel.setStackFrames(stackTrace)
  }
}
