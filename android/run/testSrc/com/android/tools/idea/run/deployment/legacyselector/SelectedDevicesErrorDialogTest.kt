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
package com.android.tools.idea.run.deployment.legacyselector

import com.android.tools.adtui.TreeWalker
import com.android.tools.adtui.swing.createModalDialogAndInteractWithIt
import com.android.tools.adtui.swing.enableHeadlessDialogs
import com.android.tools.idea.run.AndroidDevice
import com.android.tools.idea.run.LaunchCompatibility
import com.android.tools.idea.run.LaunchCompatibility.State
import com.android.tools.idea.testing.AndroidProjectRule
import com.android.tools.idea.testing.onEdt
import com.google.common.truth.Truth.assertThat
import com.intellij.openapi.ui.DialogWrapper
import com.intellij.testFramework.RunsInEdt
import org.jsoup.Jsoup
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.mockito.Mockito
import javax.swing.JButton
import javax.swing.JCheckBox
import javax.swing.JEditorPane

/**
 * Test for [SelectedDevicesErrorDialog].
 */
internal class SelectedDevicesErrorDialogTest {
  @get:Rule
  val projectRule = AndroidProjectRule.inMemory().onEdt()

  @Before
  fun enableHeadlessDialogs() {
    enableHeadlessDialogs(projectRule.fixture.testRootDisposable)
  }

  @RunsInEdt
  @Test
  fun testErrorDevice() {
    val device = VirtualDevice.Builder()
      .setName("Pixel 3 API 29")
      .setKey(VirtualDeviceName("Pixel_3_API_29"))
      .setAndroidDevice(Mockito.mock(AndroidDevice::class.java))
      .setLaunchCompatibility(LaunchCompatibility(State.ERROR, "error message"))
      .build()

    val dialog = SelectedDevicesErrorDialog(projectRule.project, listOf(device))

    createModalDialogAndInteractWithIt({ dialog.show() }) { dialogWrapper ->
      val treeWalker = TreeWalker(dialogWrapper.rootPane)
      assertThat(dialogWrapper.title == "Error")
      val message = treeWalker.descendants().filterIsInstance<JEditorPane>()
        .find { Jsoup.parse(it.text).text() == "error message on device Pixel 3 API 29" }
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
    val device = VirtualDevice.Builder()
      .setName("Pixel 3 API 29")
      .setKey(VirtualDeviceName("Pixel_3_API_29"))
      .setAndroidDevice(Mockito.mock(AndroidDevice::class.java))
      .setLaunchCompatibility(LaunchCompatibility(State.WARNING, "warning message"))
      .build()

    val dialog = SelectedDevicesErrorDialog(projectRule.project, listOf(device))

    createModalDialogAndInteractWithIt({ dialog.show() }) { dialogWrapper ->
      val treeWalker = TreeWalker(dialogWrapper.rootPane)
      assertThat(dialogWrapper.title == "Warning")
      val message = treeWalker.descendants().filterIsInstance<JEditorPane>()
        .find { Jsoup.parse(it.text).text() == "warning message on device Pixel 3 API 29" }
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
    val deviceWithWarning = VirtualDevice.Builder()
      .setName("Pixel 3 API 29")
      .setKey(VirtualDeviceName("Pixel_3_API_29"))
      .setAndroidDevice(Mockito.mock(AndroidDevice::class.java))
      .setLaunchCompatibility(LaunchCompatibility(State.WARNING, "warning message"))
      .build()

    val deviceWithError = VirtualDevice.Builder()
      .setName("Pixel 3 API 30")
      .setKey(VirtualDeviceName("Pixel_3_API_30"))
      .setAndroidDevice(Mockito.mock(AndroidDevice::class.java))
      .setLaunchCompatibility(LaunchCompatibility(State.ERROR, "error message"))
      .build()

    val dialog = SelectedDevicesErrorDialog(projectRule.project, listOf(deviceWithError, deviceWithWarning))

    createModalDialogAndInteractWithIt({ dialog.show() }) { dialogWrapper ->
      val treeWalker = TreeWalker(dialogWrapper.rootPane)
      assertThat(dialogWrapper.title == "Warning")
      val warning = treeWalker.descendants().filterIsInstance<JEditorPane>()
        .find { Jsoup.parse(it.text).text() == "warning message on device Pixel 3 API 29" }
      assertThat(warning).isNotNull()
      val error = treeWalker.descendants().filterIsInstance<JEditorPane>()
        .find { Jsoup.parse(it.text).text() == "error message on device Pixel 3 API 30" }
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
    val deviceWithWarning = VirtualDevice.Builder()
      .setName("Pixel 3 API 29")
      .setKey(VirtualDeviceName("Pixel_3_API_29"))
      .setAndroidDevice(Mockito.mock(AndroidDevice::class.java))
      .setLaunchCompatibility(LaunchCompatibility(State.WARNING, "warning message"))
      .build()

    var dialog = SelectedDevicesErrorDialog(projectRule.project, listOf(deviceWithWarning))

    // Select checkbox.
    createModalDialogAndInteractWithIt({ dialog.show() }) { dialogWrapper ->
      val treeWalker = TreeWalker(dialogWrapper.rootPane)
      val checkbox = treeWalker.descendants().filterIsInstance<JCheckBox>().find { it.text == "Do not ask again for this session" }!!
      assertThat(checkbox.isSelected).isFalse()
      checkbox.isSelected = true
      treeWalker.descendants().filterIsInstance<JButton>().find { it.text == "Continue" }!!.doClick()
    }

    dialog = SelectedDevicesErrorDialog(projectRule.project, listOf(deviceWithWarning))

    // Check that we remember checkbox state.
    createModalDialogAndInteractWithIt({ dialog.show() }) { dialogWrapper ->
      val treeWalker = TreeWalker(dialogWrapper.rootPane)
      val checkbox = treeWalker.descendants().filterIsInstance<JCheckBox>().find { it.text == "Do not ask again for this session" }!!
      assertThat(checkbox.isSelected).isTrue()
    }
  }
}
