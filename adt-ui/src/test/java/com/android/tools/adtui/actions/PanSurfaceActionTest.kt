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
package com.android.tools.adtui.actions

import org.mockito.kotlin.whenever
import com.android.tools.adtui.PANNABLE_KEY
import com.android.tools.adtui.Pannable
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.DataContext
import com.intellij.openapi.actionSystem.impl.SimpleDataContext
import com.intellij.testFramework.ApplicationRule
import com.intellij.testFramework.TestActionEvent
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.JUnit4
import org.mockito.Mockito.mock
import org.mockito.Mockito.verify

@RunWith(JUnit4::class)
class PanSurfaceActionTest {
  @get:Rule val applicationRule = ApplicationRule()

  private lateinit var panAction: PanSurfaceAction
  private var contextHasPannable: Boolean = true
  private val context: DataContext
    get() = if (contextHasPannable) SimpleDataContext.getSimpleContext(PANNABLE_KEY, pannable) else DataContext.EMPTY_CONTEXT
  private val pannable: Pannable = mock(Pannable::class.java)

  private val actionEvent: AnActionEvent
    get() = TestActionEvent.createTestEvent(panAction, context)

  @Before
  fun setUp() {
    panAction = PanSurfaceAction
    whenever(pannable.isPannable).thenReturn(true)
  }

  @Test
  fun testHiddenAction() {
    contextHasPannable = false
    val event = actionEvent
    panAction.update(event)
    assertFalse(event.presentation.isEnabledAndVisible)
  }

  @Test
  fun testDisabledAction() {
    val event = actionEvent
    whenever(pannable.isPannable).thenReturn(false)
    panAction.update(event)
    assertFalse(event.presentation.isEnabled)
  }

  @Test
  fun testEnabledAction() {
    val event = actionEvent
    panAction.update(event)
    assertTrue(event.presentation.isEnabledAndVisible)
  }

  @Test
  fun testIsPanningState() {
    whenever(pannable.isPanning).thenReturn(true)
    assertTrue(panAction.isSelected(actionEvent))
  }

  @Test
  fun testSetSelectedWithTrue() {
    panAction.setSelected(actionEvent, true)
    verify(pannable).isPanning = true
  }

  @Test
  fun testSetSelectedWithFalse() {
    panAction.setSelected(actionEvent, false)
    verify(pannable).isPanning = false
  }
}