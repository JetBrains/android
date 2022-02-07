/*
 * Copyright (C) 2021 The Android Open Source Project
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
package com.android.tools.idea.logcat

import com.android.testutils.MockitoKt.any
import com.android.testutils.MockitoKt.mock
import com.android.tools.adtui.TreeWalker
import com.android.tools.adtui.swing.FakeUi
import com.android.tools.adtui.swing.popup.FakeJBPopup
import com.android.tools.adtui.swing.popup.JBPopupRule
import com.android.tools.analytics.UsageTrackerRule
import com.android.tools.idea.FakeAndroidProjectDetector
import com.android.tools.idea.logcat.FilterTextField.Companion.HISTORY_PROPERTY_NAME
import com.android.tools.idea.logcat.filters.LogcatFilterParser
import com.android.tools.idea.logcat.util.AndroidProjectDetector
import com.android.tools.idea.logcat.util.logcatEvents
import com.google.common.truth.Truth.assertThat
import com.google.wireless.android.sdk.stats.LogcatUsageEvent
import com.google.wireless.android.sdk.stats.LogcatUsageEvent.LogcatFilterEvent
import com.google.wireless.android.sdk.stats.LogcatUsageEvent.Type.FILTER_ADDED_TO_HISTORY
import com.intellij.icons.AllIcons
import com.intellij.ide.util.PropertiesComponent
import com.intellij.openapi.editor.EditorFactory
import com.intellij.openapi.editor.event.DocumentListener
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import com.intellij.testFramework.EdtRule
import com.intellij.testFramework.ProjectRule
import com.intellij.testFramework.RuleChain
import com.intellij.testFramework.RunsInEdt
import com.intellij.testFramework.runInEdtAndGet
import com.intellij.testFramework.runInEdtAndWait
import com.intellij.ui.EditorTextField
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Rule
import org.junit.Test
import org.mockito.Mockito.verify
import java.awt.Dimension
import java.awt.event.FocusEvent
import java.awt.event.KeyEvent
import java.awt.event.KeyEvent.VK_ENTER
import javax.swing.JLabel

/**
 * Tests for [FilterTextField]
 */
class FilterTextFieldTest {
  private val projectRule = ProjectRule()
  private val popupRule = JBPopupRule()
  private val usageTrackerRule = UsageTrackerRule()

  @get:Rule
  val rule = RuleChain(projectRule, EdtRule(), usageTrackerRule, popupRule)

  private val properties by lazy { PropertiesComponent.getInstance() }
  private val fakeLogcatPresenter by lazy { FakeLogcatPresenter().apply { Disposer.register(projectRule.project, this) } }
  private val logcatFilterParser by lazy { LogcatFilterParser(projectRule.project, FakePackageNamesProvider()) }

  @After
  fun tearDown() {
    properties.setValues(HISTORY_PROPERTY_NAME, null)
  }

  @Test
  @RunsInEdt
  fun constructor_setsText() {
    val filterTextField = filterTextField(initialText = "text")

    assertThat(filterTextField.text).isEqualTo("text")
  }

  @Test
  @RunsInEdt
  fun historyPopup_withText() {
    properties.setValues(HISTORY_PROPERTY_NAME, arrayOf("foo", "bar"))
    val filterTextField = filterTextField(initialText = "text")

    filterTextField.size = Dimension(100, 100)
    val fakeUi = FakeUi(filterTextField)
    val historyButton = fakeUi.getComponent<JLabel> { it.icon == AllIcons.Actions.SearchWithHistory }
    fakeUi.clickOn(historyButton)

    val popup = popupRule.fakePopupFactory.getPopup<String>(0)
    assertThat(popup.isMovable).isFalse()
    assertThat(popup.isRequestFocus).isTrue()
    assertThat(popup.showStyle).isEqualTo(FakeJBPopup.ShowStyle.SHOW_UNDERNEATH_OF)
    assertThat(popup.showArgs).containsExactly(filterTextField)
    assertThat(popup.items).containsExactly("text", "foo", "bar").inOrder()
  }

  @Test
  @RunsInEdt
  fun historyPopup_withoutText() {
    properties.setValues(HISTORY_PROPERTY_NAME, arrayOf("foo", "bar"))
    val filterTextField = filterTextField(projectRule.project, fakeLogcatPresenter, initialText = "")

    filterTextField.size = Dimension(100, 100)
    val fakeUi = FakeUi(filterTextField)
    val historyButton = fakeUi.getComponent<JLabel> { it.icon == AllIcons.Actions.SearchWithHistory }
    fakeUi.clickOn(historyButton)

    val popup = popupRule.fakePopupFactory.getPopup<String>(0)
    assertThat(popup.isMovable).isFalse()
    assertThat(popup.isRequestFocus).isTrue()
    assertThat(popup.showStyle).isEqualTo(FakeJBPopup.ShowStyle.SHOW_UNDERNEATH_OF)
    assertThat(popup.showArgs).containsExactly(filterTextField)
    assertThat(popup.items).containsExactly("foo", "bar").inOrder()
  }

  @Test
  @RunsInEdt
  fun createEditor_putsUserData() {
    val editorFactory = EditorFactory.getInstance()
    val androidProjectDetector = FakeAndroidProjectDetector(true)
    val filterTextField = filterTextField(projectRule.project, fakeLogcatPresenter, androidProjectDetector = androidProjectDetector)
    filterTextField.addNotify() // Creates editor

    val editor = filterTextField.getEditorEx()

    assertThat(editor.getUserData(TAGS_PROVIDER_KEY)).isEqualTo(fakeLogcatPresenter)
    assertThat(editor.getUserData(PACKAGE_NAMES_PROVIDER_KEY)).isEqualTo(fakeLogcatPresenter)
    assertThat(editor.getUserData(PACKAGE_NAMES_PROVIDER_KEY)).isEqualTo(fakeLogcatPresenter)
    assertThat(editor.getUserData(AndroidProjectDetector.KEY)).isEqualTo(androidProjectDetector)
    editorFactory.releaseEditor(editor)
  }

  @Test
  @RunsInEdt
  fun pressEnter_addsToHistory() {
    val filterTextField = filterTextField()
    filterTextField.addNotify() // Creates editor
    val textField = TreeWalker(filterTextField).descendants().filterIsInstance<EditorTextField>()[0]
    filterTextField.text = "bar"

    val keyEvent = KeyEvent(textField, 0, 0L, 0, VK_ENTER, '\n')
    textField.keyListeners.forEach { it.keyPressed(keyEvent) }

    assertThat(getHistory()).containsExactly("bar").inOrder()
  }

  @Test
  @RunsInEdt
  fun loosesFocus_addsToHistory() {
    val filterTextField = filterTextField()
    filterTextField.addNotify() // Creates editor
    val editorTextField = TreeWalker(filterTextField).descendants().filterIsInstance<EditorTextField>().first()

    filterTextField.text = "foo"
    editorTextField.focusLost(FocusEvent(editorTextField, 0))

    assertThat(getHistory()).containsExactly("foo")
  }

  @Test
  @RunsInEdt
  fun addToHistory_logsUsage() {
    val filterTextField = filterTextField()
    filterTextField.addNotify() // Creates editor
    val editorTextField = TreeWalker(filterTextField).descendants().filterIsInstance<EditorTextField>().first()

    filterTextField.text = "foo"
    editorTextField.focusLost(FocusEvent(editorTextField, 0))

    assertThat(usageTrackerRule.logcatEvents()).containsExactly(
      LogcatUsageEvent.newBuilder()
        .setType(FILTER_ADDED_TO_HISTORY)
        .setLogcatFilter(
          LogcatFilterEvent.newBuilder()
            .setImplicitLineTerms(1))
        .build())
  }

  @Test
  @RunsInEdt
  fun history_size() {
    val filterTextField = filterTextField(maxHistorySize = 3)
    filterTextField.addNotify() // Creates editor
    val editorTextField = TreeWalker(filterTextField).descendants().filterIsInstance<EditorTextField>().first()

    for (text in listOf("foo1", "foo2", "foo3", "foo4")) {
      filterTextField.text = text
      editorTextField.focusLost(FocusEvent(editorTextField, 0))
    }

    assertThat(getHistory()).containsExactly(
      "foo4",
      "foo3",
      "foo2",
    ).inOrder()
  }

  @Test
  @RunsInEdt
  fun history_bubbles() {
    val filterTextField = filterTextField(maxHistorySize = 3)
    filterTextField.addNotify() // Creates editor
    val editorTextField = TreeWalker(filterTextField).descendants().filterIsInstance<EditorTextField>().first()

    for (text in listOf("foo1", "foo2", "foo3", "foo1")) {
      filterTextField.text = text
      editorTextField.focusLost(FocusEvent(editorTextField, 0))
    }

    assertThat(getHistory()).containsExactly(
      "foo1",
      "foo3",
      "foo2",
    ).inOrder()
  }

  @Test
  @RunsInEdt
  fun clickClear() {
    val filterTextField = filterTextField(initialText = "foo")
    filterTextField.size = Dimension(100, 100)
    val fakeUi = FakeUi(filterTextField)
    val clearButton = fakeUi.getComponent<JLabel> { it.icon == AllIcons.Actions.Close }

    fakeUi.clickOn(clearButton)

    assertThat(filterTextField.text).isEmpty()
  }

  @Test
  fun documentListenerIsCalled() = runBlocking {
    @Suppress("ConvertLambdaToReference") // More readable like this
    val filterTextField = runInEdtAndGet { filterTextField() }
    val documentListener = mock<DocumentListener>()

    filterTextField.addDocumentListener(documentListener)
    runInEdtAndWait { filterTextField.text = "foo" }

    filterTextField.notifyFilterChangedTask.await()
    verify(documentListener).documentChanged(any())
  }

  private fun filterTextField(
    project: Project = projectRule.project,
    logcatPresenter: LogcatPresenter = fakeLogcatPresenter,
    filterParser: LogcatFilterParser = logcatFilterParser,
    initialText: String = "",
    androidProjectDetector: AndroidProjectDetector = FakeAndroidProjectDetector(true),
    maxHistorySize: Int = 10,
  ) =
    FilterTextField(project, logcatPresenter, filterParser, initialText, androidProjectDetector, maxHistorySize)

  private fun getHistory(): List<String> = properties.getValues(HISTORY_PROPERTY_NAME)?.asList() ?: emptyList()
}
