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
package com.android.tools.idea.whatsnew.assistant

import com.android.tools.idea.testing.AndroidProjectRule
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.Presentation
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

class WhatsNewSidePanelActionTest {
  @get:Rule val myRule = AndroidProjectRule.inMemory()

  private lateinit var myPresentation: Presentation
  private lateinit var myEvent: AnActionEvent
  private lateinit var myBrowseToWhatsNewUrl: Runnable

  @Before
  fun mockEvent() {
    myPresentation = Presentation()

    myEvent = mock()
    whenever(myEvent.presentation).thenReturn(myPresentation)
  }

  @Before
  fun mockBrowseToWhatsNewUrl() {
    myBrowseToWhatsNewUrl = mock()
  }

  @Test
  fun updateProjectIsNull() {
    val action = WhatsNewSidePanelAction(myBrowseToWhatsNewUrl)

    action.update(myEvent)
    assertTrue(myPresentation.isEnabled)

    action.actionPerformed(myEvent)
    verify(myBrowseToWhatsNewUrl).run()
  }

  @Test
  fun updateProjectIsNotNull() {
    val action = WhatsNewSidePanelAction(myBrowseToWhatsNewUrl)
    whenever(myEvent.project).thenReturn(myRule.project)

    action.update(myEvent)
    assertTrue(myPresentation.isEnabled)

    action.actionPerformed(myEvent)
    verify(myBrowseToWhatsNewUrl, never()).run()
  }
}
