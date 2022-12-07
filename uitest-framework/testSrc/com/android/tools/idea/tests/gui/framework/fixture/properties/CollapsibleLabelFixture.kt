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

import com.android.tools.idea.tests.gui.framework.fixture.ActionButtonFixture
import com.android.tools.idea.tests.gui.framework.fixture.ComponentFixture
import com.android.tools.idea.tests.gui.framework.matcher.Matchers
import com.android.tools.idea.tests.gui.framework.waitForIdle
import com.android.tools.property.panel.impl.ui.CollapsibleLabelPanel
import com.google.common.collect.Lists
import com.intellij.openapi.actionSystem.impl.ActionButton
import org.fest.swing.core.Robot
import org.jetbrains.kotlin.idea.util.application.invokeLater
import javax.swing.JLabel

/**
 * Fixture for a [CollapsibleLabelPanel] commonly used as a title for a section in the properties panel.
 */
class CollapsibleLabelPanelFixture(
  robot: Robot,
  private val label: CollapsibleLabelPanel
) : ComponentFixture<CollapsibleLabelPanelFixture, CollapsibleLabelPanel>(
  CollapsibleLabelPanelFixture::class.java,
  robot,
  label
) {

  /**
   * The currently visible name of the Collapsible label.
   */
  val name: String
    get() = label.model.name

  fun expand() {
    if (label.model.expandable) {
      invokeLater {
        label.model.expanded = true
      }
      waitForIdle()
    }
  }

  fun collapse() {
    if (label.model.expandable) {
      invokeLater {
        label.model.expanded = false
      }
      waitForIdle()
    }
  }

  fun clickActionButton(buttonName: String) {
    val actionButtons: List<ActionButton> =
      Lists.newArrayList(
        robot().finder().findAll(target(), Matchers.byType(ActionButton::class.java))
      )
    if (actionButtons.isEmpty()) {
      throw AssertionError("Action Buttons not found !!!")
    }
    for (button in actionButtons) {
      if (button.action.toString().contains(buttonName, true)) {
        robot().click(button)
      }
    }
  }
}
