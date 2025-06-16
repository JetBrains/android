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
package com.android.tools.idea.gradle.actions

import com.android.testutils.VirtualTimeScheduler
import com.android.tools.analytics.TestUsageTracker
import com.android.tools.analytics.UsageTracker
import com.android.tools.idea.flags.StudioFlags
import com.android.tools.idea.gradle.projectView.ProjectToolWindowSettings
import com.android.tools.idea.navigator.ANDROID_VIEW_ID
import com.google.wireless.android.sdk.stats.AndroidStudioEvent
import com.intellij.ide.projectView.ProjectView
import com.intellij.ide.projectView.impl.AbstractProjectViewPane
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.Presentation
import com.intellij.testFramework.HeavyPlatformTestCase
import org.mockito.Mock
import org.mockito.MockedStatic
import org.mockito.Mockito.mockStatic
import org.mockito.Mockito.`when`
import org.mockito.MockitoAnnotations.initMocks

/**
 * Tests for [ShowBuildFilesInModuleAction].
 */
class ShowBuildFilesInModuleActionTest : HeavyPlatformTestCase() {

  @Mock
  private lateinit var myEvent: AnActionEvent

  @Mock
  private lateinit var myViewPane: AbstractProjectViewPane

  @Mock lateinit var myProjectView: ProjectView

  private var myPresentation: Presentation? = null

  @Throws(Exception::class)
  override fun setUp() {
    super.setUp()
    initMocks(this)

     myPresentation = Presentation()
    `when`(myEvent.presentation).thenReturn(myPresentation)
    `when`(myEvent.project).thenReturn(myProject)
  }

  fun testActionVisibility() {
    val mockedStaticProjectView: MockedStatic<ProjectView> = mockStatic(ProjectView::class.java)
    mockedStaticProjectView.`when`<ProjectView> { ProjectView.getInstance(project) }
      .thenReturn(myProjectView)
    `when`(myViewPane.id).thenReturn(ANDROID_VIEW_ID)
    `when`(myProjectView.currentProjectViewPane).thenReturn(myViewPane)
    val action = ShowBuildFilesInModuleAction()

    // Visible when flag enabled and android view is open
    StudioFlags.SHOW_BUILD_FILES_IN_MODULE_SETTINGS.override(true)
    `when`(myViewPane.id).thenReturn(ANDROID_VIEW_ID)
    action.update(myEvent)
    assert(myEvent.presentation.isEnabledAndVisible)

    // Hidden when flag disabled and android view is open
    StudioFlags.SHOW_BUILD_FILES_IN_MODULE_SETTINGS.override(false)
    action.update(myEvent)
    assertFalse(myEvent.presentation.isEnabledAndVisible)

    // Hidden when flag enabled and android view is not open
    StudioFlags.SHOW_BUILD_FILES_IN_MODULE_SETTINGS.override(true)
    `when`(myViewPane.id).thenReturn("SomeOtherViewId")
    action.update(myEvent)
    assertFalse(myEvent.presentation.isEnabledAndVisible)
  }

  fun testSettingShowBuildFilesInModuleSetting() {
    val testUsageTracker = TestUsageTracker(VirtualTimeScheduler())
    UsageTracker.setWriterForTest(testUsageTracker)

    val settings = ProjectToolWindowSettings.Companion.getInstance()
    assertFalse(settings.showBuildFilesInModule)

    val action = ShowBuildFilesInModuleAction()
    action.setSelected(myEvent, true)
    assert(settings.showBuildFilesInModule)

    action.setSelected(myEvent, false)
    assertFalse(settings.showBuildFilesInModule)

    val statsEvents = testUsageTracker.usages.map {it.studioEvent }.filter { it.kind == AndroidStudioEvent.EventKind.ANDROID_VIEW_SHOW_BUILD_FILES_IN_MODULE_EVENT }
    assertSize(2, statsEvents)

    UsageTracker.cleanAfterTesting()
  }
}