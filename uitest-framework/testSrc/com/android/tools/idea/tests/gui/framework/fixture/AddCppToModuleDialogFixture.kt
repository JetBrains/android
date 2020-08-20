/*
 * Copyright (C) 2020 The Android Open Source Project
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
import com.android.tools.idea.tests.gui.framework.matcher.Matchers
import com.intellij.openapi.ui.DialogWrapper
import com.intellij.openapi.ui.TextFieldWithBrowseButton
import org.fest.swing.fixture.JButtonFixture
import org.fest.swing.fixture.JRadioButtonFixture
import org.fest.swing.fixture.JTextComponentFixture
import javax.swing.JButton
import javax.swing.JDialog
import javax.swing.JRadioButton

class AddCppToModuleDialogFixture(val ideFrameFixture: IdeFrameFixture,
                                  dialogAndWrapper: DialogAndWrapper<DialogWrapper>) :
  IdeaDialogFixture<DialogWrapper>(ideFrameFixture.robot(), dialogAndWrapper) {

  fun selectCreateCppFiles(): AddCppToModuleDialogFixture {
    val radioButton = GuiTests.waitUntilFound(
      ideFrameFixture.robot(),
      target(),
      Matchers.byText(JRadioButton::class.java, "Create CMakeLists.txt at the following location")
    )
    JRadioButtonFixture(ideFrameFixture.robot(), radioButton).click()
    return this
  }

  fun selectLinkCppProject(): AddCppToModuleDialogFixture {
    val radioButton = GuiTests.waitUntilFound(
      ideFrameFixture.robot(),
      target(),
      Matchers.byText(JRadioButton::class.java, "Link an existing CMakeLists.txt or Android.mk to this module")
    )
    JRadioButtonFixture(ideFrameFixture.robot(), radioButton).click()
    return this
  }

  val enabledTextField: JTextComponentFixture
    get() {
      val matcher = Matchers.byType(TextFieldWithBrowseButton::class.java).andIsEnabled()
      val textFieldWithBrowseButton = GuiTests.waitUntilFound(ideFrameFixture.robot(), target(), matcher)
      return JTextComponentFixture(ideFrameFixture.robot(), textFieldWithBrowseButton.textField)
    }

  val okButton: JButtonFixture
    get() = JButtonFixture(
      ideFrameFixture.robot(),
      GuiTests.waitUntilFound(ideFrameFixture.robot(), target(), Matchers.byText(JButton::class.java, "OK")))

  companion object {
    fun find(ideFrameFixture: IdeFrameFixture): AddCppToModuleDialogFixture =
      AddCppToModuleDialogFixture(ideFrameFixture,
                                  find(ideFrameFixture.robot(),
                                       DialogWrapper::class.java,
                                       Matchers.byTitle(JDialog::class.java, "Add C++ to Module")))
  }
}