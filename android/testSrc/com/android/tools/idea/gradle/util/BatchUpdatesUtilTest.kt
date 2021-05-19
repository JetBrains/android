/*
 * Copyright (C) 2019 The Android Open Source Project
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
package com.android.tools.idea.gradle.util

import com.intellij.ide.file.BatchFileChangeListener
import com.intellij.openapi.components.impl.stores.BatchUpdateListener
import com.intellij.testFramework.LightPlatformTestCase
import com.intellij.testFramework.PlatformTestUtil
import org.mockito.Mockito.mock
import org.mockito.Mockito.verify

/**
 * Tests for [BatchUpdatesUtil]
 */
class BatchUpdatesUtilTest : LightPlatformTestCase() {

  lateinit var mockUpdatePublisher: BatchUpdateListener
  lateinit var mockFileChangeListener: BatchFileChangeListener

  override fun setUp() {
    super.setUp()
    mockUpdatePublisher = mock(BatchUpdateListener::class.java)
    mockFileChangeListener = mock(BatchFileChangeListener::class.java)
    val connection = project.messageBus.connect()
    connection.subscribe(BatchUpdateListener.TOPIC, mockUpdatePublisher)
    connection.subscribe(BatchFileChangeListener.TOPIC, mockFileChangeListener)
  }

  fun testStartBatchUpdate() {
    BatchUpdatesUtil.startBatchUpdate(project)
    PlatformTestUtil.dispatchAllInvocationEventsInIdeEventQueue()

    verify(mockUpdatePublisher).onBatchUpdateStarted()
    verify(mockFileChangeListener).batchChangeStarted(project, "batch update")
  }

  fun testFinishBatchUpdate() {
    BatchUpdatesUtil.finishBatchUpdate(project)
    PlatformTestUtil.dispatchAllInvocationEventsInIdeEventQueue()

    verify(mockUpdatePublisher).onBatchUpdateFinished()
    verify(mockFileChangeListener).batchChangeCompleted(project)
  }
}
