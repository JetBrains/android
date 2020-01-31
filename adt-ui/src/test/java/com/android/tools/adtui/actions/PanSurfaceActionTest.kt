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

import com.android.tools.adtui.PANNABLE_KEY
import com.android.tools.adtui.Pannable
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.DataContext
import com.intellij.openapi.actionSystem.ex.ActionManagerEx
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.JUnit4
import org.mockito.Mockito.`when`
import org.mockito.Mockito.mock
import org.mockito.Mockito.verify

@RunWith(JUnit4::class)
class PanSurfaceActionTest {
  private lateinit var panAction: PanSurfaceAction

  private val actionManager: ActionManagerEx = mock(ActionManagerEx::class.java)
  private val context: DataContext = mock(DataContext::class.java)
  private val pannable: Pannable = mock(Pannable::class.java)

  private val actionEvent: AnActionEvent
    get() = AnActionEvent(null, context, "PanPlace", panAction.templatePresentation.clone(), actionManager, 0)

  @Before
  fun setUp() {
    panAction = PanSurfaceAction
    `when`(context.getData(PANNABLE_KEY)).thenReturn(pannable)
  }

  @Test
  fun testDisabledAction() {
    `when`(context.getData(PANNABLE_KEY)).thenReturn(null)
    val event = actionEvent
    panAction.update(event)
    assertFalse(event.presentation.isEnabledAndVisible)
  }

  @Test
  fun testEnabledAction() {
    val event = actionEvent
    panAction.update(event)
    assertTrue(event.presentation.isEnabledAndVisible)
  }

  @Test
  fun testIsPanningState() {
    `when`(pannable.isPanning).thenReturn(true)
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