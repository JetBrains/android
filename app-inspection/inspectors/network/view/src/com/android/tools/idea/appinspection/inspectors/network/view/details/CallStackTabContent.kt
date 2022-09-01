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
package com.android.tools.idea.appinspection.inspectors.network.view.details

import com.android.tools.idea.appinspection.inspectors.network.model.httpdata.HttpData
import com.android.tools.idea.codenavigation.CodeLocation
import com.android.tools.inspectors.common.api.stacktrace.StackFrameParser
import com.android.tools.inspectors.common.api.stacktrace.ThreadId
import com.android.tools.inspectors.common.ui.stacktrace.StackTraceView
import org.jetbrains.annotations.VisibleForTesting
import javax.swing.JComponent

/**
 * Tab which shows a stack trace to where a network request was created.
 */
class CallStackTabContent(@VisibleForTesting val stackTraceView: StackTraceView) : TabContent() {
  override val title = "Call Stack"

  override fun createComponent(): JComponent {
    return stackTraceView.component
  }

  override fun populateFor(data: HttpData?) {
    if (data != null) {
      stackTraceView.model.setStackFrames(ThreadId.INVALID_THREAD_ID, data.codeLocations())
    }
    else {
      stackTraceView.model.clearStackFrames()
    }
  }
}

/**
 * Captures the stacktrace content contained in an [HttpData] object.
 */
private fun HttpData.codeLocations(): List<CodeLocation> = trace.split('\n')
  .map { line -> line.trim { it <= ' ' } }
  .filter { line -> line.isNotEmpty() }
  .mapNotNull { line -> StackFrameParser.parseFrame(line) }