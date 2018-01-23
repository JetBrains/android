/*
 * Copyright (C) 2017 The Android Open Source Project
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
package com.android.tools.idea.tests.gui.framework.bazel.fixture

import com.android.tools.idea.tests.gui.framework.GuiTests.findAndClickButtonWhenEnabled
import com.android.tools.idea.tests.gui.framework.fixture.wizard.AbstractWizardFixture
import com.intellij.openapi.editor.impl.EditorComponentImpl
import org.fest.swing.core.GenericTypeMatcher
import org.fest.swing.core.Robot
import org.fest.swing.finder.WindowFinder.findDialog
import java.awt.Component
import java.awt.event.InputEvent.CTRL_MASK
import java.awt.event.KeyEvent.VK_A
import java.awt.event.KeyEvent.VK_DELETE
import javax.swing.JDialog
import javax.swing.JRadioButton
import javax.swing.SwingUtilities

class ImportBazelProjectWizardFixture(robot: Robot, target: JDialog) :
    AbstractWizardFixture<ImportBazelProjectWizardFixture>(ImportBazelProjectWizardFixture::class.java, robot, target) {

  companion object {
    fun find(robot: Robot): ImportBazelProjectWizardFixture {
      val wizardDialog = findDialog(object : GenericTypeMatcher<JDialog>(JDialog::class.java) {
        override fun isMatching(dialog: JDialog) = dialog.title == "Import Project from Bazel" && dialog.isShowing
      }).withTimeout(500).using(robot)

      return ImportBazelProjectWizardFixture(robot, wizardDialog.target() as JDialog)
    }
  }

  private fun selectAndClearTextField(textField: Component) {
    robot().click(textField)
    robot().pressAndReleaseKey(VK_A, CTRL_MASK)
    robot().pressAndReleaseKey(VK_DELETE)
  }

  fun setWorkspacePath(path: String): ImportBazelProjectWizardFixture {
    val textField = robot().finder().findByName("workspace-directory-field")
    selectAndClearTextField(textField)
    robot().enterText(path)

    return this
  }

  fun selectGenerateFromBuildFileOptionAndSetPath(path: String): ImportBazelProjectWizardFixture {
    val generateFromBuildFileOption = robot().finder().find(object : GenericTypeMatcher<JRadioButton>(JRadioButton::class.java) {
      override fun isMatching(component: JRadioButton): Boolean {
        return (component.text == "Generate from BUILD file")
      }
    })
    robot().click(generateFromBuildFileOption)

    val textField = robot().finder().findByName("build-file-path-field")
    selectAndClearTextField(textField)
    robot().enterText(path)

    return this
  }

  fun uncommentApi27(): ImportBazelProjectWizardFixture {
    val editor = robot().finder().findByType(EditorComponentImpl::class.java)

    // Maybe let test configs select which platform to use?
    val toSelect = "# android_sdk_platform: android-27"
    val toPaste = "android_sdk_platform: android-27"
    val selectionStartIndex = editor.text.indexOf(toSelect)

    if (selectionStartIndex < 0) {
      System.out.println("Assuming api 27 in bazel test run.")
    }
    else {
      val selectionEndIndex = selectionStartIndex + toSelect.length

      SwingUtilities.invokeAndWait {
        val editableText = editor.accessibleContext.accessibleEditableText
        editableText.selectText(selectionStartIndex, selectionEndIndex)
        assert(editableText.selectedText == toSelect)
        editableText.replaceText(selectionStartIndex, toSelect.length, toPaste)
      }
    }

    return this
  }

  fun clickFinish(): ImportBazelProjectWizardFixture {
    findAndClickButtonWhenEnabled(this, "Finish")
    return this
  }

}