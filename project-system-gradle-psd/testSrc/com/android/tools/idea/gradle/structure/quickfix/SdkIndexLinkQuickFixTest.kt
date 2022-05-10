/*
 * Copyright (C) 2022 The Android Open Source Project
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
package com.android.tools.idea.gradle.structure.quickfix

import com.android.testutils.VirtualTimeScheduler
import com.android.tools.analytics.TestUsageTracker
import com.android.tools.analytics.UsageTracker
import com.android.tools.idea.gradle.structure.configurables.PsContext
import com.android.tools.idea.gradle.structure.model.PsProject
import com.google.common.truth.Truth.assertThat
import com.google.wireless.android.sdk.stats.AndroidStudioEvent
import com.intellij.openapi.project.Project
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.mockito.Mockito.mock
import org.mockito.Mockito.`when`

class SdkIndexLinkQuickFixTest {
  private lateinit var tracker: TestUsageTracker

  @Before
  fun setUp() {
    tracker = TestUsageTracker(VirtualTimeScheduler())
    UsageTracker.setWriterForTest(tracker)
  }

  @After
  fun tearDown() {
    tracker.close()
    UsageTracker.cleanAfterTesting()
  }

  @Test
  fun `execute logs click`() {
    val quickfix = SdkIndexLinkQuickFix("Open link text", "http://google.com", "com.google.androidx", "firebase", "2.0.0",
                                        browseFunction = {});
    val mockContext = mock(PsContext::class.java)
    val mockProject = mock(PsProject::class.java)
    val mockIdeProject = mock(Project::class.java)
    `when`(mockContext.project).thenReturn(mockProject)
    `when`(mockProject.ideProject).thenReturn(mockIdeProject)
    `when`(mockIdeProject.isDefault).thenReturn(true)
    `when`(mockIdeProject.isDisposed).thenReturn(true)
    quickfix.execute(mockContext)

    val events = tracker.usages
      .map { it.studioEvent }
      .filter { it.kind == AndroidStudioEvent.EventKind.SDK_INDEX_LINK_FOLLOWED }
    assertThat(events).isNotEmpty()
  }
}