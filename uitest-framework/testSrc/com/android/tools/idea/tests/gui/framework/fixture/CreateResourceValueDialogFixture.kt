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
package com.android.tools.idea.tests.gui.framework.fixture

import com.android.tools.idea.tests.gui.framework.GuiTests
import com.intellij.ui.components.JBLabel
import org.fest.swing.core.Robot
import org.fest.swing.fixture.JComboBoxFixture
import org.fest.swing.fixture.JLabelFixture
import org.fest.swing.fixture.JTextComponentFixture
import org.jetbrains.android.actions.CreateXmlResourceDialog
import javax.swing.JComboBox
import javax.swing.JTextArea
import javax.swing.JTextField

/**
 * Dialog fixture for the [CreateXmlResourceDialog].
 */
class CreateResourceValueDialogFixture private constructor(
  robot: Robot,
  dialogAndWrapper: DialogAndWrapper<CreateXmlResourceDialog>
) : IdeaDialogFixture<CreateXmlResourceDialog>(robot, dialogAndWrapper) {
  companion object {
    fun find(robot: Robot): CreateResourceValueDialogFixture {
      return CreateResourceValueDialogFixture(robot, find(robot, CreateXmlResourceDialog::class.java))
    }
  }

  /**
   * Populates the textField for the resource name.
   */
  fun setResourceName(name: String): CreateResourceValueDialogFixture {
    val textField = robot().finder().findByLabel(target(), "Resource name:", JTextField::class.java, true)
    JTextComponentFixture(robot(), textField).deleteText().enterText(name)
    return this
  }

  /**
   * Populates the textField for the resource value.
   */
  fun setResourceValue(value: String): CreateResourceValueDialogFixture {
    val textArea = robot().finder().findByType(target(),JTextArea::class.java, true)
    JTextComponentFixture(robot(), textArea).deleteText().enterText(value)
    return this
  }

  fun clickOk() {
    assert(dialogWrapper.resourceName.isNotEmpty() && dialogWrapper.value.isNotEmpty())
    GuiTests.findAndClickOkButton(this)
  }
}