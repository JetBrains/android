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
package com.android.tools.idea.stats

import com.android.testutils.VirtualTimeScheduler
import com.android.tools.analytics.TestUsageTracker
import com.android.tools.analytics.UsageTracker
import com.google.wireless.android.sdk.stats.AndroidStudioEvent
import com.intellij.openapi.wm.ToolWindow
import com.intellij.openapi.wm.ToolWindowManager
import com.intellij.testFramework.registerComponentInstance
import org.jetbrains.android.AndroidTestCase
import org.mockito.Mock
import org.mockito.Mockito
import org.mockito.Mockito.`when`
import org.mockito.MockitoAnnotations

class ToolWindowTrackerServiceTest : AndroidTestCase() {
  private lateinit var myUsageTracker: TestUsageTracker
  private lateinit var myService : ToolWindowTrackerService
  @Mock
  private lateinit var myMockToolWindowManager: ToolWindowManager

  override fun setUp() {
    super.setUp()
    MockitoAnnotations.initMocks(this)
    myUsageTracker = TestUsageTracker(VirtualTimeScheduler())
    UsageTracker.setWriterForTest(myUsageTracker)
    val project = project
    myService = ToolWindowTrackerService(project)
    project.registerComponentInstance(ToolWindowManager::class.java, myMockToolWindowManager, testRootDisposable)
  }

  override fun tearDown() {
    myUsageTracker.close()
    UsageTracker.cleanAfterTesting()
    super.tearDown()
  }

  fun testOpen() {
    // registered a tool window in closed state
    val testId = "test"
    myService.toolWindowRegistered(testId)
    val mockToolWindow = Mockito.mock(ToolWindow::class.java)
    `when`(mockToolWindow.isActive).thenReturn(false)
    `when`(myMockToolWindowManager.getToolWindow(testId)).thenReturn(mockToolWindow)
    myService.stateChanged()

    // Open tool window
    `when`(mockToolWindow.isActive).thenReturn(true)
    myService.stateChanged()

    // check
    val usages = myUsageTracker.usages.filterNotNull()
    assert(usages.isNotEmpty())
    assert(usages.all { usage ->  usage.studioEvent.kind == AndroidStudioEvent.EventKind.STUDIO_TOOL_WINDOW_ACTION_STATS})
  }
}
