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
package com.android.tools.idea.tests.gui.framework.fixture.newpsd

import com.android.tools.idea.tests.gui.framework.DialogContainerFixture
import com.android.tools.idea.tests.gui.framework.GuiTests
import com.android.tools.idea.tests.gui.framework.fixture.IdeFrameFixture
import com.android.tools.idea.tests.gui.framework.fixupWaiting
import com.android.tools.idea.tests.gui.framework.matcher.Matchers
import com.intellij.openapi.roots.ui.configuration.SdkComboBox
import org.fest.swing.core.Robot
import org.fest.swing.fixture.JComboBoxFixture
import javax.swing.JDialog

class GradleSettingsDialogFixture(
  val container: JDialog,
  private val ideFrameFixture: IdeFrameFixture
) : DialogContainerFixture {

  private val robot = ideFrameFixture.robot().fixupWaiting()
  override fun target(): JDialog = container
  override fun robot(): Robot = robot

  override fun maybeRestoreLostFocus() {
    ideFrameFixture.requestFocusIfLost()
  }

  fun gradleJDKComboBox(): JComboBoxFixture{
    val jdkComboBox = robot().finder().findByType(target(), SdkComboBox::class.java)
    return JComboBoxFixture(robot(), jdkComboBox)
  }

  fun clickCancel(): IdeFrameFixture {
    clickCancelAndWaitDialogDisappear()
    return ideFrameFixture
  }

  companion object{
    fun find(ideFrameFixture: IdeFrameFixture): GradleSettingsDialogFixture {
      val dialog = GuiTests.waitUntilShowing(ideFrameFixture.robot(), Matchers.byTitle(JDialog::class.java, "Gradle"))
      return GradleSettingsDialogFixture(dialog, ideFrameFixture)
    }
  }
}

fun IdeSdksLocationConfigurableFixture.selectGradleSetting(ideframe: IdeFrameFixture): GradleSettingsDialogFixture{
  clickGradleSetting()
  val dialog = GuiTests.waitUntilShowing(ideframe.robot(), Matchers.byTitle(JDialog::class.java, "Gradle"))
  return GradleSettingsDialogFixture(dialog, ideframe)
}