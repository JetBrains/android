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

import com.android.ide.common.resources.Locale
import com.android.testutils.MockitoKt.any
import com.android.testutils.MockitoKt.mock
import com.android.testutils.MockitoKt.whenever
import com.android.tools.adtui.swing.popup.JBPopupRule
import com.android.tools.idea.editors.strings.StringResourceEditor
import com.android.tools.idea.editors.strings.StringResourceViewPanel
import com.android.tools.idea.editors.strings.table.StringResourceTable
import com.android.tools.idea.editors.strings.table.StringResourceTableModel
import com.android.tools.idea.editors.strings.table.filter.LocaleColumnFilter
import com.android.tools.idea.editors.strings.table.filter.StringResourceTableColumnFilter
import com.android.tools.idea.testing.AndroidProjectRule
import com.google.common.truth.Truth.assertThat
import com.intellij.icons.AllIcons
import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.actionSystem.PlatformDataKeys
import com.intellij.openapi.actionSystem.Presentation
import com.intellij.openapi.actionSystem.impl.SimpleDataContext
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.wm.IdeFrame
import com.intellij.openapi.wm.WindowManager
import com.intellij.testFramework.EdtRule
import com.intellij.testFramework.RuleChain
import com.intellij.testFramework.RunsInEdt
import com.intellij.testFramework.replaceService
import org.jetbrains.android.facet.AndroidFacet
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.JUnit4
import org.mockito.Mockito.doAnswer
import org.mockito.Mockito.verify
import org.mockito.Mockito.verifyNoInteractions
import org.mockito.Mockito.withSettings
import java.awt.event.MouseEvent
import javax.swing.Icon
import javax.swing.JButton
import javax.swing.JFrame
import javax.swing.JPanel

/** Tests the [FilterLocalesAction] class. */
@RunWith(JUnit4::class)
@RunsInEdt
class FilterLocalesActionTest {
  private val projectRule = AndroidProjectRule.inMemory()
  private val edtRule = EdtRule()
  private val popupRule = JBPopupRule()

  @get:Rule val ruleChain = RuleChain(projectRule, edtRule, popupRule)

  private val stringResourceEditor: StringResourceEditor = mock()
  private val panel: StringResourceViewPanel = mock()
  private val table: StringResourceTable = mock()
  private val model: StringResourceTableModel = mock()
  private val filterLocalesAction = FilterLocalesAction()
  private var columnFilter: StringResourceTableColumnFilter? = null

  private lateinit var facet: AndroidFacet
  private lateinit var event: AnActionEvent

  @Before
  fun setUp() {
    facet = AndroidFacet.getInstance(projectRule.module)!!
    val mouseEvent =
      MouseEvent(
        JPanel(),
        /* id= */ 0,
        /* when= */ 0L,
        /* modifiers= */ 0,
        /* x= */ 0,
        /* y= */ 0,
        /* clickCount= */ 1,
        /* popupTrigger= */ true,
      )

    val dataContext =
      SimpleDataContext.builder()
        .add(CommonDataKeys.PROJECT, projectRule.project)
        .add(PlatformDataKeys.FILE_EDITOR, stringResourceEditor)
        .build()

    event =
      AnActionEvent(
        mouseEvent,
        dataContext,
        "place",
        Presentation(),
        ActionManager.getInstance(),
        0,
      )

    whenever(stringResourceEditor.panel).thenReturn(panel)
    whenever(panel.table).thenReturn(table)
    whenever(table.model).thenReturn(model)

    doAnswer { columnFilter }.whenever(table).columnFilter
    doAnswer {
        columnFilter = it.getArgument(0)
        Unit
      }
      .whenever(table)
      .setColumnFilter(any())

    // Mock the WindowManager so the call to windowManager.getFrame will not return null.
    // A null frame causes ComboBoxAction.actionPerformed(...) to return early before it invokes
    // createActionPopup, and thus will do nothing and can't be tested.
    val windowManager: WindowManager = mock()
    val frame: JFrame = mock(withSettings().extraInterfaces(IdeFrame::class.java))
    whenever((frame as IdeFrame).component).thenReturn(JButton())
    whenever(windowManager.getFrame(projectRule.project)).thenReturn(frame)
    ApplicationManager.getApplication()
      .replaceService(WindowManager::class.java, windowManager, projectRule.testRootDisposable)
  }

  @Test
  fun initialState() {
    assertThat(filterLocalesAction.templateText).isEqualTo("Show All Locales")
  }

  @Test
  fun update_noEditor() {
    val mouseEvent =
      MouseEvent(
        JPanel(),
        /* id= */ 0,
        /* when= */ 0L,
        /* modifiers= */ 0,
        /* x= */ 0,
        /* y= */ 0,
        /* clickCount= */ 1,
        /* popupTrigger= */ true,
      )

    val dataContext =
      SimpleDataContext.builder()
        .add(CommonDataKeys.PROJECT, projectRule.project)
        .build()

    val noEditorEvent =
      AnActionEvent(
        mouseEvent,
        dataContext,
        "place",
        Presentation(),
        ActionManager.getInstance(),
        0,
      )

    filterLocalesAction.update(noEditorEvent)

    verifyNoInteractions(stringResourceEditor, panel, table)
  }

  @Test
  fun update_noRowFilter() {
    filterLocalesAction.update(event)

    assertThat(event.presentation.icon).isNull()
    assertThat(event.presentation.text).isEqualTo("Show All Locales")
  }

  @Test
  fun update_rowFilterPresent() {
    val presentationText = "Some amazing text!"
    columnFilter =
      object : StringResourceTableColumnFilter {
        override fun include(locale: Locale): Boolean = throw NotImplementedError("Not called")

        override fun getDescription(): String = presentationText

        override fun getIcon(): Icon = AllIcons.Idea_logo_welcome
      }

    filterLocalesAction.update(event)

    assertThat(event.presentation.icon).isEqualTo(AllIcons.Idea_logo_welcome)
    assertThat(event.presentation.text).isEqualTo(presentationText)
  }

  @Test
  fun actionPerformed_showAllLocales() {
    filterLocalesAction.actionPerformed(event)

    val popup = popupRule.fakePopupFactory.getPopup<Any>(0)
    assertThat(popup.actions).hasSize(1)
    val selectedAction = popup.actions[0]
    assertThat(selectedAction.templateText).isEqualTo("Show All Locales")

    selectedAction.actionPerformed(event)

    assertThat(columnFilter).isNull()
    verify(table).setColumnFilter(null) // Make sure it's not just still null
  }

  @Test
  fun actionPerformed_showSpecificLocales() {
    whenever(model.columnCount).thenReturn(StringResourceTableModel.FIXED_COLUMN_COUNT + 2)
    whenever(model.getLocale(StringResourceTableModel.FIXED_COLUMN_COUNT)).thenReturn(ARABIC_LOCALE)
    whenever(model.getLocale(StringResourceTableModel.FIXED_COLUMN_COUNT + 1))
      .thenReturn(US_SPANISH_LOCALE)

    filterLocalesAction.actionPerformed(event)

    val popup = popupRule.fakePopupFactory.getPopup<Any>(0)
    assertThat(popup.actions).hasSize(3)

    var selectedAction = popup.actions[1]
    assertThat(selectedAction.templateText).isEqualTo("Show Arabic (ar)")
    assertThat(selectedAction.templatePresentation.icon).isNull()

    selectedAction.actionPerformed(event)

    assertThat(columnFilter).isInstanceOf(LocaleColumnFilter::class.java)
    assertThat(columnFilter!!.getDescription()).isEqualTo("Arabic (ar)")
    assertThat(columnFilter!!.getIcon()).isNull()

    selectedAction = popup.actions[2]
    assertThat(selectedAction.templateText).isEqualTo("Show Spanish (es) in United States (US)")
    assertThat(selectedAction.templatePresentation.icon).isNull()

    selectedAction.actionPerformed(event)

    assertThat(columnFilter).isInstanceOf(LocaleColumnFilter::class.java)
    assertThat(columnFilter!!.getDescription()).isEqualTo("Spanish (es) in United States (US)")
    assertThat(columnFilter!!.getIcon()).isNull()
  }

  companion object {
    private val ARABIC_LOCALE = Locale.create("ar")
    private val US_SPANISH_LOCALE = Locale.create("es-rUS")
  }
}
