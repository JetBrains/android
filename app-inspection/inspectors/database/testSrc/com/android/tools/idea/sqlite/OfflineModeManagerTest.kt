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
package com.android.tools.idea.sqlite

import com.android.testutils.MockitoKt.mock
import com.android.testutils.MockitoKt.whenever
import com.android.tools.idea.appinspection.inspector.api.process.ProcessDescriptor
import com.android.tools.idea.sqlite.mocks.FakeDatabaseInspectorAnalyticsTracker
import com.android.tools.idea.sqlite.mocks.FakeFileDatabaseManager
import com.android.tools.idea.sqlite.model.SqliteDatabaseId
import com.android.tools.idea.testing.runDispatching
import com.intellij.testFramework.LightPlatformTestCase
import com.intellij.testFramework.registerServiceInstance
import com.intellij.util.concurrency.EdtExecutorService
import kotlin.coroutines.CoroutineContext
import kotlin.coroutines.EmptyCoroutineContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking

class OfflineModeManagerTest : LightPlatformTestCase() {

  private lateinit var processDescriptor: ProcessDescriptor

  private lateinit var fileDatabaseManager: FakeFileDatabaseManager
  private lateinit var offlineModeManager: OfflineModeManager

  private val liveDb1 =
    SqliteDatabaseId.fromLiveDatabase("db1", 1) as SqliteDatabaseId.LiveSqliteDatabaseId
  private val liveDb2 =
    SqliteDatabaseId.fromLiveDatabase("db2", 2) as SqliteDatabaseId.LiveSqliteDatabaseId
  private val inMemoryDb =
    SqliteDatabaseId.fromLiveDatabase(":memory: { 123 }", 3) as
      SqliteDatabaseId.LiveSqliteDatabaseId

  private lateinit var trackerService: FakeDatabaseInspectorAnalyticsTracker

  private lateinit var uiDispatcher: CoroutineContext

  override fun setUp() {
    super.setUp()

    uiDispatcher = EdtExecutorService.getInstance().asCoroutineDispatcher()

    trackerService = FakeDatabaseInspectorAnalyticsTracker()
    project.registerServiceInstance(DatabaseInspectorAnalyticsTracker::class.java, trackerService)

    fileDatabaseManager = FakeFileDatabaseManager()
    offlineModeManager =
      OfflineModeManagerImpl(
        project,
        fileDatabaseManager,
        uiDispatcher,
        isFileDownloadAllowed = { true }
      )

    processDescriptor = StubProcessDescriptor()
  }

  fun testDownloadFiles() {
    // Act
    val flow =
      offlineModeManager.downloadFiles(
        listOf(liveDb1, liveDb2, inMemoryDb),
        processDescriptor,
        null
      ) { _, _ -> }
    val results = runDispatching { flow.toList(mutableListOf()) }

    // Assert
    assertEquals(
      listOf(
        OfflineModeManager.DownloadProgress(
          OfflineModeManager.DownloadState.IN_PROGRESS,
          emptyList(),
          2
        ),
        OfflineModeManager.DownloadProgress(
          OfflineModeManager.DownloadState.IN_PROGRESS,
          listOf(fileDatabaseManager.databaseFileData),
          2
        ),
        OfflineModeManager.DownloadProgress(
          OfflineModeManager.DownloadState.IN_PROGRESS,
          listOf(fileDatabaseManager.databaseFileData, fileDatabaseManager.databaseFileData),
          2
        ),
        OfflineModeManager.DownloadProgress(
          OfflineModeManager.DownloadState.COMPLETED,
          listOf(fileDatabaseManager.databaseFileData, fileDatabaseManager.databaseFileData),
          2
        )
      ),
      results
    )
  }

  fun testDownloadFilesCanceled() {
    // Prepare
    val scope = CoroutineScope(EmptyCoroutineContext)
    var hasBeenCanceled = false
    val downloadFirstFile = CompletableDeferred<Unit>()

    // Act
    val flow =
      offlineModeManager.downloadFiles(listOf(liveDb1, liveDb2), processDescriptor, null) { _, _ ->
      }
    val job =
      scope.launch {
        try {
          flow
            .onEach {
              // get first one and delay others
              if (it.filesDownloaded.isNotEmpty()) CompletableDeferred<Unit>().await()
              else downloadFirstFile.complete(Unit)
            }
            .toList(mutableListOf())
          fail()
        } catch (e: CancellationException) {
          hasBeenCanceled = true
        }
      }
    runDispatching {
      downloadFirstFile.await()
      job.cancelAndJoin()
    }

    // Assert
    assertTrue(hasBeenCanceled)

    assertEquals(listOf(fileDatabaseManager.databaseFileData), fileDatabaseManager.cleanedUpFiles)
  }

  fun testDownloadFailed() = runBlocking {
    // Prepare
    val fileDatabaseManager = mock<FileDatabaseManager>()
    whenever(fileDatabaseManager.loadDatabaseFileData("processName", processDescriptor, liveDb1))
      .thenThrow(FileDatabaseException::class.java)
    whenever(fileDatabaseManager.loadDatabaseFileData("processName", processDescriptor, liveDb2))
      .thenThrow(DeviceNotFoundException::class.java)
    offlineModeManager =
      OfflineModeManagerImpl(
        project,
        fileDatabaseManager,
        uiDispatcher,
        isFileDownloadAllowed = { true }
      )

    var handleErrorInvoked = false
    var handleErrorInvokeCount = 0

    // Act
    val flow =
      offlineModeManager.downloadFiles(listOf(liveDb1, liveDb2), processDescriptor, null) { _, _ ->
        handleErrorInvoked = true
        handleErrorInvokeCount += 1
      }
    runDispatching { flow.toList(mutableListOf()) }

    // Assert
    assertTrue(handleErrorInvoked)
    assertEquals(2, handleErrorInvokeCount)

    assertTrue(trackerService.offlineDownloadFailed!!)
    assertEquals(1, trackerService.offlineDownloadFailedCount)
  }

  fun testDoesNotEnterOfflineModeIfUserDoesNotTrustProject() {
    // Prepare
    var errorMessage: String? = null
    offlineModeManager =
      OfflineModeManagerImpl(
        project,
        fileDatabaseManager,
        uiDispatcher,
        isFileDownloadAllowed = { false }
      )

    // Act
    val flow =
      offlineModeManager.downloadFiles(
        listOf(liveDb1, liveDb2, inMemoryDb),
        processDescriptor,
        null
      ) { s, _ -> errorMessage = s }
    val results = runDispatching { flow.toList(mutableListOf()) }

    // Assert
    assertEquals(
      listOf(
        OfflineModeManager.DownloadProgress(
          OfflineModeManager.DownloadState.IN_PROGRESS,
          emptyList(),
          2
        ),
        OfflineModeManager.DownloadProgress(
          OfflineModeManager.DownloadState.COMPLETED,
          emptyList(),
          2
        )
      ),
      results
    )

    assertEquals(
      "For security reasons offline mode is disabled when " +
        "the process being inspected does not correspond to the project open in studio " +
        "or when the project has been generated from a prebuilt apk.",
      errorMessage
    )
  }

  fun testUserIsNotWarnedMultipleTimesAfterTrustingProject() {
    runDispatching {
      var askUserCalled0 = false
      var canDownloadFiles =
        OfflineModeManagerImpl.doIsFileDownloadAllowed(
          project,
          uiDispatcher,
          askUser = {
            askUserCalled0 = true
            false
          }
        )
      assertFalse(canDownloadFiles)
      assertTrue(askUserCalled0)

      var askUserCalled1 = false
      canDownloadFiles =
        OfflineModeManagerImpl.doIsFileDownloadAllowed(
          project,
          uiDispatcher,
          askUser = {
            askUserCalled1 = true
            true
          }
        )
      assertTrue(canDownloadFiles)
      assertTrue(askUserCalled1)

      var askUserCalled2 = false
      canDownloadFiles =
        OfflineModeManagerImpl.doIsFileDownloadAllowed(
          project,
          uiDispatcher,
          askUser = {
            askUserCalled2 = true
            true
          }
        )
      assertTrue(canDownloadFiles)
      assertFalse(askUserCalled2)
    }
  }
}
