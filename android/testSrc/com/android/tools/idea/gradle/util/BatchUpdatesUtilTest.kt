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
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.impl.stores.BatchUpdateListener
import com.intellij.openapi.util.Disposer
import com.intellij.testFramework.PlatformTestCase
import com.intellij.util.messages.MessageBus
import org.mockito.ArgumentMatchers.any
import org.mockito.ArgumentMatchers.eq
import org.mockito.Mockito.`when`
import org.mockito.Mockito.mock
import org.mockito.Mockito.spy
import org.mockito.Mockito.verify

/**
 * Tests for [BatchUpdatesUtil]
 */
class BatchUpdatesUtilTest : PlatformTestCase() {
  fun testStartBatchUpdate() {
    val spyProject = spy(project)
    val mockMessageBus = mock(MessageBus::class.java)
    val mockUpdatePublisher = mock(BatchUpdateListener::class.java)
    val mockFileChangeListener = mock(BatchFileChangeListener::class.java)
    val spyApplication = spy(ApplicationManager.getApplication())
    val disposable = Disposer.newDisposable()

    `when`(mockMessageBus.syncPublisher(BatchUpdateListener.TOPIC)).thenReturn(mockUpdatePublisher)
    `when`(mockMessageBus.syncPublisher(BatchFileChangeListener.TOPIC)).thenReturn(mockFileChangeListener)
    `when`(spyProject.messageBus).thenReturn(mockMessageBus)

    `when`(spyApplication.messageBus).thenReturn(mockMessageBus)
    ApplicationManager.setApplication(spyApplication, disposable)

    BatchUpdatesUtil.startBatchUpdate(spyProject)

    verify(mockUpdatePublisher).onBatchUpdateStarted()
    verify(mockFileChangeListener).batchChangeStarted(eq(spyProject), any())

    Disposer.dispose(disposable)
  }

  fun testFinishBatchUpdate() {
    val spyProject = spy(project)
    val mockMessageBus = mock(MessageBus::class.java)
    val mockUpdatePublisher = mock(BatchUpdateListener::class.java)
    val mockFileChangeListener = mock(BatchFileChangeListener::class.java)
    val spyApplication = spy(ApplicationManager.getApplication())
    val disposable = Disposer.newDisposable()

    `when`(mockMessageBus.syncPublisher(BatchUpdateListener.TOPIC)).thenReturn(mockUpdatePublisher)
    `when`(mockMessageBus.syncPublisher(BatchFileChangeListener.TOPIC)).thenReturn(mockFileChangeListener)
    `when`(spyProject.messageBus).thenReturn(mockMessageBus)

    `when`(spyApplication.messageBus).thenReturn(mockMessageBus)
    ApplicationManager.setApplication(spyApplication, disposable)

    BatchUpdatesUtil.finishBatchUpdate(spyProject)

    verify(mockUpdatePublisher).onBatchUpdateFinished()
    verify(mockFileChangeListener).batchChangeCompleted(eq(spyProject))

    Disposer.dispose(disposable)
  }
}
