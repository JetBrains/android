/*
 * Copyright (C) 2024 The Android Open Source Project
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
package com.android.tools.idea.common.error

import com.android.tools.idea.testing.AndroidProjectRule
import com.android.tools.idea.util.TestToolWindowManager
import com.intellij.analysis.problemsView.toolWindow.ProblemsView
import com.intellij.openapi.wm.RegisterToolWindowTask
import com.intellij.openapi.wm.ToolWindowManager
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test

class IssuePanelServiceTroubleInfoCollectorTest {
  @JvmField @Rule val projectRule = AndroidProjectRule.inMemory()

  @Before
  fun setup() {
    projectRule.replaceProjectService(
      ToolWindowManager::class.java,
      TestToolWindowManager(projectRule.project),
    )
    val manager = ToolWindowManager.getInstance(projectRule.project)
    manager.registerToolWindow(RegisterToolWindowTask(ProblemsView.ID))
  }

  @Test
  fun testCollector() {
    val info = IssuePanelServiceTroubleInfoCollector().collectInfo(projectRule.project)
    assertEquals("IssuePanelService: nIssues=0", info)
  }
}
