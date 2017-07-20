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

import com.android.tools.idea.naveditor.editor.AddMenuWrapper
import com.android.tools.idea.tests.gui.framework.GuiTests
import com.android.tools.idea.tests.gui.framework.fixture.ComponentFixture
import org.fest.swing.core.Robot
import org.fest.swing.core.matcher.JButtonMatcher
import org.fest.swing.fixture.JButtonFixture
import org.fest.swing.fixture.JComboBoxFixture
import org.fest.swing.fixture.JListFixture
import javax.swing.JPanel

class AddMenuFixture(private val robot: Robot, private val menu: AddMenuWrapper) :
    ComponentFixture<AddMenuFixture, JPanel>(AddMenuFixture::class.java, robot, menu.mainPanel) {

  fun clickNewDestination(): AddMenuFixture {
    JButtonFixture(robot, menu.myNewDestinationButton).click()
    GuiTests.waitUntilShowing(robot, JButtonMatcher.withText("Create"))
    return this
  }

  fun selectKind(tag: String): AddMenuFixture {
    JComboBoxFixture(robot, menu.myKindPopup).selectItem(tag)
    return this
  }

  fun clickCreate() {
    JButtonFixture(robot, menu.myCreateButton).click()
  }

  fun clickBack() {
    JButtonFixture(robot, menu.myBackButton).click()
  }

  fun selectDestination(qName: String) {
    val index = menu.myDestinations.indexOfFirst{ it.qualifiedName == qName }
    JListFixture(robot, menu.myDestinationsGallery).clickItem(index)
  }
}
