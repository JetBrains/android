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

import com.android.tools.adtui.common.AdtUiUtils
import com.android.tools.idea.appinspection.inspectors.backgroundtask.model.BackgroundTaskInspectorClient
import com.android.tools.idea.concurrency.AndroidDispatchers
import com.intellij.ui.JBSplitter
import com.intellij.ui.components.JBScrollPane
import com.intellij.ui.components.JBTextArea
import kotlinx.coroutines.launch

/**
 * View class for the Background Task Inspector Tab.
 */
class BackgroundTaskInspectorTab(private val client: BackgroundTaskInspectorClient) {

  private val textArea = JBTextArea("")

  private val splitter = JBSplitter(false).apply {
    border = AdtUiUtils.DEFAULT_VERTICAL_BORDERS
    isOpaque = true
    firstComponent = BackgroundTaskInstanceView(client)
    secondComponent = JBScrollPane().apply { setViewportView(textArea) }
    dividerWidth = 1
    divider.background = AdtUiUtils.DEFAULT_BORDER_COLOR
  }

  val component = splitter


  init {
    var count = 0
    client.addEventListener { event ->
      client.scope.launch(client.uiThread) {
        count += 1
        textArea.text = "Event#$count\n${event}\n${textArea.text}"
      }
    }
  }
}