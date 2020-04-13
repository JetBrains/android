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

import com.android.tools.idea.tests.gui.framework.fixture.ComponentFixture
import com.android.tools.property.panel.impl.ui.ActionButtonBinding
import org.fest.swing.core.Robot
import org.fest.swing.core.Scrolling
import org.fest.swing.fixture.JLabelFixture
import org.fest.swing.fixture.JTextComponentFixture
import javax.swing.JComponent
import javax.swing.JLabel
import javax.swing.text.JTextComponent

/**
 * Fixture for an [ActionButtonBinding] which is a wrapper around a property editor.
 */
class ActionButtonBindingFixture(
  robot: Robot,
  control: ActionButtonBinding
) : ComponentFixture<ActionButtonBindingFixture, ActionButtonBinding>(
  ActionButtonBindingFixture::class.java,
  robot,
  control
) {

  val textField: JTextComponentFixture
    get() = JTextComponentFixture(robot(), find(JTextComponent::class.java, 0))

  val button: JLabelFixture
    get() = JLabelFixture(robot(), find(JLabel::class.java, 1))

  private fun <T : JComponent> find(componentClass: Class<T>, index: Int): T {
    val component = componentClass.cast(target().getComponent(index))
    Scrolling.scrollToVisible(robot(), component)
    return component
  }
}
