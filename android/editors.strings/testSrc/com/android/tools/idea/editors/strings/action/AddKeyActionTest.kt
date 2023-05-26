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

import com.android.testutils.MockitoKt.any
import com.android.testutils.MockitoKt.mock
import com.android.testutils.MockitoKt.whenever
import com.android.tools.adtui.TreeWalker
import com.android.tools.adtui.swing.createModalDialogAndInteractWithIt
import com.android.tools.adtui.swing.enableHeadlessDialogs
import com.android.tools.adtui.swing.getDescendant
import com.android.tools.idea.editors.strings.StringResourceData
import com.android.tools.idea.editors.strings.StringResourceEditor
import com.android.tools.idea.editors.strings.StringResourceViewPanel
import com.android.tools.idea.editors.strings.model.StringResourceKey
import com.android.tools.idea.editors.strings.table.StringResourceTable
import com.android.tools.idea.res.StringResourceWriter
import com.android.tools.idea.testing.AndroidProjectRule
import com.google.common.truth.Truth.assertThat
import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.actionSystem.PlatformDataKeys
import com.intellij.openapi.actionSystem.Presentation
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.DialogWrapper
import com.intellij.testFramework.EdtRule
import com.intellij.testFramework.MapDataContext
import com.intellij.testFramework.RunsInEdt
import com.intellij.ui.EditorTextField
import org.jetbrains.android.facet.AndroidFacet
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.JUnit4
import org.mockito.Mockito.never
import org.mockito.Mockito.verify
import org.mockito.Mockito.verifyNoInteractions
import javax.swing.JButton
import kotlin.test.assertFailsWith
import kotlin.test.fail


/** Test [AddKeyAction] methods. */
@RunWith(JUnit4::class)
@RunsInEdt
class AddKeyActionTest {
  @get:Rule val projectRule = AndroidProjectRule.inMemory()
  @get:Rule val edtRule: EdtRule = EdtRule()

  private val project: Project
    get() = projectRule.project
  private val stringResourceEditor: StringResourceEditor = mock()
  private val panel: StringResourceViewPanel = mock()
  private val stringResourceWriter: StringResourceWriter = mock()
  private val table: StringResourceTable = mock()
  private val data: StringResourceData = mock()
  private val addKeyAction = AddKeyAction(stringResourceWriter)
  private val mapDataContext = MapDataContext()

  private lateinit var facet: AndroidFacet
  private lateinit var event: AnActionEvent

  @Before
  fun setUp() {
    facet = AndroidFacet.getInstance(projectRule.module)!!
    event =
        AnActionEvent(null, mapDataContext, "place", Presentation(), ActionManager.getInstance(), 0)
    mapDataContext.apply {
      put(CommonDataKeys.PROJECT, projectRule.project)
      put(PlatformDataKeys.FILE_EDITOR, stringResourceEditor)
    }

    whenever(stringResourceEditor.panel).thenReturn(panel)
    whenever(panel.table).thenReturn(table)
    whenever(panel.facet).thenReturn(facet)
    whenever(table.data).thenReturn(data)
  }

  @Test
  fun doUpdate_nullData() {
    whenever(table.data).thenReturn(null)

    addKeyAction.update(event)

    assertThat(event.presentation.isEnabled).isFalse()
  }

  @Test
  fun doUpdate() {
    addKeyAction.update(event)

    assertThat(event.presentation.isEnabled).isTrue()
  }

  @Test
  fun actionPerformed_noTableData() {
    // This is protected against by "update", but let's test this code anyway.
    whenever(table.data).thenReturn(null)

    assertFailsWith<IllegalArgumentException> { addKeyAction.actionPerformed(event) }
  }

  @Test
  fun actionPerformed_cancel() {
    enableHeadlessDialogs(project)

    createModalDialogAndInteractWithIt({ addKeyAction.actionPerformed(event) }) {
      assertThat(it.key).isEmpty()
      assertThat(it.defaultValue).isEmpty()
      it.close(DialogWrapper.CANCEL_EXIT_CODE)
    }

    verifyNoInteractions(stringResourceWriter)
    verify(panel, never()).reloadData()
  }

  @Test
  fun actionPerformed_ok_addFails() {
    enableHeadlessDialogs(project)
    whenever(stringResourceWriter.addDefault(any(), any(), any(), any())).thenReturn(false)

    createModalDialogAndInteractWithIt({ addKeyAction.actionPerformed(event) }) {
      it.key = NEW_KEY
      it.defaultValue = NEW_DEFAULT_VALUE
      it.clickOk()
    }

    verify(stringResourceWriter).addDefault(project, StringResourceKey(NEW_KEY), NEW_DEFAULT_VALUE)
    // Add failed, so we shouldn't reload the panel's data.
    verify(panel, never()).reloadData()
  }

  @Test
  fun actionPerformed_ok_addSucceeds() {
    enableHeadlessDialogs(project)
    whenever(stringResourceWriter.addDefault(any(), any(), any(), any())).thenReturn(true)

    createModalDialogAndInteractWithIt({ addKeyAction.actionPerformed(event) }) {
      it.key = NEW_KEY
      it.defaultValue = NEW_DEFAULT_VALUE
      it.clickOk()
    }

    verify(stringResourceWriter).addDefault(project, StringResourceKey(NEW_KEY), NEW_DEFAULT_VALUE)
    verify(panel).reloadData()
  }

  companion object {
    private const val KEY_FIELD_ID = "keyTextField"
    private const val DEFAULT_VALUE_FIELD_ID = "defaultValueTextField"
    private const val OK_BUTTON_ID = "okButton"

    private const val NEW_KEY = "aNewGreatKey"
    private const val NEW_DEFAULT_VALUE = "A new great default value!"

    private var DialogWrapper.key
      get() = keyField.text
      set(value) {
        keyField.text = value
      }
    private var DialogWrapper.defaultValue
      get() = defaultValueField.text
      set(value) {
        defaultValueField.text = value
      }
    private val DialogWrapper.keyField: EditorTextField
      get() = rootPane.getDescendant {it.name == KEY_FIELD_ID}
    private val DialogWrapper.defaultValueField: EditorTextField
      get() = rootPane.getDescendant {it.name == DEFAULT_VALUE_FIELD_ID}
    private fun DialogWrapper.clickOk() {
      rootPane.getDescendant<JButton> {it.name == OK_BUTTON_ID}.doClick()
    }
  }
}
