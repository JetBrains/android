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
package com.android.tools.idea.tests.gui.framework.fixture.properties

import com.android.tools.idea.layoutinspector.ui.SelectedViewPanel
import com.android.tools.idea.tests.gui.framework.fixture.ComponentFixture
import org.fest.swing.core.Robot
import javax.swing.Icon
import javax.swing.JLabel

/**
 * Fixture for a [SelectedViewPanel] which is the header component in the properties panel for a layout inspector.
 */
class SelectedViewPanelFixture(
  panel: SelectedViewPanel,
  robot: Robot
) : ComponentFixture<SelectedViewPanelFixture, SelectedViewPanel>(SelectedViewPanelFixture::class.java, robot, panel) {

  val id: String
    get() = (target().components[0] as JLabel).text

  val icon: Icon?
    get() = (target().components[0] as JLabel).icon

  val description: String
    get() = (target().components[1] as JLabel).text
}
