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
package com.android.tools.idea.sqlite.controllers

import com.android.tools.idea.sqlite.databaseConnection.DatabaseConnection
import com.android.tools.idea.sqlite.model.SqliteStatement
import com.android.tools.idea.sqlite.ui.logtab.LogTabView
import com.intellij.openapi.application.ApplicationManager
import com.intellij.testFramework.LightPlatformTestCase
import com.intellij.testFramework.PlatformTestUtil
import org.mockito.Mockito.mock
import org.mockito.Mockito.verify

class LogTabControllerTest : LightPlatformTestCase() {
  private lateinit var controller: LogTabController
  private lateinit var mockLogTabView: LogTabView

  override fun setUp() {
    super.setUp()
    mockLogTabView = mock(LogTabView::class.java)
    controller = LogTabController(mockLogTabView)
  }

  fun testExecutionSuccessfulIsLogged() {
    // Act
    ApplicationManager.getApplication()
      .messageBus.syncPublisher(DatabaseConnection.TOPIC).onSqliteStatementExecutionSuccess(SqliteStatement("statement"))
    PlatformTestUtil.dispatchAllEventsInIdeEventQueue()

    // Assert
    verify(mockLogTabView).log("Execution successful: statement")
  }

  fun testExecutionFailedIsLogged() {
    // Prepare

    // Act
    ApplicationManager.getApplication()
      .messageBus.syncPublisher(DatabaseConnection.TOPIC).onSqliteStatementExecutionFailed(SqliteStatement("statement"))
    PlatformTestUtil.dispatchAllEventsInIdeEventQueue()

    // Assert
    verify(mockLogTabView).logError("Execution failed: statement")
  }
}