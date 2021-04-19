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
package com.android.tools.idea.tests.gui.framework.fixture.npw;

import com.android.tools.idea.tests.gui.framework.fixture.wizard.AbstractWizardFixture;
import com.android.tools.idea.tests.gui.framework.fixture.wizard.AbstractWizardStepFixture;
import java.awt.Component;
import javax.swing.JRadioButton;
import javax.swing.JRootPane;
import org.fest.swing.core.ComponentMatcher;
import org.fest.swing.core.GenericTypeMatcher;
import org.fest.swing.fixture.JRadioButtonFixture;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class ChooseBenchmarkModuleTypeStepFixture<W extends AbstractWizardFixture>
  extends AbstractWizardStepFixture<ChooseBenchmarkModuleTypeStepFixture, W> {

  ChooseBenchmarkModuleTypeStepFixture(@NotNull W wizard, @NotNull JRootPane target) {
    super(ChooseBenchmarkModuleTypeStepFixture.class, wizard, target);
  }

  @NotNull
  public ConfigureMicrobenchmarkModuleStepFixture<W> clickNextToMicrobenchmarkModule() {
    findRadioButtonWithText("Microbenchmark").select();
    wizard().clickNext();
    return new ConfigureMicrobenchmarkModuleStepFixture<>(wizard(), target().getRootPane());
  }

  @NotNull
  public ConfigureMacrobenchmarkModuleStepFixture<W> clickNextToMacrobenchmarkModule() {
    findRadioButtonWithText("Macrobenchmark").select();
    wizard().clickNext();
    return new ConfigureMacrobenchmarkModuleStepFixture<>(wizard(), target().getRootPane());
  }

  @NotNull
  private JRadioButtonFixture findRadioButtonWithText(@NotNull String label) {
    JRadioButton radioButton = robot().finder().find(new GenericTypeMatcher<JRadioButton>(JRadioButton.class, true) {
      @Override
      protected boolean isMatching(@NotNull JRadioButton component) {
        return component.getText().equals(label);
      }
    });
    return new JRadioButtonFixture(robot(), radioButton);
  }
}
