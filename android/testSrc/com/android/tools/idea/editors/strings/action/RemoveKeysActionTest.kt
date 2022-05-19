/*
 * Copyright (C) 2022 The Android Open Source Project
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
package com.android.tools.idea.editors.strings.action

import com.android.testutils.MockitoKt.mock
import com.android.tools.idea.editors.strings.StringResourceEditor
import com.android.tools.idea.editors.strings.StringResourceViewPanel
import com.android.tools.idea.editors.strings.table.StringResourceTable
import com.android.tools.idea.testing.AndroidProjectRule
import com.google.common.truth.Truth.assertThat
import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.actionSystem.PlatformDataKeys
import com.intellij.openapi.actionSystem.Presentation
import com.intellij.testFramework.MapDataContext
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.JUnit4
import org.mockito.Mockito.`when` as whenever

@RunWith(JUnit4::class)
class RemoveKeysActionTest {
  @get:Rule val projectRule = AndroidProjectRule.inMemory()

  private val stringResourceEditor: StringResourceEditor = mock()
  private val panel: StringResourceViewPanel = mock()
  private val table: StringResourceTable = mock()
  private val mapDataContext = MapDataContext()
  private val removeKeysAction = RemoveKeysAction()
  private lateinit var event: AnActionEvent

  @Before
  fun setUp() {
    event = AnActionEvent(null, mapDataContext, "place", Presentation(), ActionManager.getInstance(), 0)
    mapDataContext.apply {
      put(CommonDataKeys.PROJECT, projectRule.project)
      put(PlatformDataKeys.FILE_EDITOR, stringResourceEditor)
    }

    whenever(stringResourceEditor.panel).thenReturn(panel)
    whenever(panel.table).thenReturn(table)
  }

  @Test
  fun doUpdate_disabled_tooFew() {
    // Do not select any rows.
    whenever(table.selectedRowCount).thenReturn(0)
    removeKeysAction.update(event)
    assertThat(event.presentation.isEnabled).isFalse()
  }

  @Test
  fun doUpdate_enabled() {
    whenever(table.selectedRowCount).thenReturn(1)
    removeKeysAction.update(event)
    assertThat(event.presentation.isEnabled).isTrue()
  }

  @Test
  fun doUpdate_enabled_multiple() {
    whenever(table.selectedRowCount).thenReturn(10)
    removeKeysAction.update(event)
    assertThat(event.presentation.isEnabled).isTrue()
  }

  // TODO(b/232444069): Add tests for the perform method once it is testable.
}
