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
package com.android.tools.idea.tests.gui.framework.fixture.designer

import com.android.tools.idea.tests.gui.framework.fixture.ComponentFixture
import com.android.tools.idea.tests.gui.framework.fixture.adtui.HorizontalSpinnerFixture
import com.android.tools.idea.tests.gui.framework.matcher.Matchers
import org.fest.swing.core.Robot
import org.fest.swing.fixture.JComboBoxFixture
import org.fest.swing.fixture.JSpinnerFixture

import javax.swing.*
import java.awt.*
import java.awt.event.KeyEvent

open class AssistantPanelFixture internal constructor(private val robot: Robot, private val panel: JComponent) {
  fun close() {
    robot.pressAndReleaseKey(KeyEvent.VK_ESCAPE)
  }
}

class RecyclerViewAssistantFixture internal constructor(robot: Robot, panel: JComponent) :
  AssistantPanelFixture(robot, panel){
  val spinner: HorizontalSpinnerFixture by lazy { HorizontalSpinnerFixture.find(robot, panel) }
  val countSpinner: JSpinnerFixture by lazy {
    JSpinnerFixture(robot, robot.finder().find(panel, Matchers.byType(JSpinner::class.java)))
  }
}

class TextViewAssistantFixture internal constructor(robot: Robot, panel: JComponent) {
  val combo = JComboBoxFixture(robot, robot.finder().find(panel, Matchers.byType(JComboBox::class.java)))
}

class ComponentAssistantFixture(private val robot: Robot,
                                private val assistantPanel: JComponent) : ComponentFixture<ComponentAssistantFixture, Component>(
  ComponentAssistantFixture::class.java, robot, assistantPanel) {
  fun getRecyclerViewAssistant() : RecyclerViewAssistantFixture = RecyclerViewAssistantFixture(robot, assistantPanel)
  fun getTextViewAssistant(): TextViewAssistantFixture = TextViewAssistantFixture(robot, assistantPanel)
}
