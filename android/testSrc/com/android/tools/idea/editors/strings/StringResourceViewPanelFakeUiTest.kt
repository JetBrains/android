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
import com.android.tools.idea.res.LocalResourceRepository
import com.android.tools.idea.res.createTestModuleRepository
import com.android.tools.idea.testing.AndroidProjectRule
import com.android.tools.idea.util.androidFacet
import com.google.common.truth.Truth.assertThat
import com.intellij.openapi.actionSystem.ActionToolbar
import com.intellij.openapi.actionSystem.Separator
import com.intellij.openapi.application.AppUIExecutor
import com.intellij.openapi.application.invokeAndWaitIfNeeded
import com.intellij.openapi.ui.DialogWrapper
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
import java.awt.event.KeyEvent
import java.util.concurrent.CountDownLatch

@RunWith(JUnit4::class)
class StringResourceViewPanelFakeUiTest {
  private val projectRule = AndroidProjectRule.withAndroidModel()

  @get:Rule val ruleChain = RuleChain.outerRule(projectRule).around(EdtRule())!!
  private lateinit var stringResourceViewPanel: StringResourceViewPanel
  private lateinit var fakeUi: FakeUi
  private lateinit var resourceDirectory: VirtualFile
  private lateinit var localResourceRepository: LocalResourceRepository
  private lateinit var facet: AndroidFacet

  @Before
  fun setUp() {
    facet = AndroidFacet.getInstance(projectRule.module)!!
    projectRule.fixture.testDataPath =
      resolveWorkspacePath("tools/adt/idea/android/testData").toString()
    resourceDirectory = projectRule.fixture.copyDirectoryToProject("stringsEditor/base/res", "res")
    localResourceRepository = createTestModuleRepository(facet, listOf(resourceDirectory))

    stringResourceViewPanel = StringResourceViewPanel(projectRule.module.androidFacet, projectRule.testRootDisposable)
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
    assertThat(locales).isEqualTo(Companion.DEFAULT_LOCALES)
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

  private fun LocalResourceRepository.waitForPendingUpdates() {
    val latch = CountDownLatch(1)
    invokeAfterPendingUpdatesFinish(SameThreadExecutor.INSTANCE) {
      latch.countDown()
    }
    latch.await()
  }

  private fun getResourceItem(name: String, locale: Locale): ResourceItem? =
    localResourceRepository
      .getResources(ResourceNamespace.RES_AUTO, ResourceType.STRING, name)
      .find { locale.qualifier == it.configuration.localeQualifier }

  companion object {
    val DEFAULT_KEYS = listOf("key1", "key2", "key3", "key5", "key6", "key7", "key8", "key4", "key9", "key10")
    val DEFAULT_LOCALES =
      listOf("English (en)", "English (en) in India (IN)", "English (en) in United Kingdom (GB)", "French (fr)", "Hindi (hi)")
  }
}
