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
package com.android.tools.idea.sqlite

import androidx.sqlite.inspection.SqliteInspectorProtocol
import com.android.tools.idea.appinspection.inspector.api.AppInspectorClient
import com.intellij.testFramework.PlatformTestCase
import com.intellij.testFramework.PlatformTestUtil
import org.mockito.Mockito.mock
import org.mockito.Mockito.verify

class DatabaseInspectorClientTest : PlatformTestCase() {
  private lateinit var databaseInspectorClient: DatabaseInspectorClient
  private lateinit var mockMessenger: AppInspectorClient.CommandMessenger

  private lateinit var openDatabaseFunction: (AppInspectorClient.CommandMessenger, Int, String) -> Unit
  private var openDatabaseInvoked = false

  private lateinit var handleErrorFunction: (String) -> Unit
  private var handleErrorInvoked = false

  override fun setUp() {
    super.setUp()

    mockMessenger = mock(AppInspectorClient.CommandMessenger::class.java)

    openDatabaseInvoked = false
    openDatabaseFunction = { _, _, _ -> openDatabaseInvoked = true }

    handleErrorInvoked = false
    handleErrorFunction = { _ -> handleErrorInvoked = true }

    databaseInspectorClient = DatabaseInspectorClient(mockMessenger, handleErrorFunction, openDatabaseFunction)
  }

  fun testStartTrackingDatabaseConnectionSendsMessage() {
    // Prepare
    val trackDatabasesCommand = SqliteInspectorProtocol.Command.newBuilder()
      .setTrackDatabases(SqliteInspectorProtocol.TrackDatabasesCommand.getDefaultInstance())
      .build()
      .toByteArray()

    // Act
    databaseInspectorClient.startTrackingDatabaseConnections()

    // Assert
    verify(mockMessenger).sendRawCommand(trackDatabasesCommand)
  }

  fun testOnDatabaseOpenedEventOpensDatabase() {
    // Prepare
    val databaseOpenEvent = SqliteInspectorProtocol.DatabaseOpenedEvent.newBuilder().setDatabaseId(1).setName("name").build()
    val event = SqliteInspectorProtocol.Event.newBuilder().setDatabaseOpened(databaseOpenEvent).build()

    // Act
    databaseInspectorClient.eventListener.onRawEvent(event.toByteArray())
    PlatformTestUtil.dispatchAllEventsInIdeEventQueue()

    // Assert
    assertTrue(openDatabaseInvoked)
  }

  fun testErrorMessageShowsError() {
    // Prepare
    val errorOccurredEvent = SqliteInspectorProtocol.ErrorOccurredEvent.newBuilder().setContent(
      SqliteInspectorProtocol.ErrorContent.newBuilder()
        .setMessage("errorMessage")
        .setIsRecoverable(true)
        .setStackTrace("stackTrace")
        .build()
    ).build()
    val event = SqliteInspectorProtocol.Event.newBuilder().setErrorOccurred(errorOccurredEvent).build()

    // Act
    databaseInspectorClient.eventListener.onRawEvent(event.toByteArray())
    PlatformTestUtil.dispatchAllEventsInIdeEventQueue()

    // Assert
    assertTrue(handleErrorInvoked)
  }
}