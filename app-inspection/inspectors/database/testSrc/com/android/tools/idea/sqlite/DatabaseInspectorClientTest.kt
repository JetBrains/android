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
import androidx.sqlite.inspection.SqliteInspectorProtocol.Command
import androidx.sqlite.inspection.SqliteInspectorProtocol.Response
import androidx.sqlite.inspection.SqliteInspectorProtocol.TrackDatabasesCommand
import androidx.sqlite.inspection.SqliteInspectorProtocol.TrackDatabasesResponse
import com.android.tools.idea.appinspection.inspector.api.AppInspectionIdeServices.Severity
import com.android.tools.idea.appinspection.inspector.api.AppInspectionIdeServices.Severity.ERROR
import com.android.tools.idea.appinspection.inspector.api.AppInspectionIdeServicesAdapter
import com.android.tools.idea.appinspection.inspector.api.AppInspectorMessenger
import com.android.tools.idea.concurrency.AndroidCoroutineScope
import com.android.tools.idea.concurrency.pumpEventsAndWaitForFuture
import com.android.tools.idea.concurrency.pumpEventsAndWaitForFutureException
import com.android.tools.idea.flags.StudioFlags
import com.android.tools.idea.sqlite.databaseConnection.DatabaseConnection
import com.android.tools.idea.sqlite.model.SqliteDatabaseId
import com.android.tools.idea.sqlite.settings.DatabaseInspectorProjectSettings
import com.android.tools.idea.testing.WaitForIndexRule
import com.android.tools.idea.testing.flags.overrideForTest
import com.google.common.truth.Truth.assertThat
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.testFramework.DisposableRule
import com.intellij.testFramework.EdtRule
import com.intellij.testFramework.PlatformTestUtil
import com.intellij.testFramework.ProjectRule
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
  private val projectRule = ProjectRule()
  private val disposableRule = DisposableRule()
  private val project
    get() = projectRule.project

  private val disposable
    get() = disposableRule.disposable

  @get:Rule
  val rule = RuleChain(projectRule, WaitForIndexRule(projectRule), disposableRule, EdtRule())

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
  private val scope by lazy { AndroidCoroutineScope(disposable, executor.asCoroutineDispatcher()) }
  private val ideServices = TestIdeServices()

  @Test
  fun testStartTrackingDatabaseConnectionSendsMessage() = runBlocking {
    // Prepare
    val emptyResponse = Response.newBuilder()

    val appInspectorMessenger = FakeAppInspectorMessenger(scope, emptyResponse)
    val databaseInspectorClient = createDatabaseInspectorClient(appInspectorMessenger)

    val trackDatabasesCommand =
      Command.newBuilder().setTrackDatabases(TrackDatabasesCommand.getDefaultInstance()).build()

    // Act
    databaseInspectorClient.startTrackingDatabaseConnections()

    // Assert
    assertThat(appInspectorMessenger.command).isEqualTo(trackDatabasesCommand)
    assertThat(ideServices.notifications).isEmpty()
  }

  @Test
  fun testStartTrackingDatabaseConnectionSendsMessage_withAdditionalDatabase() = runBlocking {
    // Prepare
    val response =
      Response.newBuilder()
        .setTrackDatabases(
          TrackDatabasesResponse.newBuilder()
            .addTrackedAdditionalDrivers(
              SqliteInspectorProtocol.AdditionalDriver.newBuilder()
                .setDriverClass("Driver")
                .setConnectionClass("Connection")
            )
        )
    StudioFlags.APP_INSPECTION_ENABLE_ADDITIONAL_SQL_DRIVER.overrideForTest(true, disposable)
    val settings = DatabaseInspectorProjectSettings.getInstance(project)
    settings.additionalDriverClass = "Driver"
    settings.additionalConnectionClass = "Connection"

    val appInspectorMessenger = FakeAppInspectorMessenger(scope, response)
    val databaseInspectorClient = createDatabaseInspectorClient(appInspectorMessenger)

    val trackDatabasesCommand =
      Command.newBuilder()
        .setTrackDatabases(
          TrackDatabasesCommand.newBuilder()
            .addAdditionalDrivers(
              SqliteInspectorProtocol.AdditionalDriver.newBuilder()
                .setDriverClass("Driver")
                .setConnectionClass("Connection")
            )
        )
        .build()

    // Act
    databaseInspectorClient.startTrackingDatabaseConnections()

    // Assert
    assertThat(appInspectorMessenger.command).isEqualTo(trackDatabasesCommand)
    assertThat(ideServices.notifications).isEmpty()
  }

  @Test
  fun testStartTrackingDatabaseConnectionSendsMessage_withAdditionalDatabase_noFlagOverride() =
    runBlocking {
      // Prepare
      val emptyResponse = Response.newBuilder()
      val settings = DatabaseInspectorProjectSettings.getInstance(project)
      settings.additionalDriverClass = "Driver"
      settings.additionalConnectionClass = "Connection"

      val appInspectorMessenger = FakeAppInspectorMessenger(scope, emptyResponse)
      val databaseInspectorClient = createDatabaseInspectorClient(appInspectorMessenger)

      val trackDatabasesCommand =
        Command.newBuilder().setTrackDatabases(TrackDatabasesCommand.getDefaultInstance()).build()

      // Act
      databaseInspectorClient.startTrackingDatabaseConnections()

      // Assert
      assertThat(appInspectorMessenger.command).isEqualTo(trackDatabasesCommand)
      assertThat(ideServices.notifications).isEmpty()
    }

  @Test
  fun testStartTrackingDatabaseConnectionSendsMessage_withAdditionalDatabase_notifiesError(): Unit =
    runBlocking {
      // Prepare
      val emptyResponse = Response.newBuilder()
      StudioFlags.APP_INSPECTION_ENABLE_ADDITIONAL_SQL_DRIVER.overrideForTest(true, disposable)
      val settings = DatabaseInspectorProjectSettings.getInstance(project)
      settings.additionalDriverClass = "Driver"
      settings.additionalConnectionClass = "Connection"

      val appInspectorMessenger = FakeAppInspectorMessenger(scope, emptyResponse)
      val databaseInspectorClient = createDatabaseInspectorClient(appInspectorMessenger)

      val trackDatabasesCommand =
        Command.newBuilder()
          .setTrackDatabases(
            TrackDatabasesCommand.newBuilder()
              .addAdditionalDrivers(
                SqliteInspectorProtocol.AdditionalDriver.newBuilder()
                  .setDriverClass("Driver")
                  .setConnectionClass("Connection")
              )
          )
          .build()

      // Act
      databaseInspectorClient.startTrackingDatabaseConnections()

      // Assert
      assertThat(appInspectorMessenger.command).isEqualTo(trackDatabasesCommand)
      assertThat(ideServices.notifications)
        .containsExactly(
          TestNotification("Database Inspector", "Failed to inject tracker into Driver", ERROR)
        )
    }

  @Test
  fun testStartTrackingDatabaseConnectionSendsMessage_ignoreFrameworkApi() = runBlocking {
    StudioFlags.APP_INSPECTION_ENABLE_ADDITIONAL_SQL_DRIVER.overrideForTest(true, disposable)
    val settings = DatabaseInspectorProjectSettings.getInstance(project)
    settings.isIgnoreFrameworkApi = true

    val appInspectorMessenger = FakeAppInspectorMessenger(scope, Response.newBuilder())
    val databaseInspectorClient = createDatabaseInspectorClient(appInspectorMessenger)

    val trackDatabasesCommand =
      Command.newBuilder()
        .setTrackDatabases(TrackDatabasesCommand.newBuilder().setIgnoreFrameworkApi(true))
        .build()

    // Act
    databaseInspectorClient.startTrackingDatabaseConnections()

    // Assert
    assertThat(appInspectorMessenger.command).isEqualTo(trackDatabasesCommand)
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
      Response.newBuilder()
        .setKeepDatabasesOpen(SqliteInspectorProtocol.KeepDatabasesOpenResponse.newBuilder())

    val appInspectorMessenger = FakeAppInspectorMessenger(scope, keepDbsOpenResponse)
    val databaseInspectorClient = createDatabaseInspectorClient(appInspectorMessenger)

    val trackDatabasesCommand =
      Command.newBuilder()
        .setKeepDatabasesOpen(
          SqliteInspectorProtocol.KeepDatabasesOpenCommand.newBuilder().setSetEnabled(true).build()
        )
        .build()

    // Act
    val result = pumpEventsAndWaitForFuture(databaseInspectorClient.keepConnectionsOpen(true))

    // Assert
    assertThat(appInspectorMessenger.command).isEqualTo(trackDatabasesCommand)
    assertEquals(true, result)
  }

  @Test
  fun testKeepConnectionOpenError() = runBlocking {
    // Prepare
    val keepDbsOpenResponse =
      Response.newBuilder()
        .setKeepDatabasesOpen(
          SqliteInspectorProtocol.KeepDatabasesOpenResponse.newBuilder().build()
        )
        .setErrorOccurred(
          SqliteInspectorProtocol.ErrorOccurredResponse.newBuilder()
            .setContent(
              SqliteInspectorProtocol.ErrorContent.newBuilder()
                .setRecoverability(
                  SqliteInspectorProtocol.ErrorRecoverability.newBuilder().setIsRecoverable(true)
                )
                .setMessage("msg")
                .setStackTrace("stk")
            )
        )

    val appInspectorMessenger = FakeAppInspectorMessenger(scope, keepDbsOpenResponse)
    val databaseInspectorClient = createDatabaseInspectorClient(appInspectorMessenger)

    val trackDatabasesCommand =
      Command.newBuilder()
        .setKeepDatabasesOpen(
          SqliteInspectorProtocol.KeepDatabasesOpenCommand.newBuilder().setSetEnabled(true).build()
        )
        .build()

    // Act
    pumpEventsAndWaitForFutureException(databaseInspectorClient.keepConnectionsOpen(true))
    PlatformTestUtil.dispatchAllEventsInIdeEventQueue()

    // Assert
    assertThat(appInspectorMessenger.command).isEqualTo(trackDatabasesCommand)
  }

  @Test
  fun testKeepConnectionOpenNotSetInResponse() = runBlocking {
    // Prepare
    val keepDbsOpenResponse = Response.newBuilder()

    val appInspectorMessenger = FakeAppInspectorMessenger(scope, keepDbsOpenResponse)
    val databaseInspectorClient = createDatabaseInspectorClient(appInspectorMessenger)

    val trackDatabasesCommand =
      Command.newBuilder()
        .setKeepDatabasesOpen(
          SqliteInspectorProtocol.KeepDatabasesOpenCommand.newBuilder().setSetEnabled(true).build()
        )
        .build()

    // Act
    val result = pumpEventsAndWaitForFuture(databaseInspectorClient.keepConnectionsOpen(true))

    // Assert
    assertThat(appInspectorMessenger.command).isEqualTo(trackDatabasesCommand)
    assertEquals(null, result)
  }

  private fun createDatabaseInspectorClient(
    appInspectorMessenger: AppInspectorMessenger = FakeAppInspectorMessenger(scope)
  ): DatabaseInspectorClient {
    return DatabaseInspectorClient(
      project,
      appInspectorMessenger,
      disposable,
      handleErrorFunction,
      openDatabaseFunction,
      hasDatabasePossiblyChangedFunction,
      handleDatabaseClosedFunction,
      executor,
      ideServices,
      scope,
    )
  }

  private class FakeAppInspectorMessenger(
    override val scope: CoroutineScope,
    private val commandResponse: Response.Builder = Response.newBuilder(),
  ) : AppInspectorMessenger {
    lateinit var command: Command

    override suspend fun sendRawCommand(rawData: ByteArray): ByteArray {
      command = Command.parseFrom(rawData)
      return commandResponse.build().toByteArray()
    }

    override val eventFlow = emptyFlow<ByteArray>()
  }

  private data class TestNotification(
    val title: String,
    val content: String,
    val severity: Severity,
  )

  private class TestIdeServices : AppInspectionIdeServicesAdapter() {
    val notifications = mutableListOf<TestNotification>()

    override fun showNotification(
      content: String,
      title: String,
      severity: Severity,
      action: AnAction?,
    ) {
      notifications.add(TestNotification(title, content, severity))
    }
  }
}
