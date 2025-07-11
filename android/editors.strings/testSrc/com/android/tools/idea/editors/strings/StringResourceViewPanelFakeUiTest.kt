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
package com.android.tools.idea.editors.strings

import com.android.ide.common.rendering.api.ResourceNamespace
import com.android.ide.common.resources.Locale
import com.android.ide.common.resources.ResourceItem
import com.android.resources.ResourceType
import com.android.testutils.TestUtils.resolveWorkspacePath
import com.android.testutils.waitForCondition
import com.android.tools.adtui.stdui.OUTLINE_PROPERTY
import com.android.tools.adtui.swing.FakeUi
import com.android.tools.adtui.swing.createModalDialogAndInteractWithIt
import com.android.tools.adtui.swing.enableHeadlessDialogs
import com.android.tools.adtui.swing.getDescendant
import com.android.tools.idea.actions.BrowserHelpAction
import com.android.tools.idea.editors.strings.action.AddKeyAction
import com.android.tools.idea.editors.strings.action.AddLocaleAction
import com.android.tools.idea.editors.strings.action.FilterKeysAction
import com.android.tools.idea.editors.strings.action.FilterLocalesAction
import com.android.tools.idea.editors.strings.action.ReloadStringResourcesAction
import com.android.tools.idea.editors.strings.action.RemoveKeysAction
import com.android.tools.idea.editors.strings.table.StringResourceTableModel.FIXED_COLUMN_COUNT
import com.android.tools.idea.editors.strings.table.StringResourceTableModel.KEY_COLUMN
import com.android.tools.idea.editors.strings.table.StringResourceTableModel.RESOURCE_FOLDER_COLUMN
import com.android.tools.idea.res.ModuleResourceRepository
import com.android.tools.idea.testing.AndroidProjectRule
import com.android.tools.idea.util.androidFacet
import com.android.tools.res.LocalResourceRepository
import com.google.common.truth.Truth.assertThat
import com.intellij.openapi.actionSystem.ActionToolbar
import com.intellij.openapi.actionSystem.Separator
import com.intellij.openapi.application.AppUIExecutor
import com.intellij.openapi.application.invokeAndWaitIfNeeded
import com.intellij.openapi.ui.DialogWrapper
import com.intellij.openapi.ui.TextFieldWithBrowseButton
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.testFramework.EdtRule
import com.intellij.testFramework.RunsInEdt
import com.intellij.util.concurrency.SameThreadExecutor
import org.jetbrains.android.facet.AndroidFacet
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.RuleChain
import org.junit.runner.RunWith
import org.junit.runners.JUnit4
import java.awt.event.FocusEvent
import java.awt.event.KeyEvent
import java.util.concurrent.CountDownLatch
import javax.swing.JTextField
import kotlin.time.Duration.Companion.seconds

@RunWith(JUnit4::class)
class StringResourceViewPanelFakeUiTest {
  private val projectRule = AndroidProjectRule.withAndroidModel()

  @get:Rule val ruleChain = RuleChain.outerRule(projectRule).around(EdtRule())!!
  private lateinit var stringResourceViewPanel: StringResourceViewPanel
  private lateinit var fakeUi: FakeUi
  private lateinit var resourceDirectory: VirtualFile
  private lateinit var localResourceRepository: LocalResourceRepository<*>
  private lateinit var facet: AndroidFacet

  @Before
  fun setUp() {
    facet = AndroidFacet.getInstance(projectRule.module)!!
    projectRule.fixture.testDataPath =
      resolveWorkspacePath("tools/adt/idea/android/testData").toString()
    resourceDirectory = projectRule.fixture.copyDirectoryToProject("stringsEditor/base/res", "res")
    localResourceRepository = ModuleResourceRepository.createForTest(facet, listOf(resourceDirectory))

    stringResourceViewPanel = StringResourceViewPanel(projectRule.module.androidFacet!!, projectRule.testRootDisposable)
    invokeAndWaitIfNeeded {
      fakeUi = FakeUi(stringResourceViewPanel.loadingPanel)
      fakeUi.root.validate()
    }
    AppUIExecutor.onWriteThread().run {
      ResourceLoadingTask(stringResourceViewPanel).queue()
    }
  }

  @Test
  fun toolbarConstructedProperly() {
    val toolbar: ActionToolbar = stringResourceViewPanel.loadingPanel.getDescendant { it.component.name == "toolbar" }
    assertThat(toolbar.actions).hasSize(8)
    assertThat(toolbar.actions[0]).isInstanceOf(AddKeyAction::class.java)
    assertThat(toolbar.actions[1]).isInstanceOf(RemoveKeysAction::class.java)
    assertThat(toolbar.actions[2]).isInstanceOf(AddLocaleAction::class.java)
    assertThat(toolbar.actions[3]).isInstanceOf(FilterKeysAction::class.java)
    assertThat(toolbar.actions[4]).isInstanceOf(FilterLocalesAction::class.java)
    assertThat(toolbar.actions[5]).isInstanceOf(ReloadStringResourcesAction::class.java)
    assertThat(toolbar.actions[6]).isInstanceOf(BrowserHelpAction::class.java)
    assertThat(toolbar.actions[7]).isInstanceOf(Separator::class.java)
  }

  @Test
  fun dataLoadCorrectly() {
    assertThat(stringResourceViewPanel.table.getColumnAt(KEY_COLUMN)).isEqualTo(DEFAULT_KEYS)
    val locales = (FIXED_COLUMN_COUNT until stringResourceViewPanel.table.columnCount)
      .map(stringResourceViewPanel.table::getColumnName)
    assertThat(locales).isEqualTo(DEFAULT_LOCALES)
    assertThat(stringResourceViewPanel.table.getColumnAt(RESOURCE_FOLDER_COLUMN)).isEqualTo(List(DEFAULT_KEYS.size) { "res" })
  }

  @Test
  @RunsInEdt
  fun removeKeyWithStringResourceWriter() {
    val row = 2
    enableHeadlessDialogs(projectRule.project)

    stringResourceViewPanel.table.selectCellAt(row, KEY_COLUMN)
    fakeUi.keyboard.setFocus(stringResourceViewPanel.table.frozenTable)
    createModalDialogAndInteractWithIt({ fakeUi.keyboard.press(KeyEvent.VK_DELETE)}) {
      it.close(DialogWrapper.OK_EXIT_CODE)
    }
    assertThat(stringResourceViewPanel.table.getColumnAt(KEY_COLUMN))
      .isEqualTo(DEFAULT_KEYS.slice(DEFAULT_KEYS.indices.minus(row)))
  }

  @Test
  @RunsInEdt
  fun removeValue() {
    val row = 2
    val column = 4
    val initialValues = stringResourceViewPanel.table.getColumnAt(column)
    // Make sure there's something to remove.
    assertThat(initialValues[row].toString()).isNotEmpty()
    val locale = stringResourceViewPanel.table.model.getLocale(column)!!
    assertThat(getResourceItem(DEFAULT_KEYS[row], locale)?.resourceValue?.value).isEqualTo(initialValues[row])

    stringResourceViewPanel.table.selectCellAt(row, column)
    fakeUi.keyboard.setFocus(stringResourceViewPanel.table.scrollableTable)
    fakeUi.keyboard.press(KeyEvent.VK_DELETE)

    // Should not have removed any keys
    assertThat(stringResourceViewPanel.table.getColumnAt(KEY_COLUMN)).isEqualTo(DEFAULT_KEYS)
    // The value should be gone from the table.
    val expected = initialValues.take(row) + "" + initialValues.drop(row + 1)
    assertThat(stringResourceViewPanel.table.getColumnAt(column)).isEqualTo(expected)
    // And the actual file should also be updated.
    localResourceRepository.waitForPendingUpdates()
    assertThat(getResourceItem(DEFAULT_KEYS[row], locale)).isNull()
  }

  // These should really be tests on the model, but ain't nobody got time for that.
  @Test
  @RunsInEdt
  fun overwriteKey() {
    val row = 2
    assertThat(stringResourceViewPanel.table.getColumnAt(KEY_COLUMN)).isEqualTo(DEFAULT_KEYS)
    val locales = (FIXED_COLUMN_COUNT until stringResourceViewPanel.table.columnCount)
      .mapNotNull(stringResourceViewPanel.table.model::getLocale)
    val oldResources = locales.mapNotNull { getResourceItem(DEFAULT_KEYS[row], it) }
    val newKey = "new_key"

    // Change a key value in the table:
    stringResourceViewPanel.table.model.setValueAt(newKey, row, KEY_COLUMN)

    // Editing a key runs a refactor which happens in invokeLater(), so wait for that to finish.
    waitForCondition(2.seconds) { stringResourceViewPanel.table.getValueAt(row, KEY_COLUMN) == "new_key" }

    assertThat(stringResourceViewPanel.table.getColumnAt(KEY_COLUMN)).isEqualTo(
      DEFAULT_KEYS.toMutableList().apply { this[row] = newKey})
    assertThat(locales.mapNotNull { getResourceItem(DEFAULT_KEYS[row], it)}).isEmpty()

    locales.mapNotNull { getResourceItem(newKey, it)}.zip(oldResources).forEach { (new, old) ->
      assertThat(new.name).isEqualTo(newKey)
      assertThat(new.type).isEqualTo(old.type)
      assertThat(new.resourceValue?.resourceType).isEqualTo(old.resourceValue?.resourceType)
      assertThat(new.resourceValue?.name).isEqualTo(newKey)
      assertThat(new.resourceValue?.namespace).isEqualTo(old.resourceValue?.namespace)
      assertThat(new.resourceValue?.value).isEqualTo(old.resourceValue?.value)
      assertThat(new.source).isEqualTo(old.source)
    }
  }

  @Test
  @RunsInEdt
  fun overwriteValue() {
    val row = 2
    val column = 4
    val initialValues = stringResourceViewPanel.table.getColumnAt(column)
    // Make sure there's something to remove.
    assertThat(initialValues[row].toString()).isNotEmpty()
    val locale = stringResourceViewPanel.table.model.getLocale(column)!!
    assertThat(getResourceItem(DEFAULT_KEYS[row], locale)?.resourceValue?.value).isEqualTo(initialValues[row])

    stringResourceViewPanel.table.model.setValueAt("new_value", row, column)

    // Should not have removed any keys
    assertThat(stringResourceViewPanel.table.getColumnAt(KEY_COLUMN)).isEqualTo(DEFAULT_KEYS)
    // The value should be updated in the table.
    val expected = initialValues.take(row) + "new_value" + initialValues.drop(row + 1)
    assertThat(stringResourceViewPanel.table.getColumnAt(column)).isEqualTo(expected)
    // And the actual file should also be updated.
    localResourceRepository.waitForPendingUpdates()
    assertThat(getResourceItem(DEFAULT_KEYS[row], locale)?.resourceValue?.value).isEqualTo("new_value")
  }

  @Test
  @RunsInEdt
  fun changeKeyInBottomPanel() {
    stringResourceViewPanel.table.selectCellAt(1, 3)
    val field: JTextField = stringResourceViewPanel.loadingPanel.getDescendant { it.name == "keyTextField" }
    fakeUi.keyboard.setFocus(field)
    field.focusListeners.forEach { it.focusGained(FocusEvent(field, FocusEvent.FOCUS_GAINED)) }

    // Invalid value:
    field.imitateEditing("b a d v a l u e")
    fakeUi.keyboard.pressAndRelease(KeyEvent.VK_ENTER)
    assertThat(field.getClientProperty(OUTLINE_PROPERTY)).isEqualTo("error")
    assertThat(field.toolTipText).isEqualTo("' ' is not a valid resource name character")

    // Valid change:
    field.imitateEditing("new_key")
    var changes = 0
    stringResourceViewPanel.table.frozenTable.model.addTableModelListener { changes++ }
    fakeUi.keyboard.pressAndRelease(KeyEvent.VK_ENTER)
    assertThat(field.getClientProperty(OUTLINE_PROPERTY)).isNull()
    assertThat(field.toolTipText).isNull()

    waitForCondition(2.seconds) { changes > 0 }
    assertThat(stringResourceViewPanel.table.data?.keys[1]?.name).isEqualTo("new_key")
  }

  @Test
  @RunsInEdt
  fun changeDefaultValueInBottomPanel() {
    stringResourceViewPanel.table.selectCellAt(1, 3)
    val component: TextFieldWithBrowseButton = stringResourceViewPanel.loadingPanel.getDescendant { it.name == "defaultValueTextField" }
    val field = component.textField
    fakeUi.keyboard.setFocus(field)
    field.focusListeners.forEach { it.focusGained(FocusEvent(field, FocusEvent.FOCUS_GAINED)) }

    // Invalid value:
    field.imitateEditing("<bad value")
    fakeUi.keyboard.pressAndRelease(KeyEvent.VK_ENTER)
    assertThat(field.getClientProperty(OUTLINE_PROPERTY)).isEqualTo("error")
    assertThat(field.toolTipText).isEqualTo("Invalid value")

    // Valid change:
    field.imitateEditing("New default value")
    var changes = 0
    stringResourceViewPanel.table.frozenTable.model.addTableModelListener { changes++ }
    fakeUi.keyboard.pressAndRelease(KeyEvent.VK_ENTER)
    assertThat(field.getClientProperty(OUTLINE_PROPERTY)).isNull()
    assertThat(field.toolTipText).isNull()

    waitForCondition(2.seconds) { changes > 0 }
    val data = stringResourceViewPanel.table.data!!
    val key = data.keys[1]
    assertThat(data.getStringResource(key).defaultValueAsString).isEqualTo("New default value")
  }

  @Test
  @RunsInEdt
  fun changeTranslationInBottomPanel() {
    stringResourceViewPanel.table.selectCellAt(1, 4)
    val component: TextFieldWithBrowseButton = stringResourceViewPanel.loadingPanel.getDescendant { it.name == "translationTextField" }
    val field = component.textField
    fakeUi.keyboard.setFocus(field)
    field.focusListeners.forEach { it.focusGained(FocusEvent(field, FocusEvent.FOCUS_GAINED)) }

    // Invalid value:
    field.imitateEditing("<bad value")
    fakeUi.keyboard.pressAndRelease(KeyEvent.VK_ENTER)
    assertThat(field.getClientProperty(OUTLINE_PROPERTY)).isEqualTo("error")
    assertThat(field.toolTipText).isEqualTo("Invalid value")

    // Valid change:
    field.imitateEditing("New default value")
    field.imitateEditing("New translated value")
    var changes = 0
    stringResourceViewPanel.table.scrollableTable.model.addTableModelListener { changes++ }
    fakeUi.keyboard.pressAndRelease(KeyEvent.VK_ENTER)
    assertThat(field.getClientProperty(OUTLINE_PROPERTY)).isNull()
    assertThat(field.toolTipText).isNull()

    waitForCondition(2.seconds) { changes > 0 }
    val data = stringResourceViewPanel.table.data!!
    val key = data.keys[1]
    val locale = data.localeList[0]
    assertThat(data.getStringResource(key).getTranslationAsString(locale)).isEqualTo("New translated value")
  }

  private fun <T> LocalResourceRepository<T>.waitForPendingUpdates() {
    val latch = CountDownLatch(1)
    invokeAfterPendingUpdatesFinish(SameThreadExecutor.INSTANCE) {
      latch.countDown()
    }
    latch.await()
  }

  private fun JTextField.imitateEditing(newText: String) {
    document.remove(0, document.length)
    document.insertString(0, newText, null)
  }

  private fun getResourceItem(name: String, locale: Locale): ResourceItem? =
    localResourceRepository
      .getResources(ResourceNamespace.RES_AUTO, ResourceType.STRING, name)
      .find { locale.qualifier == it.configuration.localeQualifier }

  companion object {
    val DEFAULT_KEYS = listOf("key1", "key2", "key3", "key5", "key6", "key7", "key8", "key4", "key9", "key10", "donottranslate_key1", "donottranslate_key2")
    val DEFAULT_LOCALES =
      listOf("English (en)", "English (en) in India (IN)", "English (en) in United Kingdom (GB)", "French (fr)", "Hindi (hi)")
  }
}
