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

import com.android.tools.idea.wizard.*;
import icons.AndroidIcons;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;

/**
 * Wizard to setup Android Studio before the first run
 */
public class FirstRunWizard extends DynamicWizard {

  public static final String WIZARD_TITLE = "Android Studio Setup";

  public FirstRunWizard(DynamicWizardHost host) {
    super(null, null, WIZARD_TITLE, host);
    setTitle(WIZARD_TITLE);
  }

  @Override
  public void init() {
    // TODO Wizard logic goes here
    super.init();
  }

  @Override
  public void performFinishingActions() {

  }

  @Override
  protected String getWizardActionDescription() {
    return "Android Studio Setup";
  }
}
