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
package com.android.tools.adtui

import com.android.testutils.MockitoKt.mock
import com.android.tools.adtui.swing.popup.FakeJBPopup.ShowStyle.SHOW_UNDERNEATH_OF
import com.android.tools.adtui.swing.popup.JBPopupRule
import com.android.tools.idea.concurrency.AndroidCoroutineScope
import com.android.tools.idea.concurrency.AndroidDispatchers
import com.android.tools.idea.concurrency.SupervisorJob
import com.google.common.truth.Truth.assertThat
import com.intellij.icons.AllIcons
import com.intellij.ide.util.PropertiesComponent
import com.intellij.openapi.Disposable
import com.intellij.testFramework.ProjectRule
import com.intellij.testFramework.RuleChain
import com.intellij.ui.LightColors
import com.intellij.ui.components.fields.ExtendableTextComponent.Extension
import com.intellij.ui.components.fields.ExtendableTextField
import com.intellij.util.ui.UIUtil
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.test.runBlockingTest
import org.junit.After
import org.junit.Rule
import org.junit.Test
import org.mockito.Mockito.atLeast
import org.mockito.Mockito.never
import org.mockito.Mockito.verify
import java.awt.event.KeyEvent
import javax.swing.Icon

private const val HISTORY_PROPERTY_NAME = "test"

/**
 * Tests for [RegexTextField]
 */
class RegexTextFieldTest {
  private val projectRule = ProjectRule()

  private val popupRule = JBPopupRule()

  @get:Rule
  val rule = RuleChain(projectRule, popupRule)


  private val supervisorJob by lazy { SupervisorJob(projectRule.project) }
  private val properties by lazy { PropertiesComponent.getInstance() }
  private val mockOnChangeListener = mock<RegexTextField.OnChangeListener>()

  @After
  fun tearDown() {
    properties.setValues(HISTORY_PROPERTY_NAME, null)
  }

  @Test
  fun registersExtensions() {
    val regexTextField = regexTextField()
    regexTextField.text = "foo" // So Clear icon is not null

    assertThat(regexTextField.textField.getClientProperty("JTextField.variant")).isEqualTo("extendable")
    assertThat(regexTextField.textField.extensions.map(Extension::toExtensionInfo)).containsExactly(
      ExtensionInfo(AllIcons.Actions.SearchWithHistory, AllIcons.Actions.SearchWithHistory, iconBeforeText = true),
      ExtensionInfo(AllIcons.Actions.Regex, AllIcons.Actions.Regex, iconBeforeText = false),
      ExtensionInfo(AllIcons.Actions.Close, AllIcons.Actions.CloseHovered, iconBeforeText = false),
    ).inOrder()
  }

  @Test
  fun clearExtension_clearsText() {
    val regexTextField = regexTextField()
    regexTextField.text = "foo"

    regexTextField.textField.getExtension(AllIcons.Actions.Close).actionOnClick.run()

    assertThat(regexTextField.text).isEmpty()
  }

  @Test
  fun clearExtension_noIconIfEmpty() {
    val regexTextField = regexTextField()
    regexTextField.text = "foo"
    val clearExtension = regexTextField.textField.getExtension(AllIcons.Actions.Close)

    regexTextField.text = ""

    assertThat(clearExtension.getIcon(/* hovered=*/ true)).isNull()
    assertThat(clearExtension.getIcon(/* hovered=*/ false)).isNull()
  }

  @Test
  fun regexExtension_togglesIsRegex() {
    val regexTextField = regexTextField()
    val regexExtension = regexTextField.textField.getExtension(AllIcons.Actions.Regex)
    regexTextField.isRegex = false

    regexExtension.actionOnClick.run()
    assertThat(regexTextField.isRegex).isTrue()

    regexExtension.actionOnClick.run()
    assertThat(regexTextField.isRegex).isFalse()
  }

  @Test
  fun regexExtension_togglesIcon() {
    val regexTextField = regexTextField()
    val regexExtension = regexTextField.textField.getExtension(AllIcons.Actions.Regex)
    regexTextField.isRegex = false

    regexExtension.actionOnClick.run()
    assertThat(regexExtension.getIcon(/* hovered=*/ false)).isEqualTo(AllIcons.Actions.RegexSelected)
    assertThat(regexExtension.getIcon(/* hovered=*/ true)).isEqualTo(AllIcons.Actions.RegexSelected)

    regexExtension.actionOnClick.run()
    assertThat(regexExtension.getIcon(/* hovered=*/ false)).isEqualTo(AllIcons.Actions.Regex)
    assertThat(regexExtension.getIcon(/* hovered=*/ true)).isEqualTo(AllIcons.Actions.Regex)
  }

  @Test
  fun regexExtension_noDelay_triggersDocumentChangeEvent() {
    val regexTextField = regexTextField(delayUpdateMs = 0)
    val regexExtension = regexTextField.textField.getExtension(AllIcons.Actions.Regex)
    regexTextField.addOnChangeListener(mockOnChangeListener)

    regexExtension.actionOnClick.run()

    verify(mockOnChangeListener).onChange(regexTextField)
  }

  @Suppress("EXPERIMENTAL_API_USAGE")
  @Test
  fun regexExtension_withDelay_triggersDocumentChangeEvent() = runBlockingTest {
    val regexTextField = regexTextField(delayUpdateMs = 1000, coroutineScope = CoroutineScope(coroutineContext + supervisorJob))
    val regexExtension = regexTextField.textField.getExtension(AllIcons.Actions.Regex)
    regexTextField.addOnChangeListener(mockOnChangeListener)

    regexExtension.actionOnClick.run()

    verify(mockOnChangeListener).onChange(regexTextField)
  }

  @Test
  fun textChanged_noDelay_triggersDocumentChangeEvent() {
    val regexTextField = regexTextField(delayUpdateMs = 0)
    regexTextField.addOnChangeListener(mockOnChangeListener)

    regexTextField.text = "foo"

    verify(mockOnChangeListener).onChange(regexTextField)
  }

  @Suppress("EXPERIMENTAL_API_USAGE")
  @Test
  fun textChanged_withDelay_triggersDocumentChangeEvent() = runBlockingTest {
    val regexTextField = regexTextField(delayUpdateMs = 1000, coroutineScope = CoroutineScope(coroutineContext + supervisorJob))
    regexTextField.addOnChangeListener(mockOnChangeListener)

    regexTextField.text = "foo"

    verify(mockOnChangeListener, never()).onChange(regexTextField)
    testScheduler.apply { advanceTimeBy(1000); runCurrent() }
    verify(mockOnChangeListener).onChange(regexTextField)
  }

  @Suppress("EXPERIMENTAL_API_USAGE")
  @Test
  fun invalidRegex() {
    val regexTextField = regexTextField()
    regexTextField.isRegex = true
    regexTextField.addOnChangeListener(mockOnChangeListener)

    regexTextField.text = "foo\\"

    verify(mockOnChangeListener, never()).onChange(regexTextField)
    assertThat(regexTextField.textField.background).isEqualTo(LightColors.RED)
  }

  @Suppress("EXPERIMENTAL_API_USAGE")
  @Test
  fun invalidRegex_changedToValid() {
    val regexTextField = regexTextField()
    regexTextField.addOnChangeListener(mockOnChangeListener)
    regexTextField.isRegex = true
    regexTextField.text = "foo\\"

    regexTextField.text = "foo\\w"

    // Setting the text explicitly actually generates 2 events. One with text == "" and another with the new text.
    verify(mockOnChangeListener, atLeast(1)).onChange(regexTextField)
    assertThat(regexTextField.textField.background).isEqualTo(UIUtil.getTextFieldBackground())
  }

  @Suppress("EXPERIMENTAL_API_USAGE")
  @Test
  fun invalidRegex_changedToNonRegex() {
    val regexTextField = regexTextField()
    val regexExtension = regexTextField.textField.getExtension(AllIcons.Actions.Regex)
    regexTextField.addOnChangeListener(mockOnChangeListener)
    regexTextField.isRegex = true
    regexTextField.text = "foo\\"

    // Changing regexTextField.isRegex directly doesn't generate an event.
    // TODO(review): Should it?
    regexExtension.actionOnClick.run()

    verify(mockOnChangeListener).onChange(regexTextField)
    assertThat(regexTextField.textField.background).isEqualTo(UIUtil.getTextFieldBackground())
  }


  @Test
  fun historyExtension_clickOpensPopup() {
    properties.setValues(HISTORY_PROPERTY_NAME, arrayOf("foo", "bar"))
    val regexTextField = regexTextField(historyPropertyName = HISTORY_PROPERTY_NAME)
    val historyExtension = regexTextField.textField.getExtension(AllIcons.Actions.SearchWithHistory)

    historyExtension.actionOnClick.run()

    val popup = popupRule.fakePopupFactory.getPopup<String>(0)
    assertThat(popup.isMovable).isFalse()
    assertThat(popup.isRequestFocus).isTrue()
    assertThat(popup.items).containsExactly("foo", "bar").inOrder()
    assertThat(popup.showStyle).isEqualTo(SHOW_UNDERNEATH_OF)
    assertThat(popup.showArgs).containsExactly(regexTextField)
  }

  @Test
  fun historyExtension_selectPopupItem_setsText() {
    properties.setValues(HISTORY_PROPERTY_NAME, arrayOf("foo", "bar"))
    val regexTextField = regexTextField(historyPropertyName = HISTORY_PROPERTY_NAME)
    val historyExtension = regexTextField.textField.getExtension(AllIcons.Actions.SearchWithHistory)
    historyExtension.actionOnClick.run()
    val popup = popupRule.fakePopupFactory.getPopup<String>(0)

    popup.selectItem("bar")

    assertThat(regexTextField.text).isEqualTo("bar")
  }

  @Test
  fun historyExtension_clickAddsToHistory() {
    properties.setValues(HISTORY_PROPERTY_NAME, arrayOf("foo"))
    val regexTextField = regexTextField(historyPropertyName = HISTORY_PROPERTY_NAME)
    val historyExtension = regexTextField.textField.getExtension(AllIcons.Actions.SearchWithHistory)

    regexTextField.text = "bar"
    historyExtension.actionOnClick.run()

    assertThat(properties.getValues(HISTORY_PROPERTY_NAME)).asList().containsExactly("bar", "foo").inOrder()
  }

  @Test
  fun historyExtension_duplicatesNotAdded() {
    properties.setValues(HISTORY_PROPERTY_NAME, arrayOf("foo"))
    val regexTextField = regexTextField(propertiesComponent = properties, historyPropertyName = HISTORY_PROPERTY_NAME)
    val historyExtension = regexTextField.textField.getExtension(AllIcons.Actions.SearchWithHistory)

    regexTextField.text = "foo"
    historyExtension.actionOnClick.run()

    assertThat(properties.getValues(HISTORY_PROPERTY_NAME)).asList().containsExactly("foo")
  }

  @Test
  fun historyExtension_emptyStringNotAdded() {
    properties.setValues(HISTORY_PROPERTY_NAME, arrayOf("foo", "bar"))
    val regexTextField = regexTextField(historyPropertyName = HISTORY_PROPERTY_NAME)
    val historyExtension = regexTextField.textField.getExtension(AllIcons.Actions.SearchWithHistory)

    regexTextField.text = ""
    historyExtension.actionOnClick.run()

    assertThat(properties.getValues(HISTORY_PROPERTY_NAME)).asList().containsExactly("foo", "bar").inOrder()
  }

  @Test
  fun historyExtension_existingEntryFloats() {
    properties.setValues(HISTORY_PROPERTY_NAME, arrayOf("foo", "bar"))
    val regexTextField = regexTextField(historyPropertyName = HISTORY_PROPERTY_NAME)
    val historyExtension = regexTextField.textField.getExtension(AllIcons.Actions.SearchWithHistory)

    regexTextField.text = "bar"
    historyExtension.actionOnClick.run()

    assertThat(properties.getValues(HISTORY_PROPERTY_NAME)).asList().containsExactly("bar", "foo").inOrder()
  }

  @Test
  fun historyExtension_historySize() {
    properties.setValues(HISTORY_PROPERTY_NAME, arrayOf("foo1", "foo2", "foo3"))
    val regexTextField = regexTextField(historyPropertyName = HISTORY_PROPERTY_NAME, historySize = 3)
    val historyExtension = regexTextField.textField.getExtension(AllIcons.Actions.SearchWithHistory)

    regexTextField.text = "bar"
    historyExtension.actionOnClick.run()

    assertThat(properties.getValues(HISTORY_PROPERTY_NAME)).asList().containsExactly("bar", "foo1", "foo2").inOrder()
  }

  // No need to repeat all the history tests using the KeyEvent. We will just do one.
  @Test
  fun historyExtension_enterAddsToHistory() {
    properties.setValues(HISTORY_PROPERTY_NAME, arrayOf("foo"))
    val regexTextField = regexTextField(historyPropertyName = HISTORY_PROPERTY_NAME)

    regexTextField.text = "bar"
    val keyEvent = KeyEvent(regexTextField.textField, 0, 0L, 0, KeyEvent.VK_ENTER, '\n')
    regexTextField.textField.keyListeners.forEach { it.keyPressed(keyEvent) }

    assertThat(keyEvent.isConsumed).isTrue()
    assertThat(properties.getValues(HISTORY_PROPERTY_NAME)).asList().containsExactly("bar", "foo").inOrder()
  }

  private fun regexTextField(
    disposableParent: Disposable = projectRule.project,
    historyPropertyName: String = "test",
    historySize: Int = 5,
    delayUpdateMs: Long = 0L,
    propertiesComponent: PropertiesComponent = PropertiesComponent.getInstance(),
    coroutineScope: CoroutineScope = AndroidCoroutineScope(disposableParent, AndroidDispatchers.uiThread),
  ) = RegexTextField(historyPropertyName, historySize, delayUpdateMs, propertiesComponent, coroutineScope)
}

private data class ExtensionInfo(val icon: Icon, val hoveredIcon: Icon, val iconBeforeText: Boolean)

private fun Extension.toExtensionInfo() =
  ExtensionInfo(getIcon(/* hovered=*/ false), getIcon(/* hovered=*/ true), isIconBeforeText)

private val RegexTextField.textField
  get() = getComponent(0) as ExtendableTextField

private fun ExtendableTextField.getExtension(icon: Icon) = extensions.first { it.getIcon(/* hovered=*/ false) == icon }
