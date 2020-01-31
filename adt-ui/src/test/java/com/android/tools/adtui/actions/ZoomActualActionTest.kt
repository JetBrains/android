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

import com.android.tools.adtui.ZOOMABLE_KEY
import com.android.tools.adtui.Zoomable
import com.google.common.truth.Truth.assertThat
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.DataContext
import com.intellij.openapi.actionSystem.ex.ActionManagerEx

import org.junit.Before
import org.junit.Test

import org.junit.runner.RunWith
import org.junit.runners.JUnit4
import org.mockito.Mockito.`when`
import org.mockito.Mockito.mock
import org.mockito.Mockito.verify

@RunWith(JUnit4::class)
class ZoomActualActionTest {

  val actionManager: ActionManagerEx = mock(ActionManagerEx::class.java)
  val dataContext: DataContext = mock(DataContext::class.java)
  val zoomable: Zoomable = mock(Zoomable::class.java)

  val zoomAction = ZoomActualAction

  @Before
  fun setUp() {
    `when`(dataContext.getData(ZOOMABLE_KEY)).thenReturn(zoomable)
    `when`(zoomable.canZoomToActual()).thenReturn(true)
  }

  @Test
  fun testZoomActualEnabled() {
    val event = getActionEvent()
    zoomAction.update(event)
    assertThat(event.presentation.isEnabled).isTrue()
    `when`(zoomable.canZoomToActual()).thenReturn(false)
    zoomAction.update(event)
    assertThat(event.presentation.isEnabled).isFalse()
  }

  @Test
  fun testZoomActual() {
    val event = getActionEvent()
    zoomAction.actionPerformed(event)
    verify(zoomable).zoom(ZoomType.ACTUAL)
  }

  private fun getActionEvent(): AnActionEvent = AnActionEvent(null, dataContext, "Place", zoomAction.templatePresentation.clone(),
                                                              actionManager, 0)
}