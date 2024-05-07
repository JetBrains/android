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
package com.android.tools.idea.res

import com.android.tools.idea.projectsystem.PROJECT_SYSTEM_SYNC_TOPIC
import com.android.tools.idea.projectsystem.ProjectSystemService
import com.android.tools.idea.projectsystem.ProjectSystemSyncManager
import com.android.tools.idea.testing.AndroidProjectRule
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.EDT
import com.intellij.openapi.application.invokeAndWaitIfNeeded
import com.intellij.testFramework.TestFrameworkUtil
import com.intellij.testFramework.registerOrReplaceServiceInstance
import com.intellij.testFramework.waitUntil
import com.intellij.util.concurrency.Invoker.EDT
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.asExecutor
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.jetbrains.android.facet.ResourceFolderManager
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.JUnit4
import org.mockito.Mockito.never
import org.mockito.Mockito.spy
import org.mockito.Mockito.times
import org.mockito.Mockito.verify
import kotlin.time.Duration.Companion.seconds

@RunWith(JUnit4::class)
class AndroidProjectRootListenerTest {
  @get:Rule
  val androidProjectRule = AndroidProjectRule.inMemory()

  private val project by lazy { androidProjectRule.project }
  private val module by lazy { androidProjectRule.fixture.module }

  @Test
  fun updateAfterSync() {
    // Register ResourceFolderManager spy so we can tell if it's been updated.
    val resourceFolderManagerSpy: ResourceFolderManager = spy(ResourceFolderManager(module))
    module.registerOrReplaceServiceInstance(
      ResourceFolderManager::class.java,
      resourceFolderManagerSpy,
      androidProjectRule.testRootDisposable
    )

    // Register AndroidProjectRootListener and ensure it doesn't trigger updates as it starts.
    AndroidProjectRootListener.ensureSubscribed(project)
    verify(resourceFolderManagerSpy, never()).checkForChanges()

    // Simulate sync finishing.
    ApplicationManager.getApplication().invokeAndWait {
      project.messageBus
        .syncPublisher(PROJECT_SYSTEM_SYNC_TOPIC)
        .syncEnded(ProjectSystemSyncManager.SyncResult.SUCCESS)
    }

    // Wait for the event queue to clear out, and verify the ResourceFolderManager was updated.
    runBlocking {
      waitUntil("resourceFolderManagerSpy.checkForChanges was called", timeout = 5.seconds) {
        runCatching {
          verify(resourceFolderManagerSpy, times(1)).checkForChanges()
        }.isSuccess
      }
    }
  }

  @Test
  fun updateAfterProjectRootsChange() {
    // Register ResourceFolderManager spy so we can tell if it's been updated.
    val resourceFolderManagerSpy: ResourceFolderManager = spy(ResourceFolderManager(module))
    module.registerOrReplaceServiceInstance(
      ResourceFolderManager::class.java,
      resourceFolderManagerSpy,
      androidProjectRule.testRootDisposable
    )

    // Register AndroidProjectRootListener and ensure it doesn't trigger updates as it starts.
    AndroidProjectRootListener.ensureSubscribed(project)
    verify(resourceFolderManagerSpy, never()).checkForChanges()

    // Simulate roots changing.
    ApplicationManager.getApplication().invokeAndWait {
      // Replacing the current AndroidProjectSystem with itself is enough to trigger the relevant
      // events for this test.
      val projectSystemService = ProjectSystemService.getInstance(project)
      projectSystemService.replaceProjectSystemForTests(projectSystemService.projectSystem)
    }

    // Wait for the event queue to clear out, and verify the ResourceFolderManager was updated.

    runBlocking {
      waitUntil("resourceFolderManagerSpy.checkForChanges was called", timeout = 5.seconds) {
        runCatching {
          verify(resourceFolderManagerSpy, times(1)).checkForChanges()
        }.isSuccess
      }
    }
  }
}
