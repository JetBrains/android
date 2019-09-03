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

import com.android.tools.idea.tests.gui.framework.GuiTests.findAndClickButton
import com.android.tools.idea.tests.gui.framework.GuiTests.waitUntilShowingAndEnabled
import com.android.tools.idea.tests.gui.framework.fixture.wizard.AbstractWizardFixture
import com.android.tools.idea.tests.gui.framework.matcher.Matchers
import com.google.common.base.Verify
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.editor.impl.EditorComponentImpl
import com.intellij.openapi.options.ConfigurationException
import com.intellij.openapi.wm.impl.IdeFrameImpl
import org.fest.swing.core.GenericTypeMatcher
import org.fest.swing.core.Robot
import org.fest.swing.exception.WaitTimedOutError
import org.fest.swing.finder.WindowFinder.findDialog
import org.fest.swing.fixture.JComboBoxFixture
import org.fest.swing.fixture.JPanelFixture
import org.fest.swing.fixture.JRadioButtonFixture
import org.fest.swing.timing.Wait
import org.junit.Assert.fail
import java.io.File
import javax.swing.*
import javax.swing.text.JTextComponent

class ImportBazelProjectWizardFixture(robot: Robot, target: JDialog) :
    AbstractWizardFixture<ImportBazelProjectWizardFixture>(ImportBazelProjectWizardFixture::class.java, robot, target) {
  val id: String
    get() = ImportBazelProjectWizardFixture::class.java.name

  private val logger = Logger.getInstance(id)

  companion object {
    private const val CONTENT_MISMATCH_MESSAGE = "Contents of %s do not match what was entered during project import. Expecting '%s', but found '%s'."

    private fun verifyContentMatches(componentName: String, expecting: Any?, found: Any?) =
        Verify.verify(expecting == found, CONTENT_MISMATCH_MESSAGE, componentName, expecting.toString(), found.toString())

    fun find(robot: Robot): ImportBazelProjectWizardFixture {
      val wizardDialog = findDialog(object : GenericTypeMatcher<JDialog>(JDialog::class.java) {
        override fun isMatching(dialog: JDialog) = dialog.title == "Import Project from Bazel" && dialog.isShowing
      }).withTimeout(500).using(robot)

      return ImportBazelProjectWizardFixture(robot, wizardDialog.target() as JDialog)
    }
  }

  fun setBazelBinaryPath(path: String): ImportBazelProjectWizardFixture {
    logger.info("Setting bazel binary path = $path")
    waitUntilShowingAndEnabled(robot(), target(), Matchers.byName(JPanel::class.java, "bazel-binary-path-field"))
    val bazelBinaryComboBox = JPanelFixture(robot(), "bazel-binary-path-field").comboBox().replaceText(path)

    verifyContentMatches("bazel-binary-path-field", path, bazelBinaryComboBox.target().editor.item)
    return this
  }

  fun setWorkspacePath(path: String): ImportBazelProjectWizardFixture {
    logger.info("Setting workspace directory = $path")
    waitUntilShowingAndEnabled(robot(), target(), Matchers.byName(JComboBox::class.java, "workspace-directory-field"))
    val workspaceComboBox = JComboBoxFixture(robot(), "workspace-directory-field").replaceText(path)

    verifyContentMatches("workspace-directory-field", path, workspaceComboBox.target().editor.item)
    return this
  }

  fun selectGenerateFromBuildFileOptionAndSetPath(path: String): ImportBazelProjectWizardFixture {
    val generateFromBuildButtonMatcher = object : GenericTypeMatcher<JRadioButton>(JRadioButton::class.java) {
      override fun isMatching(component: JRadioButton): Boolean {
        return (component.text == "Generate from BUILD file")
      }
    }

    logger.info("Find 'Generate from BUILD file' radio button")
    waitUntilShowingAndEnabled(robot(), target(), generateFromBuildButtonMatcher)
    JRadioButtonFixture(robot(), robot().finder().find(generateFromBuildButtonMatcher)).click()

    logger.info("Setting build file path = $path")
    waitUntilShowingAndEnabled(robot(), target(), Matchers.byName(JComboBox::class.java, "build-file-path-field"))
    val buildFileComboBox = JComboBoxFixture(robot(), "build-file-path-field").replaceText(path)

    verifyContentMatches("build-file-path-field", path, buildFileComboBox.target().editor.item)
    return this
  }

  fun uncommentApi27(buildFile: File): ImportBazelProjectWizardFixture {
    logger.info("Modifying BUILD file content")
    waitUntilShowingAndEnabled(robot(), target(), Matchers.byType(EditorComponentImpl::class.java))
    val editor = robot().finder().findByType(EditorComponentImpl::class.java)

    // Maybe let test configs select which platform to use?
    val toSelect = "# android_sdk_platform: android-27"
    val toPaste = "android_sdk_platform: android-27"
    val selectionStartIndex = editor.text.indexOf(toSelect)

    if (selectionStartIndex < 0) {
      logger.info("Assuming api 27 in bazel test run.")

      // Debug logs for b/73037396, comments #30 through #32
      logger.info("Project view generated by the wizard:")
      logger.info(editor.text)
      logger.info("Actual contents of BUILD file at ${buildFile.canonicalPath}")
      logger.info(buildFile.readText())
    }
    else {
      val selectionEndIndex = selectionStartIndex + toSelect.length

      SwingUtilities.invokeAndWait {
        val editableText = editor.accessibleContext.accessibleEditableText
        editableText.selectText(selectionStartIndex, selectionEndIndex)
        Verify.verify(editableText.selectedText == toSelect)
        editableText.replaceText(selectionStartIndex, toSelect.length, toPaste)
      }
    }
    return this
  }

  fun waitForProjectValidation(): ImportBazelProjectWizardFixture {
    Wait.seconds(300).expecting("Project Validation to finish").until {
      val errorDialog = try {
        findDialog(
          Matchers.byTitle(JDialog::class.java, ConfigurationException.DEFAULT_TITLE).andIsShowing()
        ).withTimeout(1).using(robot())
      }
      catch (e: WaitTimedOutError) {
        null
      }

      // If an error dialog appears, then we won't be able to import the project since it isn't valid.
      // In this case, there's no reason to continue the test.
      if (errorDialog != null) {
        val errorText = robot().finder().findByType(errorDialog.target(), JTextComponent::class.java).text
        fail("Project Validation failed:\n$errorText")
      }

      // Otherwise, keep waiting until the IDE frame is available.
      robot().finder().findAll(Matchers.byType(IdeFrameImpl::class.java).andIsShowing()).isNotEmpty()
    }
    return this
  }

  fun clickFinish(): ImportBazelProjectWizardFixture {
    logger.info("Finish import bazel project wizard")
    findAndClickButton(this, "Finish")
    return this
  }
}