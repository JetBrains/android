/*
 * Copyright (C) 2023 The Android Open Source Project
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
package com.android.tools.idea.gradle.project.sync.jdk

import com.intellij.openapi.application.impl.NonBlockingReadActionImpl
import com.intellij.openapi.application.runWriteActionAndWait
import com.intellij.openapi.projectRoots.impl.UnknownSdkEditorNotification
import com.intellij.openapi.projectRoots.impl.UnknownSdkTracker
import com.intellij.openapi.roots.ProjectRootManager
import com.intellij.testFramework.IdeaTestUtil
import com.intellij.testFramework.LightPlatformTestCase
import com.intellij.util.ui.UIUtil
import org.junit.Test

class UnknownSdkTrackerTest: LightPlatformTestCase() {

  @Test
  fun `test Given disabled UnknownSdkTracker When project JDK is invalid Then no notification was triggered`() {
    runWriteActionAndWait {
      ProjectRootManager.getInstance(project).projectSdk = IdeaTestUtil.getMockJdk17()
    }
    UnknownSdkTracker.getInstance(project).updateUnknownSdks()

    NonBlockingReadActionImpl.waitForAsyncTaskCompletion()
    UIUtil.dispatchAllInvocationEvents()
    assertEmpty(UnknownSdkEditorNotification.getInstance(project).getNotifications())
  }
}