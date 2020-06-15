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
package com.android.tools.idea.sqlite.controllers

import com.android.testutils.MockitoKt.any
import com.android.tools.idea.sqlite.mocks.MockParametersBindingDialogView
import com.android.tools.idea.sqlite.mocks.MockDatabaseInspectorViewsFactory
import com.android.tools.idea.sqlite.model.SqliteStatement
import com.android.tools.idea.sqlite.ui.parametersBinding.ParametersBindingDialogView
import com.intellij.openapi.util.Disposer
import com.intellij.testFramework.PlatformTestCase
import org.mockito.InOrder
import org.mockito.Mockito
import org.mockito.Mockito.spy

class ParametersBindingControllerTest : PlatformTestCase() {
  private lateinit var controller: ParametersBindingController
  private lateinit var view: MockParametersBindingDialogView
  private lateinit var orderVerifier: InOrder
  private lateinit var ranStatements: MutableList<SqliteStatement>

  override fun setUp() {
    super.setUp()

    val factory = MockDatabaseInspectorViewsFactory()
    view = spy(factory.parametersBindingDialogView)
    orderVerifier = Mockito.inOrder(view)

    ranStatements = mutableListOf()
    controller = ParametersBindingController(
      view, "select * from Foo", listOf("param1", "param2")
    ) { ranStatements.add(it) }
    Disposer.register(project, controller)
  }

  fun testSetup() {
    // Prepare

    // Act
    controller.setUp()

    // Assert
    orderVerifier.verify(view).addListener(any(ParametersBindingDialogView.Listener::class.java))
    orderVerifier.verify(view).showNamedParameters(setOf("param1", "param2"))
  }

  fun testRunStatement() {
    // Prepare

    // Act
    controller.setUp()
    val listener = view.listeners.first()
    listener.bindingCompletedInvoked(mapOf("param1" to "1", "param2" to "2"))

    // Assert
    assertContainsElements(ranStatements, listOf(SqliteStatement("select * from Foo", listOf("1", "2"))))
  }

  fun testRunStatementWithRepeatedNamedParameter() {
    // Prepare
    controller = ParametersBindingController(
      view, "select * from Foo", listOf("param1", "param1")
    ) { ranStatements.add(it) }

    // Act
    controller.setUp()
    val listener = view.listeners.first()
    listener.bindingCompletedInvoked(mapOf("param1" to "1"))

    // Assert
    assertContainsElements(ranStatements, listOf(SqliteStatement("select * from Foo", listOf("1", "1"))))
  }
}