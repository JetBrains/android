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

import com.android.tools.idea.tests.gui.framework.GuiTestRule
import com.android.tools.idea.tests.gui.framework.GuiTests
import com.android.tools.idea.tests.gui.framework.matcher.Matchers
import com.android.tools.idea.ui.resourcechooser.CompactResourcePicker
import com.intellij.ui.components.JBList
import com.intellij.util.ui.UIUtil
import org.fest.swing.fixture.JListFixture
import org.fest.swing.fixture.JPanelFixture
import org.fest.swing.timing.Wait
import java.util.regex.Pattern
import javax.swing.JPanel

/**
 * Test fixture for the [CompactResourcePicker] popup component.
 */
class CompactResourcePickerFixture private constructor(private val guiTest: GuiTestRule, target: JPanel)
  : JPanelFixture(guiTest.robot(), target) {

  companion object {
    fun find(guiTest: GuiTestRule): CompactResourcePickerFixture {
      val picker = GuiTests.waitUntilShowing(guiTest.robot(), Matchers.byType(CompactResourcePicker::class.java))

      return CompactResourcePickerFixture(guiTest, picker)
    }
  }

  init {
    // Wait for the picker to be properly initialized, before interacting with it.
    Wait.seconds(10).expecting("Resources to load in picker").until {
      val list = UIUtil.findComponentOfType(target, JBList::class.java)
      return@until list != null && list.model.size > 0
    }
  }

  /**
   * Selects (equivalent to a single click) the desired resource in the list of visible resources. Has to match the exact resource name.
   */
  fun selectResource(resourceName: String): CompactResourcePickerFixture {
    guiTest.robot().finder().findByType(JBList::class.java).also {
      JListFixture(robot(), it).item(Pattern.compile(".*(name=${resourceName},).*")).click()
    }
    return this
  }
}