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
import com.android.tools.adtui.swing.createModalDialogAndInteractWithIt
import com.android.tools.adtui.swing.enableHeadlessDialogs
import com.android.tools.adtui.swing.getDescendant
import com.android.tools.adtui.swing.popup.JBPopupRule
import com.android.tools.idea.editors.strings.StringResourceEditor
import com.android.tools.idea.editors.strings.StringResourceViewPanel
import com.android.tools.idea.editors.strings.table.StringResourceTable
import com.android.tools.idea.editors.strings.table.StringResourceTableModel
import com.android.tools.idea.editors.strings.table.filter.NeedsTranslationForLocaleRowFilter
import com.android.tools.idea.editors.strings.table.filter.NeedsTranslationsRowFilter
import com.android.tools.idea.editors.strings.table.filter.StringResourceTableRowFilter
import com.android.tools.idea.editors.strings.table.filter.TextRowFilter
import com.android.tools.idea.editors.strings.table.filter.TranslatableRowFilter
import com.android.tools.idea.rendering.FlagManager
import com.android.tools.idea.testing.AndroidProjectRule
import com.google.common.truth.Truth.assertThat
import com.intellij.icons.AllIcons
import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.actionSystem.PlatformDataKeys
import com.intellij.openapi.actionSystem.Presentation
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.ui.DialogWrapper
import com.intellij.openapi.wm.WindowManager
import com.intellij.openapi.wm.ex.WindowManagerEx
import com.intellij.openapi.wm.impl.IdeFrameImpl
import com.intellij.testFramework.EdtRule
import com.intellij.testFramework.MapDataContext
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
import org.mockito.Mockito.never
import org.mockito.Mockito.verify
import org.mockito.Mockito.verifyNoInteractions
import java.awt.event.MouseEvent
import javax.swing.Icon
import javax.swing.JButton
import javax.swing.JPanel
import javax.swing.JTextField

/** Tests the [FilterKeysAction] class. */
@RunWith(JUnit4::class)
@RunsInEdt
class FilterKeysActionTest {
  private val projectRule = AndroidProjectRule.inMemory()
  private val edtRule = EdtRule()
  private val popupRule = JBPopupRule()

  @get:Rule
  val ruleChain = RuleChain(projectRule, edtRule, popupRule)

  private val stringResourceEditor: StringResourceEditor = mock()
  private val panel: StringResourceViewPanel = mock()
  private val table: StringResourceTable = mock()
  private val model: StringResourceTableModel = mock()
  private val mapDataContext = MapDataContext()
  private val filterKeysAction = FilterKeysAction()
  private var rowFilter: StringResourceTableRowFilter? = null

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
        /* popupTrigger= */ true)

    event = AnActionEvent(mouseEvent, mapDataContext, "place", Presentation(), ActionManager.getInstance(), 0)
    mapDataContext.apply {
      put(CommonDataKeys.PROJECT, projectRule.project)
      put(PlatformDataKeys.FILE_EDITOR, stringResourceEditor)
    }

    whenever(stringResourceEditor.panel).thenReturn(panel)
    whenever(panel.table).thenReturn(table)
    whenever(table.model).thenReturn(model)

    doAnswer { rowFilter }.whenever(table).rowFilter
    doAnswer {
      rowFilter = it.getArgument(0)
      Unit
    }.whenever(table).setRowFilter(any())

    // Mock the WindowManager so the call to windowManager.getFrame will not return null.
    // A null frame causes ComboBoxAction.actionPerformed(...) to return early before it invokes
    // createActionPopup, and thus will do nothing and can't be tested.
    val windowManager: WindowManagerEx = mock()
    val frame: IdeFrameImpl = mock()
    whenever(frame.component).thenReturn(JButton())
    whenever(windowManager.getFrame(projectRule.project)).thenReturn(frame)
    ApplicationManager.getApplication().replaceService(WindowManager::class.java, windowManager, projectRule.testRootDisposable)
  }

  @Test
  fun update_noEditor() {
    mapDataContext.put(PlatformDataKeys.FILE_EDITOR, null)

    filterKeysAction.update(event)

    verifyNoInteractions(stringResourceEditor, panel, table)
  }

  @Test
  fun update_noRowFilter() {
    filterKeysAction.update(event)

    assertThat(event.presentation.icon).isNull()
    assertThat(event.presentation.text).isEqualTo("Show All Keys")
  }

  @Test
  fun update_rowFilterPresent() {
    val presentationText = "Some amazing text!"
    rowFilter = object : StringResourceTableRowFilter() {
      override fun include(entry: Entry<out StringResourceTableModel, out Int>?): Boolean = throw NotImplementedError("Not called")
      override fun getDescription(): String = presentationText
      override fun getIcon(): Icon = AllIcons.Idea_logo_welcome
    }

    filterKeysAction.update(event)

    assertThat(event.presentation.icon).isEqualTo(AllIcons.Idea_logo_welcome)
    assertThat(event.presentation.text).isEqualTo(presentationText)
  }

  @Test
  fun actionPerformed_showAllKeys() {
    filterKeysAction.actionPerformed(event)

    val popup = popupRule.fakePopupFactory.getPopup<AnAction>(0)
    assertThat(popup.items).hasSize(4)
    val selectedAction = popup.items[0]
    assertThat(selectedAction.templateText).isEqualTo("Show All Keys")

    selectedAction.actionPerformed(event)

    assertThat(rowFilter).isNull()
    verify(table).setRowFilter(null)  // Make sure it's not just still null
  }

  @Test
  fun actionPerformed_showTranslatableKeys() {
    filterKeysAction.actionPerformed(event)

    val popup = popupRule.fakePopupFactory.getPopup<AnAction>(0)
    assertThat(popup.items).hasSize(4)
    val selectedAction = popup.items[1]
    assertThat(selectedAction.templateText).isEqualTo("Show Translatable Keys")

    selectedAction.actionPerformed(event)

    assertThat(rowFilter).isInstanceOf(TranslatableRowFilter::class.java)
  }

  @Test
  fun actionPerformed_showKeysNeedingTranslation() {
    filterKeysAction.actionPerformed(event)

    val popup = popupRule.fakePopupFactory.getPopup<AnAction>(0)
    assertThat(popup.items).hasSize(4)
    val selectedAction = popup.items[2]
    assertThat(selectedAction.templateText).isEqualTo("Show Keys Needing Translation")

    selectedAction.actionPerformed(event)

    assertThat(rowFilter).isInstanceOf(NeedsTranslationsRowFilter::class.java)
  }

  @Test
  fun actionPerformed_filterByText_cancel() {
    enableHeadlessDialogs(projectRule.project)
    filterKeysAction.actionPerformed(event)

    val popup = popupRule.fakePopupFactory.getPopup<AnAction>(0)
    assertThat(popup.items).hasSize(4)
    val selectedAction = popup.items[3]
    assertThat(selectedAction.templateText).isEqualTo("Filter By Text")
    assertThat(selectedAction.templatePresentation.icon).isEqualTo(AllIcons.General.Filter)

    createModalDialogAndInteractWithIt({ selectedAction.actionPerformed(event) }) {
      it.text = "my great text"  // Won't matter, we're canceling.
      it.click("Cancel")
    }

    assertThat(rowFilter).isNull()
    verify(table, never()).setRowFilter(any())  // Make sure it's still null and wasn't overwritten.
  }

  @Test
  fun actionPerformed_filterByText_emptyString() {
    enableHeadlessDialogs(projectRule.project)
    filterKeysAction.actionPerformed(event)

    val popup = popupRule.fakePopupFactory.getPopup<AnAction>(0)
    assertThat(popup.items).hasSize(4)
    val selectedAction = popup.items[3]
    assertThat(selectedAction.templateText).isEqualTo("Filter By Text")
    assertThat(selectedAction.templatePresentation.icon).isEqualTo(AllIcons.General.Filter)

    enableHeadlessDialogs(projectRule.project)

    createModalDialogAndInteractWithIt({ selectedAction.actionPerformed(event) }) {
      it.text = ""  // Empty string means we shouldn't do anything.
      it.click("OK")
    }

    assertThat(rowFilter).isNull()
    verify(table, never()).setRowFilter(any())  // Make sure it's still null and wasn't overwritten.
  }

  @Test
  fun actionPerformed_filterByText_textInput() {
    enableHeadlessDialogs(projectRule.project)
    val filterText = "my great text"
    filterKeysAction.actionPerformed(event)

    val popup = popupRule.fakePopupFactory.getPopup<AnAction>(0)
    assertThat(popup.items).hasSize(4)
    val selectedAction = popup.items[3]
    assertThat(selectedAction.templateText).isEqualTo("Filter By Text")
    assertThat(selectedAction.templatePresentation.icon).isEqualTo(AllIcons.General.Filter)

    createModalDialogAndInteractWithIt({ selectedAction.actionPerformed(event) }) {
      it.text = filterText
      it.click("OK")
    }

    assertThat(rowFilter).isInstanceOf(TextRowFilter::class.java)
    assertThat(rowFilter!!.getDescription()).isEqualTo("""Show Keys with Values Containing "$filterText"""")
  }

  @Test
  fun actionPerformed_keysNeedingTranslationForLocale() {
    whenever(model.columnCount).thenReturn(StringResourceTableModel.FIXED_COLUMN_COUNT + 2)
    whenever(model.getLocale(StringResourceTableModel.FIXED_COLUMN_COUNT)).thenReturn(ARABIC_LOCALE)
    whenever(model.getLocale(StringResourceTableModel.FIXED_COLUMN_COUNT + 1)).thenReturn(US_SPANISH_LOCALE)

    filterKeysAction.actionPerformed(event)

    val popup = popupRule.fakePopupFactory.getPopup<AnAction>(0)
    assertThat(popup.items).hasSize(6)

    var selectedAction = popup.items[4]
    assertThat(selectedAction.templateText).isEqualTo("Show Keys Needing a Translation for Arabic (ar)")
    assertThat(selectedAction.templatePresentation.icon).isEqualTo(FlagManager.getFlagImage(ARABIC_LOCALE))

    selectedAction.actionPerformed(event)

    assertThat(rowFilter).isInstanceOf(NeedsTranslationForLocaleRowFilter::class.java)
    assertThat(rowFilter!!.getDescription()).isEqualTo("Show Keys Needing a Translation for Arabic (ar)")
    assertThat(rowFilter!!.getIcon()).isEqualTo(FlagManager.getFlagImage(ARABIC_LOCALE))

    selectedAction = popup.items[5]
    assertThat(selectedAction.templateText).isEqualTo("Show Keys Needing a Translation for Spanish (es) in United States (US)")
    assertThat(selectedAction.templatePresentation.icon).isEqualTo(FlagManager.getFlagImage(US_SPANISH_LOCALE))

    selectedAction.actionPerformed(event)

    assertThat(rowFilter).isInstanceOf(NeedsTranslationForLocaleRowFilter::class.java)
    assertThat(rowFilter!!.getDescription()).isEqualTo("Show Keys Needing a Translation for Spanish (es) in United States (US)")
    assertThat(rowFilter!!.getIcon()).isEqualTo(FlagManager.getFlagImage(US_SPANISH_LOCALE))
  }

  companion object {
    private val ARABIC_LOCALE = Locale.create("ar")
    private val US_SPANISH_LOCALE = Locale.create("es-rUS")

    private var DialogWrapper.text
      get() = textField.text
      set(value) {
        textField.text = value
      }

    private val DialogWrapper.textField: JTextField
      get() = rootPane.getDescendant {it.name == null}

    private fun DialogWrapper.click(text: String) {
      rootPane.getDescendant<JButton> { it.text == text }.doClick()
    }
  }
}
