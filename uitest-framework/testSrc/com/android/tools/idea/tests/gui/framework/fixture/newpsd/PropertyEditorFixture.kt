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

import com.android.tools.idea.tests.gui.framework.IdeFrameContainerFixture
import com.android.tools.idea.tests.gui.framework.findByType
import com.android.tools.idea.tests.gui.framework.fixture.IdeFrameFixture
import com.android.tools.idea.tests.gui.framework.robot
import com.intellij.openapi.ui.ComboBox
import com.intellij.ui.SimpleColoredComponent
import org.fest.swing.edt.GuiQuery
import org.fest.swing.fixture.JComboBoxFixture
import org.fest.swing.fixture.JTextComponentFixture
import org.jetbrains.kotlin.utils.addToStdlib.safeAs
import java.awt.Container
import javax.swing.JList
import javax.swing.JTextField

private val REFERENCE_JLIST = GuiQuery.get { JList<Any>() }

class PropertyEditorFixture(
  override val ideFrameFixture: IdeFrameFixture,
  override val container: Container
) : IdeFrameContainerFixture {

  fun selectItem(text: String) {
    val comboBoxFixture = JComboBoxFixture(robot(), robot().finder().findByType<ComboBox<*>>(
      container))
    comboBoxFixture.replaceCellReader { comboBox, index ->
      val item = comboBox.getItemAt(index)
      val renderer = comboBox.renderer
      val rendererComponent = renderer.getListCellRendererComponent(
        REFERENCE_JLIST, item, index, true, true)
      rendererComponent.safeAs<SimpleColoredComponent>()?.toString().orEmpty()
    }
    comboBoxFixture.selectItem(text)
  }

  fun enterText(text: String) {
    val comboBox = JComboBoxFixture(
      robot(),
      robot().finder().findByType<ComboBox<*>>(container))
    comboBox.enterText(text)
    robot().type(9.toChar())
  }

  fun getText(): String {
    val textFiexture = JTextComponentFixture(
      robot(),
      robot().finder().findByType<JTextField>(
        robot().finder().findByType<ComboBox<*>>(container)))
    return textFiexture.text().orEmpty()
  }
}
