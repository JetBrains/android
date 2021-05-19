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

import com.android.tools.idea.tests.gui.framework.fixture.wizard.AbstractWizardFixture
import com.android.tools.idea.tests.gui.framework.fixture.wizard.AbstractWizardStepFixture
import com.android.tools.idea.wizard.template.Language
import com.intellij.openapi.ui.ComboBox
import org.fest.swing.fixture.JComboBoxFixture
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
    replaceText(findTextFieldWithLabel("Package name:"), name)
    return this
  }

  fun enterClassName(name: String): ConfigureLibraryStepFixture<W> {
    val textField = robot().finder().findByLabel(target(), "Class name:", JTextField::class.java, true)
    replaceText(textField, name)
    return this
  }

  fun setSourceLanguage(language: Language): ConfigureLibraryStepFixture<W> {
    val comboBox = robot().finder().findByType(target(), ComboBox::class.java)
    JComboBoxFixture(robot(), comboBox).selectItem(language.toString())
    return this
  }

  fun setUseKtsBuildFiles(select: Boolean): ConfigureLibraryStepFixture<W> {
    selectCheckBoxWithText("Use Kotlin script (.kts) for Gradle build files", select)
    return this
  }
}