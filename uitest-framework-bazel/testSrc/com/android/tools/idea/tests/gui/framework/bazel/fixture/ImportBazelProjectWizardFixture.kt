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
import com.google.common.truth.Truth
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.editor.impl.EditorComponentImpl
import org.fest.swing.core.GenericTypeMatcher
import org.fest.swing.core.Robot
import org.fest.swing.finder.WindowFinder.findDialog
import org.fest.swing.fixture.JComboBoxFixture
import org.fest.swing.fixture.JPanelFixture
import org.fest.swing.fixture.JRadioButtonFixture
import org.junit.Assert
import javax.swing.JDialog
import javax.swing.JRadioButton
import javax.swing.SwingUtilities

class ImportBazelProjectWizardFixture(robot: Robot, target: JDialog) :
    AbstractWizardFixture<ImportBazelProjectWizardFixture>(ImportBazelProjectWizardFixture::class.java, robot, target) {
  val id: String
    get() = ImportBazelProjectWizardFixture::class.java.name

  private val logger = Logger.getInstance(id)

  companion object {
    fun find(robot: Robot): ImportBazelProjectWizardFixture {
      val wizardDialog = findDialog(object : GenericTypeMatcher<JDialog>(JDialog::class.java) {
        override fun isMatching(dialog: JDialog) = dialog.title == "Import Project from Bazel" && dialog.isShowing
      }).withTimeout(500).using(robot)

      return ImportBazelProjectWizardFixture(robot, wizardDialog.target() as JDialog)
    }
  }

  fun setBazelBinaryPath(path: String): ImportBazelProjectWizardFixture {
    logger.info("Setting bazel binary path = $path")
    val bazelBinaryComboBox = JPanelFixture(robot(), "bazel-binary-path-field").comboBox().replaceText(path)
    Truth.assertThat(bazelBinaryComboBox.target().editor.item).isEqualTo(path)
    return this
  }

  fun setWorkspacePath(path: String): ImportBazelProjectWizardFixture {
    logger.info("Setting workspace directory = $path")
    val workspaceComboBox = JComboBoxFixture(robot(), "workspace-directory-field").replaceText(path)
    Truth.assertThat(workspaceComboBox.target().editor.item).isEqualTo(path)
    return this
  }

  fun selectGenerateFromBuildFileOptionAndSetPath(path: String): ImportBazelProjectWizardFixture {
    logger.info("Find 'Generate from BUILD file' radio button")
    val generateFromBuildFileOption = robot().finder().find(object : GenericTypeMatcher<JRadioButton>(JRadioButton::class.java) {
      override fun isMatching(component: JRadioButton): Boolean {
        return (component.text == "Generate from BUILD file")
      }
    })
    JRadioButtonFixture(robot(), generateFromBuildFileOption).click()

    logger.info("Setting build file path = $path")
    val buildFileComboBox = JComboBoxFixture(robot(), "build-file-path-field").replaceText(path)
    Truth.assertThat(buildFileComboBox.target().editor.item).isEqualTo(path)
    return this
  }

  fun uncommentApi27(): ImportBazelProjectWizardFixture {
    logger.info("Modifying BUILD file content")
    val editor = robot().finder().findByType(EditorComponentImpl::class.java)

    // Maybe let test configs select which platform to use?
    val toSelect = "# android_sdk_platform: android-27"
    val toPaste = "android_sdk_platform: android-27"
    val selectionStartIndex = editor.text.indexOf(toSelect)

    if (selectionStartIndex < 0) {
      logger.info("Assuming api 27 in bazel test run.")
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
    logger.info("BUILD file content is '${editor.text}'")
    System.out.println("BUILD file content is '${editor.text}'")
    return this
  }

  fun clickFinish(): ImportBazelProjectWizardFixture {
    logger.info("Finish import bazel project wizard")
    findAndClickButtonWhenEnabled(this, "Finish")
    return this
  }

}