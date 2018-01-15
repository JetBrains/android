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
package com.android.tools.idea.tests.gui.framework.fixture.newProjectWizard;

import com.android.tools.idea.tests.gui.framework.fixture.wizard.AbstractWizardFixture;
import com.android.tools.idea.tests.gui.framework.fixture.wizard.AbstractWizardStepFixture;
import com.android.tools.idea.ui.ApiComboBoxItem;
import org.fest.swing.edt.GuiQuery;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;

public class ChooseOptionsForNewFileStepFixture<W extends AbstractWizardFixture>
  extends AbstractWizardStepFixture<ChooseOptionsForNewFileStepFixture, W> {

  protected ChooseOptionsForNewFileStepFixture(@NotNull W wizard, @NotNull JRootPane target) {
    super(ChooseOptionsForNewFileStepFixture.class, wizard, target);
  }

  @NotNull
  public ChooseOptionsForNewFileStepFixture<W> enterActivityName(@NotNull String name) {
    JTextField textField = robot().finder().findByLabel(target(), "Activity Name:", JTextField.class, true);
    replaceText(textField, name);
    return this;
  }

  @NotNull
  public String getInstantAppsHost() {
    final JTextField textField = robot().finder().findByLabel("Instant App URL Host:", JTextField.class, true);
    return GuiQuery.getNonNull(textField::getText);
  }

  @NotNull
  public String getInstantAppsRouteType() {
    final JComboBox comboBox = robot().finder().findByLabel("Instant App URL Route Type", JComboBox.class, true);
    return ((ApiComboBoxItem)GuiQuery.getNonNull(comboBox::getSelectedItem)).getLabel();
  }

  @NotNull
  public String getInstantAppsRoute() {
    final JTextField textField = robot().finder().findByLabel("Instant App URL Route:", JTextField.class, true);
    return GuiQuery.getNonNull(textField::getText);
  }
}
