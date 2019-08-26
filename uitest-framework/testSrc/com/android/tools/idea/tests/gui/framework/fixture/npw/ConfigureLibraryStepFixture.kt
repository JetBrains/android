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
package com.android.tools.idea.tests.gui.framework.fixture.npw

import com.android.tools.adtui.LabelWithEditButton
import com.android.tools.idea.tests.gui.framework.fixture.wizard.AbstractWizardFixture
import com.android.tools.idea.tests.gui.framework.fixture.wizard.AbstractWizardStepFixture
import org.fest.swing.fixture.JButtonFixture
import org.fest.swing.fixture.JComboBoxFixture
import javax.swing.JButton
import javax.swing.JComboBox
import javax.swing.JRootPane
import javax.swing.JTextField

class ConfigureLibraryStepFixture<W : AbstractWizardFixture<*>>(
  wizard: W, target: JRootPane
) : AbstractWizardStepFixture<ConfigureLibraryStepFixture<*>, W>(ConfigureLibraryStepFixture::class.java, wizard, target) {
  fun enterLibraryName(name: String): ConfigureLibraryStepFixture<W> {
    val textField = robot().finder().findByLabel(target(), "Library name:", JTextField::class.java, true)
    replaceText(textField, name)
    return this
  }

  fun enterPackageName(name: String): ConfigureLibraryStepFixture<W> {
    val editLabelContainer = robot().finder().findByType(target(), LabelWithEditButton::class.java)
    val editButton = JButtonFixture(robot(), robot().finder().findByType( editLabelContainer, JButton::class.java))
    editButton.click()
    replaceText(findTextFieldWithLabel("Package name:"), name)
    editButton.click() // click "Done"
    return this
  }

  fun enterClassName(name: String): ConfigureLibraryStepFixture<W> {
    val textField = robot().finder().findByLabel(target(), "Class name:", JTextField::class.java, true)
    replaceText(textField, name)
    return this
  }

  fun setSourceLanguage(sourceLanguage: String): ConfigureLibraryStepFixture<W> {
    JComboBoxFixture(robot(), robot().finder().findByLabel(target(), "Language:", JComboBox::class.java, true))
      .selectItem(sourceLanguage)
    return this
  }
}