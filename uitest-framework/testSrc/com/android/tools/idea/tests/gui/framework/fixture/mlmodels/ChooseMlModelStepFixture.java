/*
 * Copyright (C) 2020 The Android Open Source Project
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
package com.android.tools.idea.tests.gui.framework.fixture.mlmodels;

import com.android.tools.idea.tests.gui.framework.fixture.wizard.AbstractWizardStepFixture;
import com.intellij.openapi.ui.TextFieldWithBrowseButton;
import javax.swing.JRootPane;
import javax.swing.JTextField;
import org.fest.swing.fixture.JTextComponentFixture;
import org.jetbrains.annotations.NotNull;

public class ChooseMlModelStepFixture
  extends AbstractWizardStepFixture<ChooseMlModelStepFixture, ImportMlModelWizardFixture> {

  ChooseMlModelStepFixture(@NotNull ImportMlModelWizardFixture wizard, @NotNull JRootPane target) {
    super(ChooseMlModelStepFixture.class, wizard, target);
  }

  @NotNull
  public ChooseMlModelStepFixture enterModelPath(String modelPath) {
    TextFieldWithBrowseButton panel = robot().finder().findByType(target(), TextFieldWithBrowseButton.class);
    new JTextComponentFixture(robot(), robot().finder().findByType(panel, JTextField.class)).setText(modelPath);
    return this;
  }
}
