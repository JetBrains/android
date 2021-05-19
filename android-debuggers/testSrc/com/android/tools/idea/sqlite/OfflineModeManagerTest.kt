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
import com.android.tools.idea.appinspection.inspector.api.process.ProcessDescriptor
import com.android.tools.idea.sqlite.mocks.FakeDatabaseInspectorAnalyticsTracker
import com.android.tools.idea.sqlite.mocks.FakeFileDatabaseManager
import com.android.tools.idea.sqlite.model.SqliteDatabaseId
import com.android.tools.idea.sqlite.utils.initProjectSystemService
import com.android.tools.idea.testing.runDispatching
import com.intellij.testFramework.LightPlatformTestCase
import com.intellij.testFramework.registerServiceInstance
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.toList
import org.jetbrains.android.facet.AndroidFacet
import org.jetbrains.android.facet.AndroidFacetConfiguration
import org.mockito.Mockito.`when`
import kotlin.coroutines.EmptyCoroutineContext

class OfflineModeManagerTest : LightPlatformTestCase() {

  private lateinit var processDescriptor: ProcessDescriptor

  private lateinit var fileDatabaseManager: FakeFileDatabaseManager
  private lateinit var offlineModeManager: OfflineModeManager

  private val liveDb1 = SqliteDatabaseId.fromLiveDatabase("db1", 1) as SqliteDatabaseId.LiveSqliteDatabaseId
  private val liveDb2 = SqliteDatabaseId.fromLiveDatabase("db2", 2) as SqliteDatabaseId.LiveSqliteDatabaseId
  private val inMemoryDb = SqliteDatabaseId.fromLiveDatabase(":memory: { 123 }", 3) as SqliteDatabaseId.LiveSqliteDatabaseId

  private lateinit var trackerService: FakeDatabaseInspectorAnalyticsTracker

  override fun setUp() {
    super.setUp()

    trackerService = FakeDatabaseInspectorAnalyticsTracker()
    project.registerServiceInstance(DatabaseInspectorAnalyticsTracker::class.java, trackerService)

    fileDatabaseManager = FakeFileDatabaseManager()
    offlineModeManager = OfflineModeManagerImpl(project, fileDatabaseManager)

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
    // Prepare
    initProjectSystemService(project, testRootDisposable, listOf(AndroidFacet(module, "facet", AndroidFacetConfiguration())))

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
    initProjectSystemService(project, testRootDisposable, listOf(AndroidFacet(module, "facet", AndroidFacetConfiguration())))

    val scope = CoroutineScope(EmptyCoroutineContext)
    var hasBeenCanceled = false
    val downloadFirstFile = CompletableDeferred<Unit>()

    // Act
    val flow = offlineModeManager.downloadFiles(listOf(liveDb1, liveDb2), processDescriptor, null) { _, _ -> }
    val job = scope.launch {
      try {
        flow.onEach {
          // get first one and delay others
          if (it.filesDownloaded.isNotEmpty()) CompletableDeferred<Unit>().await()
          else downloadFirstFile.complete(Unit)
        }.toList(mutableListOf())
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
    initProjectSystemService(project, testRootDisposable, listOf(AndroidFacet(module, "facet", AndroidFacetConfiguration())))

    val fileDatabaseManager = mock<FileDatabaseManager>()
    `when`(fileDatabaseManager.loadDatabaseFileData("processName", processDescriptor, liveDb1))
      .thenThrow(FileDatabaseException::class.java)
    `when`(fileDatabaseManager.loadDatabaseFileData("processName", processDescriptor, liveDb2))
      .thenThrow(DeviceNotFoundException::class.java)
    offlineModeManager = OfflineModeManagerImpl(project, fileDatabaseManager)

    var handleErrorInvoked = false
    var handleErrorInvokeCount = 0

    // Act
    val flow = offlineModeManager.downloadFiles(listOf(liveDb1, liveDb2), processDescriptor, null) { _, _ ->
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

  fun testDoesNotEnterOfflineModeIfPackageDoesNotHaveAndroidFacet() {
    // Prepare
    initProjectSystemService(project, testRootDisposable, emptyList())
    var errorMessage: String? = null

    // Act
    val flow = offlineModeManager.downloadFiles(listOf(liveDb1, liveDb2, inMemoryDb), processDescriptor, null) { s, _ ->
      errorMessage = s
    }
    val results = runDispatching { flow.toList(mutableListOf()) }

    // Assert
    assertEquals(
      listOf(
        OfflineModeManager.DownloadProgress(OfflineModeManager.DownloadState.IN_PROGRESS, emptyList(), 2),
        OfflineModeManager.DownloadProgress(OfflineModeManager.DownloadState.COMPLETED, emptyList(), 2)
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
}