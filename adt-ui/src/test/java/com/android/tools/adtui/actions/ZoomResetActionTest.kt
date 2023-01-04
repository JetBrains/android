/*
 * Copyright (C) 2023 The Android Open Source Project
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

import com.android.testutils.MockitoKt.whenever
import com.android.tools.adtui.ZOOMABLE_KEY
import com.android.tools.adtui.Zoomable
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.DataContext
import com.intellij.openapi.actionSystem.Presentation
import com.intellij.openapi.actionSystem.ex.ActionManagerEx
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.JUnit4
import org.mockito.Mockito.mock
import org.mockito.Mockito.verify
import kotlin.test.assertEquals

@RunWith(JUnit4::class)
class ZoomResetActionTest {

  val actionManager: ActionManagerEx = mock(ActionManagerEx::class.java)
  val dataContext: DataContext = mock(DataContext::class.java)
  val zoomable: Zoomable = mock(Zoomable::class.java)

  val zoomAction = ZoomResetAction

  @Before
  fun setUp() {
    whenever(dataContext.getData(ZOOMABLE_KEY)).thenReturn(zoomable)
    whenever(zoomable.canZoomToFit()).thenReturn(true)
  }

  @Test
  fun testZoomReset() {
    val event = getActionEvent("")
    zoomAction.actionPerformed(event)
    verify(zoomable).zoom(zoomAction.myType)
  }

  @Test
  fun testPresentationText() {
    val surfaceEvent = getActionEvent("Surface")
    zoomAction.update(surfaceEvent)
    assertEquals("Reset", surfaceEvent.presentation.text)

    val otherEvent = getActionEvent("")
    zoomAction.update(otherEvent)
    assertEquals("Reset Zoom", otherEvent.presentation.text)
  }

  private fun getActionEvent(place: String): AnActionEvent = AnActionEvent(null, dataContext, place, Presentation(), actionManager, 0)
}
