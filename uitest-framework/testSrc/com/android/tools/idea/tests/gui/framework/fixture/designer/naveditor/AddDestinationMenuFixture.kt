// Copyright (C) 2017 The Android Open Source Project
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
//      http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.
package com.android.tools.idea.tests.gui.framework.fixture.designer.naveditor

import com.android.tools.idea.naveditor.editor.AddDestinationMenu
import com.android.tools.idea.naveditor.editor.DESTINATION_MENU_MAIN_PANEL_NAME
import com.android.tools.idea.tests.gui.framework.fixture.ActionButtonFixture
import com.android.tools.idea.tests.gui.framework.fixture.ComponentFixture
import com.android.tools.idea.tests.gui.framework.fixture.npw.ConfigureTemplateParametersWizardFixture
import com.android.tools.idea.tests.gui.framework.fixture.npw.NewFragmentWizardFixture
import com.android.tools.idea.tests.gui.framework.fixture.wizard.findMenuDialog
import com.android.tools.idea.tests.gui.framework.matcher.Matchers
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.progress.EmptyProgressIndicator
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.util.Computable
import com.intellij.util.ui.AsyncProcessIcon
import org.fest.swing.core.Robot
import org.fest.swing.fixture.JListFixture
import org.fest.swing.timing.Wait
import javax.swing.JPanel
import kotlin.jvm.java

class AddDestinationMenuFixture private constructor(private val robot: Robot, private val menu: AddDestinationMenu, target: JPanel) :
  ComponentFixture<AddDestinationMenuFixture, JPanel>(
    AddDestinationMenuFixture::class.java,
    robot,
    target
  ) {

  fun selectDestination(label: String) {
    val index = ProgressManager.getInstance().runProcess(Computable {
      ApplicationManager.getApplication().runReadAction(Computable {
        (0 until menu.destinationsList.itemsCount).first { menu.destinationsList.model.getElementAt(it).label == label }
      })
    }, EmptyProgressIndicator())
    JListFixture(robot, menu.destinationsList).clickItem(index)
  }

  fun waitForContents(): AddDestinationMenuFixture {
    val processIcon = robot.finder().find(menu.destinationsList, Matchers.byType(AsyncProcessIcon::class.java))
    Wait.seconds(15).expecting("destination menu contents").until { !processIcon.isRunning }
    return this
  }

  fun visibleItemCount(): Int {
    return menu.destinationsList.itemsCount
  }

  fun clickCreateNewFragment(): NewFragmentWizardFixture {
    ActionButtonFixture(robot, menu.createNewDestinationButton).click()

    val dialog = findMenuDialog(robot, "New Android Fragment")
    return NewFragmentWizardFixture(robot, dialog)
  }

  fun isBalloonVisible() = menu.isBalloonVisible()

  companion object {
    private fun findMenuPanel(robot: Robot): JPanel? =
      robot.finder().findByName(DESTINATION_MENU_MAIN_PANEL_NAME, JPanel::class.java, true)

    @JvmStatic
    fun create(robot: Robot, menu: AddDestinationMenu): AddDestinationMenuFixture {
      var targetMenuPanel: JPanel? = null
      Wait.seconds(10)
        .expecting("Menu is populated")
        .until {
          targetMenuPanel = findMenuPanel(robot)
          targetMenuPanel != null
        }
      return AddDestinationMenuFixture(robot, menu, targetMenuPanel!!)
    }
  }
}
