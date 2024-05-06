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
package com.android.tools.idea.run.deployment.selector

import com.android.tools.adtui.TreeWalker
import com.android.tools.adtui.swing.createModalDialogAndInteractWithIt
import com.android.tools.adtui.swing.enableHeadlessDialogs
import com.android.tools.idea.run.LaunchCompatibility
import com.android.tools.idea.run.LaunchCompatibility.State
import com.android.tools.idea.testing.AndroidProjectRule
import com.android.tools.idea.testing.onEdt
import com.google.common.truth.Truth.assertThat
import com.intellij.openapi.ui.DialogWrapper
import com.intellij.testFramework.RunsInEdt
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import javax.swing.JButton
import javax.swing.JCheckBox
import javax.swing.JLabel

/** Test for [SelectedDevicesErrorDialog]. */
internal class SelectedDevicesErrorDialogTest {
  @get:Rule val projectRule = AndroidProjectRule.inMemory().onEdt()

  @Before
  fun enableHeadlessDialogs() {
    enableHeadlessDialogs(projectRule.fixture.testRootDisposable)
  }

  @RunsInEdt
  @Test
  fun testErrorDevice() {
    val device =
      createDevice(
        "Pixel 3 API 29",
        launchCompatibility = LaunchCompatibility(State.ERROR, "error message")
      )

    val dialog = SelectedDevicesErrorDialog(projectRule.project, listOf(device))

    createModalDialogAndInteractWithIt({ dialog.show() }) { dialogWrapper ->
      val treeWalker = TreeWalker(dialogWrapper.rootPane)
      val title = treeWalker.descendants().filterIsInstance<JLabel>().find { it.text == "Error" }
      assertThat(title).isNotNull()
      val message =
        treeWalker.descendants().filterIsInstance<JLabel>().find {
          it.text?.contains("error message") == true
        }
      assertThat(message).isNotNull()
      val buttons = treeWalker.descendants().filterIsInstance<JButton>()
      assertThat(buttons.size).isEqualTo(1)
      assertThat(buttons.first().text).isEqualTo("OK")
      buttons.first().doClick()
    }

    assertThat(dialog.exitCode).isEqualTo(DialogWrapper.CANCEL_EXIT_CODE)
  }

  @RunsInEdt
  @Test
  fun testWarningDevice() {
    val device =
      createDevice(
        "Pixel 3 API 29",
        launchCompatibility = LaunchCompatibility(State.WARNING, "warning message")
      )

    val dialog = SelectedDevicesErrorDialog(projectRule.project, listOf(device))

    createModalDialogAndInteractWithIt({ dialog.show() }) { dialogWrapper ->
      val treeWalker = TreeWalker(dialogWrapper.rootPane)
      val title = treeWalker.descendants().filterIsInstance<JLabel>().find { it.text == "Warning" }
      assertThat(title).isNotNull()
      val message =
        treeWalker.descendants().filterIsInstance<JLabel>().find {
          it.text == "<html><div>warning message on device Pixel 3 API 29</div></html>"
        }
      assertThat(message).isNotNull()
      val buttons = treeWalker.descendants().filterIsInstance<JButton>()
      assertThat(buttons.size).isEqualTo(2)
      assertThat(buttons.map { it.text }).containsAllOf("Cancel", "Continue")
      buttons.find { it.text == "Continue" }!!.doClick()
    }

    assertThat(dialog.exitCode).isEqualTo(DialogWrapper.OK_EXIT_CODE)
  }

  @RunsInEdt
  @Test
  fun testMultipleDevices() {
    val deviceWithWarning =
      createDevice(
        "Pixel 3 API 29",
        launchCompatibility = LaunchCompatibility(State.WARNING, "warning message")
      )

    val deviceWithError =
      createDevice(
        "Pixel 3 API 30",
        launchCompatibility = LaunchCompatibility(State.ERROR, "error message")
      )

    val dialog =
      SelectedDevicesErrorDialog(projectRule.project, listOf(deviceWithError, deviceWithWarning))

    createModalDialogAndInteractWithIt({ dialog.show() }) { dialogWrapper ->
      val treeWalker = TreeWalker(dialogWrapper.rootPane)
      val title = treeWalker.descendants().filterIsInstance<JLabel>().find { it.text == "Error" }
      assertThat(title).isNotNull()
      val warning =
        treeWalker.descendants().filterIsInstance<JLabel>().find {
          it.text == "<html><div>warning message on device Pixel 3 API 29</div></html>"
        }
      assertThat(warning).isNotNull()
      val error =
        treeWalker.descendants().filterIsInstance<JLabel>().find {
          it.text == "<html><div>error message on device Pixel 3 API 30</div></html>"
        }
      assertThat(error).isNotNull()
      val buttons = treeWalker.descendants().filterIsInstance<JButton>()
      assertThat(buttons.size).isEqualTo(1)
      assertThat(buttons.first().text).isEqualTo("OK")
      buttons.first().doClick()
    }

    assertThat(dialog.exitCode).isEqualTo(DialogWrapper.CANCEL_EXIT_CODE)
  }

  @RunsInEdt
  @Test
  fun rememberCheckboxState() {
    val deviceWithWarning =
      createDevice(
        "Pixel 3 API 29",
        launchCompatibility = LaunchCompatibility(State.WARNING, "warning message")
      )

    var dialog = SelectedDevicesErrorDialog(projectRule.project, listOf(deviceWithWarning))

    // Select checkbox.
    createModalDialogAndInteractWithIt({ dialog.show() }) { dialogWrapper ->
      val treeWalker = TreeWalker(dialogWrapper.rootPane)
      val checkbox =
        treeWalker.descendants().filterIsInstance<JCheckBox>().find {
          it.text == "Do not ask again for this session"
        }!!
      assertThat(checkbox.isSelected).isFalse()
      checkbox.isSelected = true
      treeWalker
        .descendants()
        .filterIsInstance<JButton>()
        .find { it.text == "Continue" }!!
        .doClick()
    }

    dialog = SelectedDevicesErrorDialog(projectRule.project, listOf(deviceWithWarning))

    // Check that we remember checkbox state.
    createModalDialogAndInteractWithIt({ dialog.show() }) { dialogWrapper ->
      val treeWalker = TreeWalker(dialogWrapper.rootPane)
      val checkbox =
        treeWalker.descendants().filterIsInstance<JCheckBox>().find {
          it.text == "Do not ask again for this session"
        }!!
      assertThat(checkbox.isSelected).isTrue()
    }
  }
}
