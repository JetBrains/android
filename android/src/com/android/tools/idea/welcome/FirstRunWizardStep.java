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
package com.android.tools.idea.welcome;

import com.android.tools.idea.wizard.DynamicWizardStep;
import com.android.tools.idea.wizard.WizardConstants;
import icons.AndroidIcons;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;

/**
 * Base class for the first run wizard steps. Ensures consistent look and
 * feel for the pages.
 */
public abstract class FirstRunWizardStep extends DynamicWizardStep {
  public static final String SETUP_WIZARD = "Setup Wizard";
  private final JPanel myPanel;
  @NotNull private final String myName;

  public FirstRunWizardStep(@Nullable String name) {
    myName = name == null ? SETUP_WIZARD : name;
    myPanel = new JPanel(new BorderLayout());
    String title = name != null ? SETUP_WIZARD + " - " + name : SETUP_WIZARD;
    JPanel header = createWizardStepHeader(WizardConstants.ANDROID_NPW_HEADER_COLOR,
                                           AndroidIcons.Wizards.NewProjectMascotGreen, title);
    myPanel.add(header, BorderLayout.NORTH);
  }

  @NotNull
  @Override
  public final String getStepName() {
    return myName;
  }

  @NotNull
  @Override
  public final JComponent getComponent() {
    return myPanel;
  }

  protected final void setComponent(@NotNull JComponent component) {
    int inset = WizardConstants.STUDIO_WIZARD_TOP_INSET * 2;
    component.setBorder(BorderFactory.createEmptyBorder(inset, inset, inset, inset));
    myPanel.add(component, BorderLayout.CENTER);
  }
}
