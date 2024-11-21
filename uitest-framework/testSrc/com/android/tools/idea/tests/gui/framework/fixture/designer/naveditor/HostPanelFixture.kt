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
package com.android.tools.idea.tests.gui.framework.fixture.designer.naveditor

import com.android.tools.idea.naveditor.structure.HostPanel
import com.android.tools.idea.tests.gui.framework.GuiTests
import com.android.tools.idea.tests.gui.framework.matcher.Matchers
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.util.Computable
import com.intellij.psi.SmartPsiElementPointer
import com.intellij.psi.xml.XmlTag
import com.intellij.ui.components.JBList
import org.fest.swing.core.Robot
import org.fest.swing.fixture.JListFixture

class HostPanelFixture(robot: Robot, private val list: HostPanel) :
  JListFixture(robot, list.list) {

  val selectedComponents: List<HostPanel.HostItem>
    get() = list.list.selectedValuesList

  val components: List<HostPanel.HostItem>
    get() = ApplicationManager.getApplication().runReadAction(Computable {
      (0 until list.list.model.size).map { list.list.model.getElementAt(it) }
    })

  fun waitForHostList(): HostPanelFixture {
    GuiTests.waitUntilShowing(robot(), list, Matchers.byType(JBList::class.java))
    return this
  }

  companion object {
    fun create(robot: Robot): HostPanelFixture {
      val result = GuiTests.waitUntilFound(robot, null, Matchers.byType(HostPanel::class.java), 5)
      return HostPanelFixture(robot, result)
    }
  }
}
