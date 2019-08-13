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

import com.android.tools.idea.common.model.NlComponent
import com.android.tools.idea.naveditor.structure.DestinationList
import com.android.tools.idea.tests.gui.framework.GuiTests
import com.android.tools.idea.tests.gui.framework.matcher.Matchers
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.util.Computable
import org.fest.swing.core.Robot
import org.fest.swing.fixture.JListFixture

class DestinationListFixture(robot: Robot, private val list: DestinationList) :
  JListFixture(robot, list.myList) {

  val selectedComponents: List<NlComponent>
    get() = runReadAction(Computable {
      list.myList.selectedValuesList
    })

  val components: List<NlComponent>
    get() = runReadAction(Computable {
      (0 until list.myList.model.size).map { list.myList.model.getElementAt(it) }
    })

  private fun <T> runReadAction(block: Computable<T>): T = ApplicationManager.getApplication().runReadAction(block)

  companion object {

    @JvmStatic
    fun create(robot: Robot): DestinationListFixture {
    val result = GuiTests.waitUntilShowing(robot, Matchers.byName(DestinationList::class.java, "DestinationList"))
      return DestinationListFixture(robot, result)
    }
  }
}
