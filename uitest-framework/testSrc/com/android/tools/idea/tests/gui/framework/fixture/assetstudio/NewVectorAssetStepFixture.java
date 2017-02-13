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
package com.android.tools.idea.tests.gui.framework.fixture.assetstudio;

import com.android.tools.idea.tests.gui.framework.fixture.newProjectWizard.AbstractWizardStepFixture;
import com.android.tools.idea.tests.gui.framework.matcher.Matchers;
import com.android.tools.idea.ui.validation.ValidatorPanel;
import com.intellij.ui.components.JBLabel;
import org.fest.swing.core.Robot;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;

public class NewVectorAssetStepFixture extends AbstractWizardStepFixture<NewVectorAssetStepFixture> {
  protected NewVectorAssetStepFixture(@NotNull Robot robot,
                                      @NotNull JRootPane target) {
    super(NewVectorAssetStepFixture.class, robot, target);
  }

  public void setName(@NotNull String name) {
    replaceText(findTextFieldWithLabel("Name:"), name);
  }

  @NotNull
  public String getError() {
    ValidatorPanel validatorPanel = robot().finder().findByType(target(), ValidatorPanel.class, true);
    JBLabel label = robot().finder().findByName(validatorPanel, "ValidationLabel", JBLabel.class, false);
    String error = label.getText();
    return " ".equals(error) ? "" : error;
  }
}
