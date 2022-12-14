/*
 * Copyright (C) 2018 The Android Open Source Project
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
package com.android.tools.idea.tests.gui.framework.fixture.newpsd

import com.android.tools.idea.tests.gui.framework.findByType
import com.intellij.openapi.ui.ComboBox
import com.intellij.ui.SimpleColoredComponent
import org.fest.swing.core.Robot
import org.fest.swing.edt.GuiQuery
import org.fest.swing.fixture.ContainerFixture
import org.fest.swing.fixture.JComboBoxFixture
import org.fest.swing.fixture.JTextComponentFixture
import java.awt.Container
import java.awt.event.InputEvent
import java.awt.event.KeyEvent
import javax.swing.JList
import javax.swing.JTextField

private val REFERENCE_JLIST = GuiQuery.get { JList<Any>() }

class PropertyEditorFixture(
  val robot: Robot,
  val container: Container
) : ContainerFixture<Container> {
  override fun target(): Container = container
  override fun robot(): Robot = robot


  fun selectItem(text: String) {
    val comboBoxFixture = createComboBoxFixture()
    comboBoxFixture.selectItem(text)
  }

  fun selectItemWithKeyboard(text: String, andTab: Boolean = false) {
    val comboBoxFixture = createComboBoxFixture()
    comboBoxFixture.focus()
    val contents = comboBoxFixture.contents()
    val index = contents.indexOf(text)
    if (index < 0) throw IllegalStateException("'$text' not found. Available items: ${contents.joinToString()}")
    robot().pressAndReleaseKey(KeyEvent.VK_DOWN, KeyEvent.ALT_DOWN_MASK)
    for (i in 0..index) {
      robot().pressAndReleaseKey(KeyEvent.VK_DOWN)
    }
    if (andTab) robot().pressAndReleaseKey(KeyEvent.VK_TAB) else robot().pressAndReleaseKey(KeyEvent.VK_ENTER)
  }

  fun enterText(text: String) {
    val comboBox = JComboBoxFixture(
      robot(),
      robot().finder().findByType<ComboBox<*>>(container))
    comboBox.selectAllText()
    comboBox.enterText(text)
    robot().type(9.toChar())
  }

  fun getText(): String {
    val textFixture = JTextComponentFixture(
      robot(),
      robot().finder().findByType<JTextField>(
        robot().finder().findByType<ComboBox<*>>(container)))
    return textFixture.text().orEmpty()
  }

  fun invokeExtractVariable(): ExtractVariableFixture {
    val comboBoxFixture = createComboBoxFixture()
    comboBoxFixture.focus()
    robot().pressAndReleaseKey(KeyEvent.VK_ENTER, InputEvent.SHIFT_MASK)
    return ExtractVariableFixture.find(robot())
  }

  private fun createComboBoxFixture(): JComboBoxFixture {
    val comboBoxFixture = JComboBoxFixture(robot(), robot().finder().findByType<ComboBox<*>>(
        container))
    comboBoxFixture.replaceCellReader { comboBox, index ->
      val item = comboBox.getItemAt(index)
      val renderer = comboBox.renderer
      val rendererComponent = renderer.getListCellRendererComponent(
          REFERENCE_JLIST, item, index, true, true)
      (rendererComponent as? SimpleColoredComponent)?.toString().orEmpty()
    }
    return comboBoxFixture
  }
}
