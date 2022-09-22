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
package com.android.tools.idea.logcat.filters

import com.android.testutils.MockitoKt.any
import com.android.testutils.MockitoKt.mock
import com.android.tools.adtui.TreeWalker
import com.android.tools.adtui.swing.FakeUi
import com.android.tools.analytics.UsageTrackerRule
import com.android.tools.idea.FakeAndroidProjectDetector
import com.android.tools.idea.concurrency.waitForCondition
import com.android.tools.idea.logcat.FakeLogcatPresenter
import com.android.tools.idea.logcat.FakePackageNamesProvider
import com.android.tools.idea.logcat.LogcatPresenter
import com.android.tools.idea.logcat.PACKAGE_NAMES_PROVIDER_KEY
import com.android.tools.idea.logcat.TAGS_PROVIDER_KEY
import com.android.tools.idea.logcat.filters.FilterTextField.FilterHistoryItem.Item
import com.android.tools.idea.logcat.filters.FilterTextField.HistoryList
import com.android.tools.idea.logcat.util.AndroidProjectDetector
import com.android.tools.idea.logcat.util.logcatEvents
import com.google.common.truth.Truth.assertThat
import com.google.wireless.android.sdk.stats.LogcatUsageEvent
import com.google.wireless.android.sdk.stats.LogcatUsageEvent.LogcatFilterEvent
import com.google.wireless.android.sdk.stats.LogcatUsageEvent.Type.FILTER_ADDED_TO_HISTORY
import com.intellij.icons.AllIcons
import com.intellij.openapi.editor.EditorFactory
import com.intellij.openapi.editor.event.DocumentListener
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.asSequence
import com.intellij.openapi.util.Disposer
import com.intellij.testFramework.EdtRule
import com.intellij.testFramework.ProjectRule
import com.intellij.testFramework.RuleChain
import com.intellij.testFramework.RunsInEdt
import com.intellij.testFramework.runInEdtAndGet
import com.intellij.testFramework.runInEdtAndWait
import com.intellij.ui.EditorTextField
import com.intellij.ui.SimpleColoredComponent
import icons.StudioIcons
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runBlockingTest
import org.junit.After
import org.junit.Rule
import org.junit.Test
import org.mockito.Mockito.verify
import java.awt.Dimension
import java.awt.event.FocusEvent
import java.awt.event.KeyEvent
import java.awt.event.KeyEvent.VK_ENTER
import java.util.concurrent.TimeUnit.SECONDS
import javax.swing.GroupLayout
import javax.swing.Icon
import javax.swing.JLabel
import javax.swing.JPanel
import javax.swing.JSeparator

/**
 * Tests for [FilterTextField]
 */
class FilterTextFieldTest {
  private val projectRule = ProjectRule()
  private val usageTrackerRule = UsageTrackerRule()

  @get:Rule
  val rule = RuleChain(projectRule, EdtRule(), usageTrackerRule)

  private val project get() = projectRule.project
  private val filterHistory by lazy { AndroidLogcatFilterHistory.getInstance() }
  private val fakeLogcatPresenter by lazy { FakeLogcatPresenter().apply { Disposer.register(project, this) } }
  private val logcatFilterParser by lazy { LogcatFilterParser(project, FakePackageNamesProvider()) }

  @After
  fun tearDown() {
    filterHistory.nonFavorites.clear()
    filterHistory.favorites.clear()
  }

  @Test
  @RunsInEdt
  fun constructor_setsText() {
    val filterTextField = filterTextField(initialText = "text")

    assertThat(filterTextField.text).isEqualTo("text")
  }

  @Test
  @RunsInEdt
  fun constructor_setsText_asFavorite() {
    filterHistory.add("text", isFavorite = true)

    val filterTextField = filterTextField(initialText = "text")

    val icons = TreeWalker(filterTextField).descendants().filterIsInstance<JLabel>()
    assertThat(icons.find { it.icon == StudioIcons.Logcat.Input.FAVORITE_OUTLINE }).isNull()
    assertThat(icons.find { it.icon == StudioIcons.Logcat.Input.FAVORITE_FILLED }).isNotNull()
  }

  @Test
  @RunsInEdt
  fun constructor_setsText_asNonFavorite() {
    val filterTextField = filterTextField(initialText = "text")

    val icons = TreeWalker(filterTextField).descendants().filterIsInstance<JLabel>()
    assertThat(icons.find { it.icon == StudioIcons.Logcat.Input.FAVORITE_OUTLINE }).isNotNull()
    assertThat(icons.find { it.icon == StudioIcons.Logcat.Input.FAVORITE_FILLED }).isNull()
  }

  @Test
  @RunsInEdt
  fun createEditor_putsUserData() {
    val editorFactory = EditorFactory.getInstance()
    val androidProjectDetector = FakeAndroidProjectDetector(true)
    val filterTextField = filterTextField(project, fakeLogcatPresenter, androidProjectDetector = androidProjectDetector)

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
    val textField = TreeWalker(filterTextField).descendants().filterIsInstance<EditorTextField>()[0]
    filterTextField.text = "bar"

    val fakeUi = FakeUi(filterTextField)
    fakeUi.keyboard.setFocus(textField.editor!!.contentComponent)
    fakeUi.keyboard.pressAndRelease(VK_ENTER)

    assertThat(getHistoryNonFavorites()).containsExactly("bar").inOrder()
  }

  @Test
  @RunsInEdt
  fun pressEnter_addsToHistory_favorite() {
    val filterTextField = filterTextField(initialText = "bar")
    val textField = TreeWalker(filterTextField).descendants().filterIsInstance<EditorTextField>()[0]
    val fakeUi = FakeUi(filterTextField, createFakeWindow = true)
    val favoriteButton = fakeUi.getComponent<JLabel> { it.icon == StudioIcons.Logcat.Input.FAVORITE_OUTLINE }

    fakeUi.clickOn(favoriteButton)
    val keyEvent = KeyEvent(textField, 0, 0L, 0, VK_ENTER, '\n')
    textField.keyListeners.forEach { it.keyPressed(keyEvent) }

    assertThat(getHistoryFavorites()).containsExactly("bar").inOrder()
  }

  @Test
  @RunsInEdt
  fun loosesFocus_addsToHistory() {
    val filterTextField = filterTextField()
    val editorTextField = TreeWalker(filterTextField).descendants().filterIsInstance<EditorTextField>().first()

    filterTextField.text = "foo"
    editorTextField.focusLost(FocusEvent(editorTextField, 0))

    assertThat(getHistoryNonFavorites()).containsExactly("foo")
  }

  @Test
  @RunsInEdt
  fun loosesFocus_addsToHistory_favorite() {
    val filterTextField = filterTextField(initialText = "foo")
    val editorTextField = TreeWalker(filterTextField).descendants().filterIsInstance<EditorTextField>().first()
    val fakeUi = FakeUi(filterTextField, createFakeWindow = true)
    val favoriteButton = fakeUi.getComponent<JLabel> { it.icon == StudioIcons.Logcat.Input.FAVORITE_OUTLINE }

    fakeUi.clickOn(favoriteButton)
    editorTextField.focusLost(FocusEvent(editorTextField, 0))

    assertThat(getHistoryFavorites()).containsExactly("foo")
  }

  @Test
  @RunsInEdt
  fun addToHistory_logsUsage() {
    val filterTextField = filterTextField()
    val editorTextField = TreeWalker(filterTextField).descendants().filterIsInstance<EditorTextField>().first()

    filterTextField.text = "foo"
    editorTextField.focusLost(FocusEvent(editorTextField, 0))

    assertThat(usageTrackerRule.logcatEvents()).containsExactly(
      LogcatUsageEvent.newBuilder()
        .setType(FILTER_ADDED_TO_HISTORY)
        .setLogcatFilter(
          LogcatFilterEvent.newBuilder()
            .setImplicitLineTerms(1)
            .setIsFavorite(false))
        .build())
  }

  @Test
  @RunsInEdt
  fun addToHistory_favorite_logsUsage() {
    val filterTextField = filterTextField(initialText = "foo")
    val fakeUi = FakeUi(filterTextField, createFakeWindow = true)
    val favoriteButton = fakeUi.getComponent<JLabel> { it.icon == StudioIcons.Logcat.Input.FAVORITE_OUTLINE }

    fakeUi.clickOn(favoriteButton)

    assertThat(usageTrackerRule.logcatEvents()).containsExactly(
      LogcatUsageEvent.newBuilder()
        .setType(FILTER_ADDED_TO_HISTORY)
        .setLogcatFilter(
          LogcatFilterEvent.newBuilder()
            .setImplicitLineTerms(1)
            .setIsFavorite(true))
        .build())
  }

  @Suppress("OPT_IN_USAGE") // runBlockingTest is experimental
  @Test
  @RunsInEdt
  fun historyList_render() = runBlockingTest {
    filterHistory.add("foo", isFavorite = true)
    filterHistory.add("bar", isFavorite = false)
    fakeLogcatPresenter.filterMatchesCount["foo"] = 1
    fakeLogcatPresenter.filterMatchesCount["bar"] = 2
    val historyList = filterTextField().HistoryList(project, coroutineContext)
    historyList.waitForCounts(fakeLogcatPresenter)

    assertThat(historyList.renderToStrings()).containsExactly(
      "*: foo ( 1 )",
      "----------------------------------",
      " : bar ( 2 )",
    ).inOrder() // Order is reverse of the order added
  }

  @Suppress("OPT_IN_USAGE") // runBlockingTest is experimental
  @Test
  @RunsInEdt
  fun historyList_renderOnlyFavorites() = runBlockingTest {
    filterHistory.add("foo", isFavorite = true)
    filterHistory.add("bar", isFavorite = true)
    fakeLogcatPresenter.filterMatchesCount["foo"] = 1
    fakeLogcatPresenter.filterMatchesCount["bar"] = 2
    val historyList = filterTextField().HistoryList(project, coroutineContext)
    historyList.waitForCounts(fakeLogcatPresenter)

    assertThat(historyList.renderToStrings()).containsExactly(
      "*: bar ( 2 )",
      "*: foo ( 1 )",
    ).inOrder() // Order is reverse of the order added
  }

  @Suppress("OPT_IN_USAGE") // runBlockingTest is experimental
  @Test
  @RunsInEdt
  fun historyList_renderNoFavorites() = runBlockingTest {
    filterHistory.add("foo", isFavorite = false)
    filterHistory.add("bar", isFavorite = false)
    fakeLogcatPresenter.filterMatchesCount["foo"] = 1
    fakeLogcatPresenter.filterMatchesCount["bar"] = 2
    val historyList = filterTextField().HistoryList(project, coroutineContext)
    historyList.waitForCounts(fakeLogcatPresenter)

    assertThat(historyList.renderToStrings()).containsExactly(
      " : bar ( 2 )",
      " : foo ( 1 )",
    ).inOrder() // Order is reverse of the order added
  }

  @Suppress("OPT_IN_USAGE") // runBlockingTest is experimental
  @Test
  @RunsInEdt
  fun historyList_renderNamedFilter() = runBlockingTest {
    filterHistory.add("name:Foo tag:Foo", isFavorite = false)
    fakeLogcatPresenter.filterMatchesCount["name:Foo tag:Foo"] = 1
    val historyList = filterTextField().HistoryList(project, coroutineContext)
    historyList.waitForCounts(fakeLogcatPresenter)

    assertThat(historyList.renderToStrings()).containsExactly(
      " : Foo ( 1 )",
    ).inOrder()
  }

  @Suppress("OPT_IN_USAGE") // runBlockingTest is experimental
  @Test
  @RunsInEdt
  fun historyList_renderNamedFilterWithSameName() = runBlockingTest {
    filterHistory.add("name:Foo tag:Foo", isFavorite = false)
    filterHistory.add("name:Foo tag:Foobar", isFavorite = false)
    fakeLogcatPresenter.filterMatchesCount["name:Foo tag:Foo"] = 1
    fakeLogcatPresenter.filterMatchesCount["name:Foo tag:Foobar"] = 2
    val historyList = filterTextField().HistoryList(project, coroutineContext)
    historyList.waitForCounts(fakeLogcatPresenter)

    assertThat(historyList.renderToStrings()).containsExactly(
      " : Foo: tag:Foobar ( 2 )",
      " : Foo: tag:Foo ( 1 )",
    ).inOrder() // Order is reverse of the order added
  }

  @Test
  @RunsInEdt
  fun clickClear() {
    val filterTextField = filterTextField(initialText = "foo")
    val fakeUi = FakeUi(filterTextField, createFakeWindow = true)
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

  @RunsInEdt
  @Test
  fun emptyText_buttonPanelInvisible() {
    val filterTextField = filterTextField(initialText = "")

    val favoriteButton = filterTextField.getButtonWithIcon(StudioIcons.Logcat.Input.FAVORITE_OUTLINE)
    val clearButton = filterTextField.getButtonWithIcon(AllIcons.Actions.Close)
    val separator = TreeWalker(filterTextField).descendants().filterIsInstance<JSeparator>().first()

    assertThat(favoriteButton.isShowing).isFalse()
    assertThat(clearButton.isShowing).isFalse()
    assertThat(separator.isShowing).isFalse()
  }

  @RunsInEdt
  @Test
  fun nonEmptyText_buttonPanelVisible() {
    val filterTextField = filterTextField(initialText = "foo")

    val favoriteButton = filterTextField.getButtonWithIcon(StudioIcons.Logcat.Input.FAVORITE_OUTLINE)
    val clearButton = filterTextField.getButtonWithIcon(AllIcons.Actions.Close)
    val separator = TreeWalker(filterTextField).descendants().filterIsInstance<JSeparator>().first()

    assertThat(favoriteButton.isShowing).isTrue()
    assertThat(clearButton.isShowing).isTrue()
    assertThat(separator.isShowing).isTrue()
  }

  @RunsInEdt
  @Test
  fun textBecomesEmpty_buttonPanelInvisible() {
    val filterTextField = filterTextField(initialText = "foo")
    val favoriteButton = filterTextField.getButtonWithIcon(StudioIcons.Logcat.Input.FAVORITE_OUTLINE)
    val clearButton = filterTextField.getButtonWithIcon(AllIcons.Actions.Close)
    val separator = TreeWalker(filterTextField).descendants().filterIsInstance<JSeparator>().first()

    filterTextField.text = ""

    assertThat(favoriteButton.isShowing).isFalse()
    assertThat(clearButton.isShowing).isFalse()
    assertThat(separator.isShowing).isFalse()
  }

  @RunsInEdt
  @Test
  fun textBecomesNotEmpty_buttonPanelVisible() {
    val filterTextField = filterTextField(initialText = "")
    val favoriteButton = filterTextField.getButtonWithIcon(StudioIcons.Logcat.Input.FAVORITE_OUTLINE)
    val clearButton = filterTextField.getButtonWithIcon(AllIcons.Actions.Close)
    val separator = TreeWalker(filterTextField).descendants().filterIsInstance<JSeparator>().first()

    filterTextField.text = "foo"

    assertThat(favoriteButton.isShowing).isTrue()
    assertThat(clearButton.isShowing).isTrue()
    assertThat(separator.isShowing).isTrue()
  }

  @RunsInEdt
  @Test
  fun textChanges_setAsMostRecent() {
    val filterTextField = filterTextField(initialText = "foo")

    filterTextField.text = "bar"

    assertThat(AndroidLogcatFilterHistory.getInstance().mostRecentlyUsed).isEqualTo("bar")
  }

  @Test
  @RunsInEdt
  fun updateText_updatesFavorite() {
    val filterTextField = filterTextField(initialText = "bar")
    val textField = TreeWalker(filterTextField).descendants().filterIsInstance<EditorTextField>()[0]
    val fakeUi = FakeUi(filterTextField, createFakeWindow = true)
    val favoriteButton = fakeUi.getComponent<JLabel> { it.icon == StudioIcons.Logcat.Input.FAVORITE_OUTLINE }
    fakeUi.clickOn(favoriteButton)

    textField.text = "foo"
    assertThat(favoriteButton.icon).isEqualTo(StudioIcons.Logcat.Input.FAVORITE_OUTLINE)
    textField.text = "bar"
    assertThat(favoriteButton.icon).isEqualTo(StudioIcons.Logcat.Input.FAVORITE_FILLED)
  }

  private fun filterTextField(
    project: Project = this.project,
    logcatPresenter: LogcatPresenter = fakeLogcatPresenter,
    filterParser: LogcatFilterParser = logcatFilterParser,
    initialText: String = "",
    androidProjectDetector: AndroidProjectDetector = FakeAndroidProjectDetector(true),
  ) =
    FilterTextField(project, logcatPresenter, filterParser, initialText, androidProjectDetector).apply {
      addNotify()  // Creates editor
      size = Dimension(100, 100) // Allows FakeUi mouse clicks
    }

  private fun getHistoryNonFavorites(): List<String> = filterHistory.nonFavorites
  private fun getHistoryFavorites(): List<String> = filterHistory.favorites
}

private fun FilterTextField.getButtonWithIcon(icon: Icon) =
  TreeWalker(this).descendants().filterIsInstance<JLabel>().first { it.icon == icon }

private fun HistoryList.renderToStrings(): List<String> {
  return model.asSequence().toList().map {
    val panel = cellRenderer.getListCellRendererComponent(this, it, 0, false, false) as? JPanel
                ?: throw IllegalStateException("Unexpected component: ${it::class}")
    panel.renderToString()
  }
}

private fun JPanel.renderToString(): String {
  return when {
    components[0] is JSeparator -> "----------------------------------"
    layout is GroupLayout -> {
      val favorite = if ((components[0] as JLabel).icon == StudioIcons.Logcat.Input.FAVORITE_FILLED) "*" else " "
      val text = (components[1] as SimpleColoredComponent).toString()
      val count = (components[2] as JLabel).text
      "$favorite: $text ($count)"
    }
    else -> throw IllegalStateException("Unexpected component")
  }
}

private fun HistoryList.waitForCounts(fakeLogcatPresenter: FakeLogcatPresenter) {
  waitForCondition(2, SECONDS) {
    val counts = model.asSequence().filterIsInstance<Item>().associateBy({ it.filter }, { it.count })
    counts == fakeLogcatPresenter.filterMatchesCount
  }
}