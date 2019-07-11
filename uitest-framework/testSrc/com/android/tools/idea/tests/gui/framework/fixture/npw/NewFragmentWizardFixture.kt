/*
 * Copyright (C) 2019 The Android Open Source Project
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
package com.android.tools.idea.tests.gui.framework.fixture.npw

import com.android.tools.adtui.ASGallery
import com.android.tools.idea.tests.gui.framework.GuiTests.findAndClickButton
import com.android.tools.idea.tests.gui.framework.GuiTests.waitUntilShowingAndEnabled
import com.android.tools.idea.tests.gui.framework.fixture.wizard.AbstractWizardFixture
import com.android.tools.idea.tests.gui.framework.fixture.wizard.findMenuDialog
import com.android.tools.idea.tests.gui.framework.matcher.Matchers.byType
import org.fest.swing.core.GenericTypeMatcher
import org.fest.swing.core.Robot
import org.fest.swing.finder.WindowFinder
import org.fest.swing.fixture.JListFixture
import javax.swing.JDialog

class NewFragmentWizardFixture constructor(robot: Robot, target: JDialog) : AbstractWizardFixture<NewFragmentWizardFixture>(
  NewFragmentWizardFixture::class.java, robot, target) {

  fun chooseFragment(fragment: String): NewFragmentWizardFixture {
    val listFixture = JListFixture(robot(),
                                   waitUntilShowingAndEnabled(robot(), target(), byType(ASGallery::class.java)))
    listFixture.replaceCellReader { jList, index -> jList.model.getElementAt(index).toString() }
    listFixture.clickItem(fragment)
    return this
  }

  fun clickNextFragment(): ConfigureTemplateParametersWizardFixture {
    findAndClickButton(this, "Next")
    val dialog = findMenuDialog(robot(), "New Android Fragment")
    return ConfigureTemplateParametersWizardFixture(robot(), dialog)
  }
}
