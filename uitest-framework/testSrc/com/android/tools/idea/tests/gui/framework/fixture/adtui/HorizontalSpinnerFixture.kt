/*
 * Copyright (C) 2018 The Android Open Source Project
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
package com.android.tools.idea.tests.gui.framework.fixture.adtui

import com.android.tools.adtui.HorizontalSpinner
import com.android.tools.idea.tests.gui.framework.fixture.ComponentFixture
import com.android.tools.idea.tests.gui.framework.matcher.Matchers
import org.fest.swing.core.Robot
import org.fest.swing.fixture.JButtonFixture
import javax.swing.JButton
import javax.swing.JComponent

class HorizontalSpinnerFixture(private val robot: Robot, private val spinner: HorizontalSpinner<*>) :
  ComponentFixture<HorizontalSpinnerFixture, HorizontalSpinner<*>>(HorizontalSpinnerFixture::class.java, robot, spinner) {

  fun nextButton(): JButtonFixture =
    JButtonFixture(robot, robot.finder().find(Matchers.byName(JButton::class.java, "next")))

  fun prevButton(): JButtonFixture =
    JButtonFixture(robot, robot.finder().find(Matchers.byName(JButton::class.java, "prev")))

  fun value(): String? = spinner.model.getElementAt(spinner.selectedIndex)?.toString()

  companion object {
    @JvmStatic
    fun find(robot: Robot) =
      HorizontalSpinnerFixture(robot, robot.finder().find(Matchers.byType(HorizontalSpinner::class.java).andIsShowing()))

    @JvmStatic
    fun find(robot: Robot, root: JComponent) =
      HorizontalSpinnerFixture(robot, robot.finder().find(root, Matchers.byType(HorizontalSpinner::class.java).andIsShowing()))
  }
}