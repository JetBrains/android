/*
 * Copyright (C) 2014 The Android Open Source Project
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
import com.android.tools.idea.tests.gui.framework.matcher.Matchers
import org.fest.swing.core.MouseButton
import org.fest.swing.fixture.JCheckBoxFixture
import org.fest.swing.fixture.JComboBoxFixture
import org.fest.swing.fixture.JTextComponentFixture
import javax.swing.JCheckBox
import javax.swing.JComboBox
import javax.swing.JRootPane

class ConfigureBasicActivityStepFixture<W : AbstractWizardFixture<*>>(
  wizard: W, target: JRootPane
) : AbstractWizardStepFixture<ConfigureBasicActivityStepFixture<*>, W>(ConfigureBasicActivityStepFixture::class.java, wizard, target) {
  /**
   * This is the list of labels used to find the right text input field.
   */
  enum class ActivityTextField(val labelText: String) {
    NAME("Activity Name"), LAYOUT("Layout Name"), TITLE("Title"), PACKAGE_NAME("Package name");
  }

  fun selectLauncherActivity(): ConfigureBasicActivityStepFixture<W> {
    val checkBox = robot().finder().find(target(), Matchers.byText(JCheckBox::class.java, "Launcher Activity"))
    JCheckBoxFixture(robot(), checkBox).select()
    return this
  }

  fun enterTextFieldValue(activityField: ActivityTextField, text: String): ConfigureBasicActivityStepFixture<W> {
    val textField = findTextFieldWithLabel(activityField.labelText)
    robot().click(textField, MouseButton.LEFT_BUTTON, 3) // select all; particularly for pseudo-JTextComponent EditorComponentImpl
    JTextComponentFixture(robot(), textField).enterText(text)
    return this
  }

  fun getTextFieldValue(activityField: ActivityTextField): String = findTextFieldWithLabel(activityField.labelText).text

  fun setSourceLanguage(sourceLanguage: String): ConfigureBasicActivityStepFixture<W> {
    JComboBoxFixture(robot(), robot().finder().findByLabel(target(), "Source Language", JComboBox::class.java, true))
      .selectItem(sourceLanguage)
    return this
  }

  fun setTargetSourceSet(targetSource: String): ConfigureBasicActivityStepFixture<W> {
    JComboBoxFixture(robot(), robot().finder().findByLabel(target(), "Target Source Set", JComboBox::class.java, true))
      .selectItem(targetSource)
    return this
  }
}