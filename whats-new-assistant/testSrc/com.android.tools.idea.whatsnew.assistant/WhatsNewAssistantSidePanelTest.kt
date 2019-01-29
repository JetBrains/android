/*
 * Copyright (C) 2018 The Android Open Source Project
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
package com.android.tools.idea.whatsnew.assistant

import com.android.tools.idea.assistant.OpenAssistSidePanelAction
import com.android.tools.idea.flags.StudioFlags
import com.intellij.openapi.wm.ToolWindowManager
import junit.framework.TestCase
import org.jetbrains.android.AndroidTestCase
import org.junit.Test

class WhatsNewAssistantSidePanelTest : AndroidTestCase() {


  override fun setUp() {
    super.setUp()
    StudioFlags.WHATS_NEW_ASSISTANT_ENABLED.override(true)
  }

  override fun tearDown() {
    super.tearDown()
    StudioFlags.WHATS_NEW_ASSISTANT_ENABLED.clearOverride()
  }

  /**
   * Test that the additional title for Assistant panel displays What's New
   */
  @Test
  fun testPanelTitle() {
    // Open Assistant
    val openAssistantAction = OpenAssistSidePanelAction()
    openAssistantAction.openWindowNow(WhatsNewAssistantBundleCreator.BUNDLE_ID, project)

    val toolWindowManager: ToolWindowManager = ToolWindowManager.getInstance(project)
    val toolWindow = toolWindowManager.getToolWindow(WhatsNewAssistantSidePanelAction.TOOL_WINDOW_TITLE)
    TestCase.assertEquals("What's New", toolWindow.contentManager.getContent(0)?.displayName)
  }
}
