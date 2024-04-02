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
import com.android.tools.idea.appinspection.inspector.api.AppInspectorMessenger
import com.android.tools.idea.concurrency.AndroidCoroutineScope
import com.android.tools.idea.concurrency.pumpEventsAndWaitForFuture
import com.android.tools.idea.concurrency.pumpEventsAndWaitForFutureException
import com.android.tools.idea.sqlite.databaseConnection.DatabaseConnection
import com.android.tools.idea.sqlite.model.SqliteDatabaseId
import com.google.common.truth.Truth.assertThat
import com.intellij.testFramework.ApplicationRule
import com.intellij.testFramework.DisposableRule
import com.intellij.testFramework.EdtRule
import com.intellij.testFramework.PlatformTestUtil
import com.intellij.testFramework.RuleChain
import com.intellij.testFramework.RunsInEdt
import com.intellij.testFramework.UsefulTestCase.assertSize
import java.util.concurrent.Executors
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.JUnit4

@RunWith(JUnit4::class)
@RunsInEdt
class DatabaseInspectorClientTest {
  private val disposableRule = DisposableRule()

  @get:Rule val rule = RuleChain(ApplicationRule(), disposableRule, EdtRule())

  private val openDatabaseFunction: (SqliteDatabaseId, DatabaseConnection) -> Unit = { _, _ ->
    openDatabaseInvoked = true
  }
  private var openDatabaseInvoked = false

  private val handleErrorFunction: (String) -> Unit = { _ -> handleErrorInvoked = true }
  private var handleErrorInvoked = false

  private val hasDatabasePossiblyChangedFunction: () -> Unit = {
    hasDatabasePossiblyChangedInvoked = true
  }
  private var hasDatabasePossiblyChangedInvoked = false

  private val handleDatabaseClosedFunction: (SqliteDatabaseId) -> Unit = { databaseId ->
    databaseClosedInvocations.add(databaseId)
  }
  private val databaseClosedInvocations = mutableListOf<SqliteDatabaseId>()

  private val executor = Executors.newSingleThreadExecutor()
  private val scope by lazy {
    AndroidCoroutineScope(disposableRule.disposable, executor.asCoroutineDispatcher())
  }

  @Test
  fun testStartTrackingDatabaseConnectionSendsMessage() = runBlocking {
    // Prepare
    val emptyResponse = SqliteInspectorProtocol.Response.newBuilder().build().toByteArray()

    val appInspectorMessenger = FakeAppInspectorMessenger(scope, emptyResponse)
    val databaseInspectorClient = createDatabaseInspectorClient(appInspectorMessenger)

    val trackDatabasesCommand =
      SqliteInspectorProtocol.Command.newBuilder()
        .setTrackDatabases(SqliteInspectorProtocol.TrackDatabasesCommand.getDefaultInstance())
        .build()
        .toByteArray()

    // Act
    databaseInspectorClient.startTrackingDatabaseConnections()

    // Assert
    assertThat(appInspectorMessenger.rawDataSent).isEqualTo(trackDatabasesCommand)
  }

  @Test
  fun testOnDatabaseOpenedEventOpensDatabase() {
    // Prepare
    val databaseOpenEvent =
      SqliteInspectorProtocol.DatabaseOpenedEvent.newBuilder()
        .setDatabaseId(1)
        .setPath("path")
        .build()
    val event =
      SqliteInspectorProtocol.Event.newBuilder().setDatabaseOpened(databaseOpenEvent).build()

    val databaseInspectorClient = createDatabaseInspectorClient()

    // Act
    databaseInspectorClient.onRawEvent(event.toByteArray())
    PlatformTestUtil.dispatchAllEventsInIdeEventQueue()

    // Assert
    assertTrue(openDatabaseInvoked)
  }

  @Test
  fun testRecoverableErrorMessageShowsError() {
    // Prepare
    val errorOccurredEvent =
      SqliteInspectorProtocol.ErrorOccurredEvent.newBuilder()
        .setContent(
          SqliteInspectorProtocol.ErrorContent.newBuilder()
            .setMessage("errorMessage")
            .setRecoverability(
              SqliteInspectorProtocol.ErrorRecoverability.newBuilder()
                .setIsRecoverable(true)
                .build()
            )
            .setStackTrace("stackTrace")
            .build()
        )
        .build()
    val event =
      SqliteInspectorProtocol.Event.newBuilder().setErrorOccurred(errorOccurredEvent).build()

    val databaseInspectorClient = createDatabaseInspectorClient()

    // Act
    databaseInspectorClient.onRawEvent(event.toByteArray())
    PlatformTestUtil.dispatchAllEventsInIdeEventQueue()

    // Assert
    assertTrue(handleErrorInvoked)
  }

  @Test
  fun testUnrecoverableErrorMessageShowsError() {
    // Prepare
    val errorOccurredEvent =
      SqliteInspectorProtocol.ErrorOccurredEvent.newBuilder()
        .setContent(
          SqliteInspectorProtocol.ErrorContent.newBuilder()
            .setMessage("errorMessage")
            .setRecoverability(
              SqliteInspectorProtocol.ErrorRecoverability.newBuilder()
                .setIsRecoverable(false)
                .build()
            )
            .setStackTrace("stackTrace")
            .build()
        )
        .build()
    val event =
      SqliteInspectorProtocol.Event.newBuilder().setErrorOccurred(errorOccurredEvent).build()

    val databaseInspectorClient = createDatabaseInspectorClient()

    // Act
    databaseInspectorClient.onRawEvent(event.toByteArray())
    PlatformTestUtil.dispatchAllEventsInIdeEventQueue()

    // Assert
    assertTrue(handleErrorInvoked)
  }

  @Test
  fun testUnknownRecoverableErrorMessageShowsError() {
    // Prepare
    val errorOccurredEvent =
      SqliteInspectorProtocol.ErrorOccurredEvent.newBuilder()
        .setContent(
          SqliteInspectorProtocol.ErrorContent.newBuilder()
            .setMessage("errorMessage")
            .setRecoverability(SqliteInspectorProtocol.ErrorRecoverability.newBuilder().build())
            .setStackTrace("stackTrace")
            .build()
        )
        .build()
    val event =
      SqliteInspectorProtocol.Event.newBuilder().setErrorOccurred(errorOccurredEvent).build()

    val databaseInspectorClient = createDatabaseInspectorClient()

    // Act
    databaseInspectorClient.onRawEvent(event.toByteArray())
    PlatformTestUtil.dispatchAllEventsInIdeEventQueue()

    // Assert
    assertTrue(handleErrorInvoked)
  }

  @Test
  fun testHasDatabasePossiblyChangedCallsCallback() {
    // Prepare
    val databasePossiblyChangedEvent =
      SqliteInspectorProtocol.DatabasePossiblyChangedEvent.newBuilder().build()
    val event =
      SqliteInspectorProtocol.Event.newBuilder()
        .setDatabasePossiblyChanged(databasePossiblyChangedEvent)
        .build()

    val databaseInspectorClient = createDatabaseInspectorClient()

    // Act
    databaseInspectorClient.onRawEvent(event.toByteArray())
    PlatformTestUtil.dispatchAllEventsInIdeEventQueue()

    // Assert
    assertTrue(hasDatabasePossiblyChangedInvoked)
  }

  @Test
  fun testDatabaseClosedCallsCallback() {
    // Prepare
    val databaseClosedEvent =
      SqliteInspectorProtocol.DatabaseClosedEvent.newBuilder().setDatabaseId(1).build()
    val event =
      SqliteInspectorProtocol.Event.newBuilder().setDatabaseClosed(databaseClosedEvent).build()

    val databaseInspectorClient = createDatabaseInspectorClient()

    // Act
    databaseInspectorClient.onRawEvent(event.toByteArray())
    PlatformTestUtil.dispatchAllEventsInIdeEventQueue()

    // Assert
    assertSize(1, databaseClosedInvocations)
    assertEquals(SqliteDatabaseId.fromLiveDatabase("", 1), databaseClosedInvocations.first())
  }

  @Test
  fun testKeepConnectionOpenSuccess() = runBlocking {
    // Prepare
    val keepDbsOpenResponse =
      SqliteInspectorProtocol.Response.newBuilder()
        .setKeepDatabasesOpen(
          SqliteInspectorProtocol.KeepDatabasesOpenResponse.newBuilder().build()
        )
        .build()
        .toByteArray()

    val appInspectorMessenger = FakeAppInspectorMessenger(scope, keepDbsOpenResponse)
    val databaseInspectorClient = createDatabaseInspectorClient(appInspectorMessenger)

    val trackDatabasesCommand =
      SqliteInspectorProtocol.Command.newBuilder()
        .setKeepDatabasesOpen(
          SqliteInspectorProtocol.KeepDatabasesOpenCommand.newBuilder().setSetEnabled(true).build()
        )
        .build()
        .toByteArray()

    // Act
    val result = pumpEventsAndWaitForFuture(databaseInspectorClient.keepConnectionsOpen(true))

    // Assert
    assertThat(appInspectorMessenger.rawDataSent).isEqualTo(trackDatabasesCommand)
    assertEquals(true, result)
  }

  @Test
  fun testKeepConnectionOpenError() = runBlocking {
    // Prepare
    val keepDbsOpenResponse =
      SqliteInspectorProtocol.Response.newBuilder()
        .setKeepDatabasesOpen(
          SqliteInspectorProtocol.KeepDatabasesOpenResponse.newBuilder().build()
        )
        .setErrorOccurred(
          SqliteInspectorProtocol.ErrorOccurredResponse.newBuilder()
            .setContent(
              SqliteInspectorProtocol.ErrorContent.newBuilder()
                .setRecoverability(
                  SqliteInspectorProtocol.ErrorRecoverability.newBuilder()
                    .setIsRecoverable(true)
                    .build()
                )
                .setMessage("msg")
                .setStackTrace("stk")
                .build()
            )
            .build()
        )
        .build()
        .toByteArray()

    val appInspectorMessenger = FakeAppInspectorMessenger(scope, keepDbsOpenResponse)
    val databaseInspectorClient = createDatabaseInspectorClient(appInspectorMessenger)

    val trackDatabasesCommand =
      SqliteInspectorProtocol.Command.newBuilder()
        .setKeepDatabasesOpen(
          SqliteInspectorProtocol.KeepDatabasesOpenCommand.newBuilder().setSetEnabled(true).build()
        )
        .build()
        .toByteArray()

    // Act
    pumpEventsAndWaitForFutureException(databaseInspectorClient.keepConnectionsOpen(true))
    PlatformTestUtil.dispatchAllEventsInIdeEventQueue()

    // Assert
    assertThat(appInspectorMessenger.rawDataSent).isEqualTo(trackDatabasesCommand)
  }

  @Test
  fun testKeepConnectionOpenNotSetInResponse() = runBlocking {
    // Prepare
    val keepDbsOpenResponse = SqliteInspectorProtocol.Response.newBuilder().build().toByteArray()

    val appInspectorMessenger = FakeAppInspectorMessenger(scope, keepDbsOpenResponse)
    val databaseInspectorClient = createDatabaseInspectorClient(appInspectorMessenger)

    val trackDatabasesCommand =
      SqliteInspectorProtocol.Command.newBuilder()
        .setKeepDatabasesOpen(
          SqliteInspectorProtocol.KeepDatabasesOpenCommand.newBuilder().setSetEnabled(true).build()
        )
        .build()
        .toByteArray()

    // Act
    val result = pumpEventsAndWaitForFuture(databaseInspectorClient.keepConnectionsOpen(true))

    // Assert
    assertThat(appInspectorMessenger.rawDataSent).isEqualTo(trackDatabasesCommand)
    assertEquals(null, result)
  }

  private fun createDatabaseInspectorClient(
    appInspectorMessenger: AppInspectorMessenger = FakeAppInspectorMessenger(scope)
  ): DatabaseInspectorClient {
    return DatabaseInspectorClient(
      appInspectorMessenger,
      disposableRule.disposable,
      handleErrorFunction,
      openDatabaseFunction,
      hasDatabasePossiblyChangedFunction,
      handleDatabaseClosedFunction,
      executor,
      scope,
    )
  }

  private class FakeAppInspectorMessenger(
    override val scope: CoroutineScope,
    private val singleRawCommandResponse: ByteArray = ByteArray(0),
  ) : AppInspectorMessenger {
    lateinit var rawDataSent: ByteArray

    override suspend fun sendRawCommand(rawData: ByteArray): ByteArray {
      rawDataSent = rawData
      return singleRawCommandResponse
    }

    override val eventFlow = emptyFlow<ByteArray>()
  }
}
