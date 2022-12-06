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

import com.android.tools.idea.fonts.MoreFontsDialog
import com.android.tools.idea.tests.gui.framework.GuiTests
import org.fest.swing.core.Robot
import org.fest.swing.fixture.JListFixture
import javax.swing.JList

/**
 * Fixture for the More Fonts Dialog for choosing a downloadable font
 */
class MoreFontsDialogFixture(robot: Robot, dialogAndWrapper: DialogAndWrapper<MoreFontsDialog>) :
  IdeaDialogFixture<MoreFontsDialog>(robot, dialogAndWrapper) {

  fun clickOk() {
    GuiTests.findAndClickOkButton(this)
    waitUntilNotShowing()
  }

  fun selectFont(fontName: String): MoreFontsDialogFixture {
    JListFixture(robot(), robot().finder().findByName("Font list", JList::class.java)).clickItem(fontName)
    return this
  }

  companion object {
    @JvmStatic
    fun find(robot: Robot): MoreFontsDialogFixture {
      return MoreFontsDialogFixture(robot, find(robot, MoreFontsDialog::class.java))
    }
  }
}