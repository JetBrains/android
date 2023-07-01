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

import com.android.tools.idea.gradle.ui.GradleJdkPathEditComboBox
import com.android.tools.idea.tests.gui.framework.fixture.IdeSettingsDialogFixture
import com.intellij.openapi.roots.ui.configuration.SdkComboBox
import com.intellij.openapi.ui.ComboBox
import org.fest.swing.fixture.JComboBoxFixture

class GradleSettingsDialogFixture (settingsDialog: IdeSettingsDialogFixture) {
  val target = settingsDialog.target()
  val robot = settingsDialog.robot()

  fun gradleJDKComboBox(): JComboBoxFixture{
    val jdkComboBox = robot.finder().findByType(target, SdkComboBox::class.java)
    return JComboBoxFixture(robot, jdkComboBox)
  }

  fun gradleJDKPathComboBox(): JComboBoxFixture {
    val jdkComboBox = robot.finder().findByType(target, GradleJdkPathEditComboBox::class.java)
    val gradlePathComboBox = robot.finder().findByType(jdkComboBox, ComboBox::class.java)
    return JComboBoxFixture(robot, gradlePathComboBox)
  }
}