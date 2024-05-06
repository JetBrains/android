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
package com.android.tools.idea.gradle.service.notification

import com.android.testutils.MockitoKt.mock
import com.android.testutils.MockitoKt.whenever
import com.android.tools.idea.gradle.structure.AndroidProjectSettingsServiceImpl
import com.intellij.openapi.roots.ui.configuration.ProjectSettingsService
import com.intellij.testFramework.LightPlatformTestCase
import com.intellij.testFramework.replaceService
import org.mockito.Mockito.verify
import javax.swing.event.HyperlinkEvent

class OpenProjectJdkLocationListenerTest : LightPlatformTestCase() {

  fun `test Given JdkLocationListener without rootProjectPath When hyperlinkActivated Then chooseJdkLocation was invoked with null path`() {
    val mockService = mock<AndroidProjectSettingsServiceImpl>()
    project.replaceService(ProjectSettingsService::class.java, mockService, testRootDisposable)
    val jdkHyperlink = OpenProjectJdkLocationListener.create(project, null)
    val mockHyperlinkEvent = mock<HyperlinkEvent>().apply {
      whenever(eventType).thenReturn(HyperlinkEvent.EventType.ACTIVATED)
    }

    jdkHyperlink?.hyperlinkUpdate(mock(), mockHyperlinkEvent)
    verify(mockService).chooseJdkLocation(null)
  }

  fun `test Given JdkLocationListener with rootProjectPath When hyperlinkActivated Then chooseJdkLocation was invoked with provided path`() {
    val mockService = mock<AndroidProjectSettingsServiceImpl>()
    project.replaceService(ProjectSettingsService::class.java, mockService, testRootDisposable)
    val rootProjectPath = "gradle/project/root/path"
    val jdkHyperlink = OpenProjectJdkLocationListener.create(project, rootProjectPath)
    val mockHyperlinkEvent = mock<HyperlinkEvent>().apply {
      whenever(eventType).thenReturn(HyperlinkEvent.EventType.ACTIVATED)
    }

    jdkHyperlink?.hyperlinkUpdate(mock(), mockHyperlinkEvent)
    verify(mockService).chooseJdkLocation(rootProjectPath)
  }
}