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
package com.android.tools.idea.tests.gui.framework.fixture.designer.naveditor

import com.android.tools.idea.naveditor.editor.CreateDestinationMenu
import com.android.tools.idea.tests.gui.framework.fixture.ComponentFixture
import com.intellij.openapi.ui.TextFieldWithBrowseButton
import org.fest.swing.core.Robot
import org.fest.swing.fixture.JButtonFixture
import org.fest.swing.fixture.JComboBoxFixture
import org.fest.swing.fixture.JTextComponentFixture
import javax.swing.JPanel
import javax.swing.JTextField

class CreateDestinationMenuFixture(private val robot: Robot, private val menu: CreateDestinationMenu) :
    ComponentFixture<CreateDestinationMenuFixture, JPanel>(CreateDestinationMenuFixture::class.java, robot, menu.mainPanel) {

  fun selectKind(tag: String): CreateDestinationMenuFixture = apply { JComboBoxFixture(robot, menu.myKindPopup).selectItem(tag) }

  fun setLabel(label: String) {
    JTextComponentFixture(robot(), menu.myLabelField).deleteText().enterText(label)
  }

  fun clickCreate() = JButtonFixture(robot, menu.myCreateButton).click()
}
