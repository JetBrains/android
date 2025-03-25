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
package com.android.tools.idea.uibuilder.actions

import com.android.AndroidXConstants
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.DataContext
import com.intellij.openapi.actionSystem.PlatformDataKeys
import com.intellij.openapi.actionSystem.Presentation
import com.intellij.psi.xml.XmlFile
import com.intellij.psi.xml.XmlTag
import org.jetbrains.android.AndroidTestCase
import org.mockito.Mockito
import org.mockito.kotlin.whenever

class LayoutEditorHelpAssistantActionTest : AndroidTestCase() {

  private val event: AnActionEvent = Mockito.mock(AnActionEvent::class.java)
  private val psi: XmlFile = Mockito.mock(XmlFile::class.java)
  private val tag: XmlTag = Mockito.mock(XmlTag::class.java)
  private val presentation = Presentation()

  override fun setUp() {
    super.setUp()
    whenever(event.getData(PlatformDataKeys.PSI_FILE)).thenReturn(psi)
    whenever(event.dataContext).thenReturn(DataContext.EMPTY_CONTEXT)
    whenever(event.project).thenReturn(myModule.project)
    whenever(event.presentation).thenReturn(presentation)
    whenever(psi.rootTag).thenReturn(tag)
    whenever(tag.name).thenReturn("")
  }

  fun testUpdateConstraintlayout() {
    setupTagName(AndroidXConstants.CONSTRAINT_LAYOUT.defaultName())
    val action = LayoutEditorHelpAssistantAction()
    action.update(event)
    assertEquals(LayoutEditorHelpAssistantAction.Type.FULL, action.type)
  }

  fun testUpdateMotionlayout() {
    setupTagName(AndroidXConstants.MOTION_LAYOUT.defaultName())
    val action = LayoutEditorHelpAssistantAction()
    action.update(event)
    assertEquals(LayoutEditorHelpAssistantAction.Type.FULL, action.type)
  }

  private fun setupTagName(name: String) {
    whenever(tag.name).thenReturn(name)
  }
}
