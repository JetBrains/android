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
package com.android.build.attribution.ui.view.details

import com.android.build.attribution.ui.mockTask
import com.android.build.attribution.ui.panels.taskDetailsPage
import com.android.tools.adtui.TreeWalker
import com.google.common.truth.Truth
import com.intellij.util.ui.UIUtil
import org.junit.Test
import javax.swing.JEditorPane

class TaskDetailsPageTest {

  @Test
  fun testTaskPageOnLogicalCriticalPath() {
    val taskData = mockTask(":module1", "task1", "myPlugin", 100, criticalPathDurationMs = 1000).apply {
      onLogicalCriticalPath = true
      onExtendedCriticalPath = true
    }
    val page = taskDetailsPage(taskData, {}, {})

    Truth.assertThat(clearHtml(TreeWalker(page).descendants().filterIsInstance<JEditorPane>()[1].text))
      .startsWith("This task frequently determines build duration because of dependencies between its inputs/outputs and other tasks.")
  }

  @Test
  fun testTaskPageOnExtendedCriticalPath() {
    val taskData = mockTask(":module1", "task1", "myPlugin", 100, criticalPathDurationMs = 1000).apply {
      onLogicalCriticalPath = false
      onExtendedCriticalPath = true
    }
    val page = taskDetailsPage(taskData, {}, {})

    Truth.assertThat(clearHtml(TreeWalker(page).descendants().filterIsInstance<JEditorPane>()[1].text))
      .startsWith("This task occasionally determines build duration because of parallelism constraints introduced by number of " +
                  "cores or other tasks in the same module.")
  }

  @Test
  fun testTaskPageNotOnCriticalPath() {
    val taskData = mockTask(":module1", "task1", "myPlugin", 100, criticalPathDurationMs = 1000).apply {
      onLogicalCriticalPath = false
      onExtendedCriticalPath = false
    }
    val page = taskDetailsPage(taskData, {}, {})

    Truth.assertThat(clearHtml(TreeWalker(page).descendants().filterIsInstance<JEditorPane>()[1].text))
      .startsWith("<b>Duration:</b>")
  }

  private fun clearHtml(html: String): String = UIUtil.getHtmlBody(html)
    .trimIndent()
    .replace("\n", "")
    .replace("<br>", "<br>\n")
    .trim()
}