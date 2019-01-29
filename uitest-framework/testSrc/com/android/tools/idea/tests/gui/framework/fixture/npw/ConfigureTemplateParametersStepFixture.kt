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

import com.android.tools.idea.tests.gui.framework.fixture.wizard.AbstractWizardStepFixture
import org.fest.swing.core.Robot
import org.fest.swing.fixture.AbstractJComponentFixture
import org.fest.swing.fixture.JComboBoxFixture
import org.fest.swing.fixture.JTextComponentFixture
import javax.swing.JComponent
import javax.swing.JRootPane

class ConfigureTemplateParametersStepFixture(wizard: ConfigureTemplateParametersWizardFixture, target: JRootPane)
  : AbstractWizardStepFixture<ConfigureTemplateParametersStepFixture, ConfigureTemplateParametersWizardFixture>(
  ConfigureTemplateParametersStepFixture::class.java, wizard, target) {

  inline fun <reified F : AbstractJComponentFixture<*, T, *>, reified T : JComponent> getFieldFixture(label: String): F {
    return F::class.java.getConstructor(Robot::class.java, T::class.java)
      .newInstance(robot(), robot().finder().findByLabel(target(), label, T::class.java))
  }

  fun getTextComponent(label: String): JTextComponentFixture = getFieldFixture(label)

  fun getComboBoxComponent(label: String): JComboBoxFixture = getFieldFixture(label)

  fun enterTextFieldValue(label: String, text: String): ConfigureTemplateParametersStepFixture {
    getTextComponent(label).selectAll().enterText(text)
    return this
  }

  fun selectComboBoxItem(label: String, item: String): ConfigureTemplateParametersStepFixture {
    getComboBoxComponent(label).selectItem(item)
    return this
  }
}