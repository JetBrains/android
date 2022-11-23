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
package com.android.tools.idea.welcome.wizard.deprecated;

import com.android.tools.idea.wizard.dynamic.DynamicWizardStep;
import com.android.tools.idea.wizard.WizardConstants;
import com.intellij.util.IJSwingUtilities;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;

/**
 * Base class for the first run wizard steps. Ensures consistent look and
 * feel for the pages.
 * @deprecated use {@link com.android.tools.idea.wizard.model.ModelWizardStep}
 */
public abstract class FirstRunWizardStep extends DynamicWizardStep {
  public static final String SETUP_WIZARD = "Setup Wizard";
  @NotNull private final String myName;
  @Nullable private final String myDescription;
  private JComponent myComponent;
  private boolean myComponentUpdated;

  public FirstRunWizardStep(@Nullable String name) {
    this(name == null ? SETUP_WIZARD : name, null);
  }

  public FirstRunWizardStep(@NotNull String name, @Nullable String description) {
    myName = name;
    myDescription = description;
  }

  @Override
  public void onEnterStep() {
    super.onEnterStep();
    if (!myComponentUpdated) {
      // Update the UI after a potential color theme change.
      // If this is missing and a user selects the darcula color theme, the colors
      // in all subsequent pages will be wrong (a mix of darcula and default).
      IJSwingUtilities.updateComponentTreeUI(myComponent);
      myComponentUpdated = true;
    }
  }

  @NotNull
  @Override
  public final String getStepName() {
    return myName;
  }

  @NotNull
  @Override
  protected String getStepTitle() {
    return myName;
  }

  @NotNull
  @Override
  protected Component createStepBody() {
    assert myComponent != null : "setComponent was not called when constructing the wizard step";
    return myComponent;
  }

  @Deprecated
  protected final void setComponent(@NotNull JComponent component) {
    int inset = WizardConstants.STUDIO_WIZARD_TOP_INSET * 2;
    component.setBorder(BorderFactory.createEmptyBorder(inset, inset, inset, inset));
    myComponent = component;
  }

  @Nullable
  @Override
  protected String getStepDescription() {
    return myDescription;
  }
}
