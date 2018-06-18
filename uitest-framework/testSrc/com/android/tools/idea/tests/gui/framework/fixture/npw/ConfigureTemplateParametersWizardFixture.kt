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
package com.android.tools.idea.tests.gui.framework.fixture.npw

import com.android.tools.idea.tests.gui.framework.fixture.wizard.AbstractWizardFixture
import org.fest.swing.core.Robot
import org.fest.swing.timing.Wait
import javax.swing.JDialog

class ConfigureTemplateParametersWizardFixture(robot: Robot, dialog: JDialog)
  : AbstractWizardFixture<ConfigureTemplateParametersWizardFixture>(ConfigureTemplateParametersWizardFixture::class.java, robot, dialog) {

  fun getConfigureTemplateParametersStep(): ConfigureTemplateParametersStepFixture {
    return ConfigureTemplateParametersStepFixture(this, target())

  }

  fun clickFinish() = super.clickFinish(Wait.seconds(5))
}