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
import com.android.testutils.MockitoKt.whenever
import com.android.tools.idea.editors.strings.StringResourceEditor
import com.android.tools.idea.editors.strings.StringResourceViewPanel
import com.android.tools.idea.testing.AndroidProjectRule
import com.google.common.truth.Truth.assertThat
import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.actionSystem.PlatformDataKeys
import com.intellij.openapi.actionSystem.Presentation
import com.intellij.openapi.actionSystem.impl.SimpleDataContext
import com.intellij.openapi.project.Project
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.JUnit4
import org.mockito.Mockito.verify


/** Test [AddLocaleAction] methods. */
@RunWith(JUnit4::class)
class ReloadStringResourcesActionTest {
  @get:Rule val projectRule = AndroidProjectRule.inMemory()

  private val project: Project
    get() = projectRule.project
  private val stringResourceEditor: StringResourceEditor = mock()
  private val panel: StringResourceViewPanel = mock()
  private val reloadStringResourcesAction = ReloadStringResourcesAction()
  private lateinit var event: AnActionEvent

  @Before
  fun setUp() {
    val dataContext = SimpleDataContext.builder()
      .add(CommonDataKeys.PROJECT, project)
      .add(PlatformDataKeys.FILE_EDITOR, stringResourceEditor)
      .build()
    event = AnActionEvent(null, dataContext, "place", Presentation(), ActionManager.getInstance(), 0)

    whenever(stringResourceEditor.panel).thenReturn(panel)
  }

  @Test
  fun doUpdate() {
    reloadStringResourcesAction.update(event)

    assertThat(event.presentation.isEnabled).isTrue()
  }

  @Test
  fun actionPerformed() {
    reloadStringResourcesAction.actionPerformed(event)

    verify(panel).reloadData()
  }
}
