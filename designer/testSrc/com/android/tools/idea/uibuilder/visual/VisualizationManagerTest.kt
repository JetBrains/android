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
package com.android.tools.idea.uibuilder.visual

import com.android.tools.idea.testing.AndroidProjectRule
import com.intellij.openapi.wm.ToolWindowManager
import com.intellij.testFramework.EdtRule
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.JUnit4
import kotlin.test.assertNotNull

@RunWith(JUnit4::class)
class VisualizationManagerTest {

  @JvmField
  @Rule
  val projectRule = AndroidProjectRule.inMemory()

  @JvmField
  @Rule
  val edtRule = EdtRule()

  @Before
  fun setupToolWindowManager() {
    // The HeadlessToolWindowManager doesn't record the status of ToolWindow. We create a simple one to record it.
    val toolManager = VisualizationTestToolWindowManager(projectRule.project, projectRule.fixture.testRootDisposable)
    projectRule.replaceProjectService(ToolWindowManager::class.java, toolManager)
  }

  @Test
  fun testToolWindowExist() {
    assertNotNull(ToolWindowManager.getInstance(projectRule.project).getToolWindow(VisualizationManager.TOOL_WINDOW_ID))
  }
}
