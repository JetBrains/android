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
package com.android.tools.idea.util

import com.android.tools.idea.projectsystem.PROJECT_SYSTEM_SYNC_TOPIC
import com.android.tools.idea.projectsystem.ProjectSystemSyncManager.SyncResult
import com.android.tools.idea.projectsystem.ProjectSystemSyncManager.SyncResultListener
import com.android.tools.idea.testing.AndroidProjectRule
import com.intellij.openapi.project.Project
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.JUnit4
import org.mockito.Mockito

@RunWith(JUnit4::class)
class SyncUtilTest {
  @Rule @JvmField val projectRule = AndroidProjectRule.inMemory().initAndroid(false)
  private lateinit var project: Project
  private lateinit var listener: SyncResultListener

  private fun emulateSync(result: SyncResult) = project.messageBus.syncPublisher(PROJECT_SYSTEM_SYNC_TOPIC).syncEnded(result)

  @Before
  fun setUp() {
    project = projectRule.project
    listener = Mockito.mock(SyncResultListener::class.java)
  }


  @Test
  fun listenUntilNextSuccessfulSync_broadcastsUntilSuccessful() {
    listenUntilNextSuccessfulSync(project, listener = listener)
    Mockito.verifyZeroInteractions(listener)

    emulateSync(SyncResult.CANCELLED)
    Mockito.verify(listener).syncEnded(SyncResult.CANCELLED)

    emulateSync(SyncResult.PARTIAL_SUCCESS)
    Mockito.verify(listener).syncEnded(SyncResult.PARTIAL_SUCCESS)

    emulateSync(SyncResult.SUCCESS)
    Mockito.verify(listener).syncEnded(SyncResult.SUCCESS)

    emulateSync(SyncResult.SKIPPED)
    Mockito.verifyNoMoreInteractions(listener)
  }
}
