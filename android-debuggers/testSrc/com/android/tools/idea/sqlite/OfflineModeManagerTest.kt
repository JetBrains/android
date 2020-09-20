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

import com.android.testutils.MockitoKt.any
import com.android.testutils.MockitoKt.mock
import com.android.tools.idea.appinspection.inspector.api.process.ProcessDescriptor
import com.android.tools.idea.sqlite.mocks.FakeDatabaseInspectorAnalyticsTracker
import com.android.tools.idea.sqlite.mocks.FakeFileDatabaseManager
import com.android.tools.idea.sqlite.model.SqliteDatabaseId
import com.android.tools.idea.testing.runDispatching
import com.intellij.testFramework.LightPlatformTestCase
import com.intellij.testFramework.registerServiceInstance
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.mockito.Mockito.`when`
import kotlin.coroutines.EmptyCoroutineContext

class OfflineModeManagerTest : LightPlatformTestCase() {

  private lateinit var processDescriptor: ProcessDescriptor

  private lateinit var fileDatabaseManager: FakeFileDatabaseManager
  private lateinit var offlineModeManager: OfflineModeManager

  private val liveDb1 = SqliteDatabaseId.fromLiveDatabase("db1", 1)
  private val liveDb2 = SqliteDatabaseId.fromLiveDatabase("db2", 2)
  private val inMemoryDb = SqliteDatabaseId.fromLiveDatabase(":memory: { 123 }", 3)

  private lateinit var trackerService: FakeDatabaseInspectorAnalyticsTracker

  override fun setUp() {
    super.setUp()

    trackerService = FakeDatabaseInspectorAnalyticsTracker()
    project.registerServiceInstance(DatabaseInspectorAnalyticsTracker::class.java, trackerService)

    fileDatabaseManager = FakeFileDatabaseManager()
    offlineModeManager = OfflineModeManager(project, fileDatabaseManager)

    processDescriptor = object : ProcessDescriptor {
      override val manufacturer = "manufacturer"
      override val model = "model"
      override val serial = "serial"
      override val processName = "processName"
      override val isEmulator = false
      override val isRunning = false
    }
  }

  fun testDownloadFiles() {
    // Act
    val flow = offlineModeManager.downloadFiles(listOf(liveDb1, liveDb2, inMemoryDb), processDescriptor, null) { _, _ -> }
    val results = runDispatching { flow.toList(mutableListOf()) }

    // Assert
    assertEquals(
      listOf(
        OfflineModeManager.DownloadProgress(OfflineModeManager.DownloadState.IN_PROGRESS, emptyList(), 2),
        OfflineModeManager.DownloadProgress(OfflineModeManager.DownloadState.IN_PROGRESS, listOf(fileDatabaseManager.databaseFileData), 2),
        OfflineModeManager.DownloadProgress(OfflineModeManager.DownloadState.IN_PROGRESS, listOf(fileDatabaseManager.databaseFileData, fileDatabaseManager.databaseFileData), 2),
        OfflineModeManager.DownloadProgress(OfflineModeManager.DownloadState.COMPLETED, listOf(fileDatabaseManager.databaseFileData, fileDatabaseManager.databaseFileData), 2)
      ),
      results
    )
  }

  fun testDownloadFilesCanceled() {
    // Prepare
    val scope = CoroutineScope(EmptyCoroutineContext)

    var results: List<OfflineModeManager.DownloadProgress>? = null
    var hasBeenCanceled = false

    // Act
    val flow = offlineModeManager.downloadFiles(listOf(liveDb1, liveDb2), processDescriptor, null) { _, _ -> }
    val job = scope.launch {
      try {
        val deferred = CompletableDeferred<Unit>()
        var i = 0
        // immediately get first one and delay others
        results = flow.onEach { if (i > 0) deferred.await() else i++ }.toList(mutableListOf())
      } catch (e: CancellationException) {
        hasBeenCanceled = true
      }
    }
    runDispatching { job.cancelAndJoin() }

    // Assert
    assertNull(results)
    assertTrue(hasBeenCanceled)

    assertEquals(listOf(fileDatabaseManager.databaseFileData), fileDatabaseManager.cleanedUpFiles)
  }

  fun testDownloadFailedMetrics() = runBlocking {
    // Prepare
    val fileDatabaseManager = mock<FileDatabaseManager>()
    `when`(fileDatabaseManager.loadDatabaseFileData(any(), any(), any())).thenThrow(FileDatabaseException::class.java)
    offlineModeManager = OfflineModeManager(project, fileDatabaseManager)

    var handleErrorInvoked = false

    // Act
    val flow = offlineModeManager.downloadFiles(listOf(liveDb1), processDescriptor, null) { _, _ -> handleErrorInvoked = true }
    runDispatching { flow.toList(mutableListOf()) }

    // Assert
    assertTrue(handleErrorInvoked)
    assertTrue(trackerService.offlineDownloadFailed!!)
  }
}