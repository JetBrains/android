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
package com.android.tools.idea.tests.gui.framework.fixture

import com.android.tools.idea.tests.gui.framework.GuiTests
import com.intellij.openapi.ui.DialogWrapper
import org.fest.swing.core.Robot

/**
 * Test fixture for the consent dialog
 */
class ConsentDialogFixture(robot: Robot, dialogAndWrapper: DialogAndWrapper<DialogWrapper>) :
  IdeaDialogFixture<DialogWrapper>(robot, dialogAndWrapper) {

  fun optIn() {
    GuiTests.findAndClickButton(this, "Send usage statistics")
  }

  fun decline() {
    GuiTests.findAndClickButton(this, "Don't send")
  }

  companion object {
    @JvmStatic
    fun find(robot: Robot): ConsentDialogFixture = ConsentDialogFixture(robot, find(robot, DialogWrapper::class.java))
  }
}