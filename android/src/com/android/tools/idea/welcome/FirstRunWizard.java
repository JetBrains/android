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

import com.android.tools.idea.wizard.DynamicWizard;
import com.android.tools.idea.wizard.DynamicWizardHost;
import com.android.tools.idea.wizard.ScopedStateStore;
import com.android.tools.idea.wizard.SingleStepPath;

/**
 * Wizard to setup Android Studio before the first run
 */
public class FirstRunWizard extends DynamicWizard {
  public static final String WIZARD_TITLE = "Android Studio Setup";
  private static final ScopedStateStore.Key<Boolean> KEY_CUSTOM_INSTALL =
    ScopedStateStore.createKey("custom.install", ScopedStateStore.Scope.WIZARD, Boolean.class);

  public FirstRunWizard(DynamicWizardHost host) {
    super(null, null, WIZARD_TITLE, host);
    setTitle(WIZARD_TITLE);
  }

  @Override
  public void init() {
    addPath(new SingleStepPath(new FirstRunWelcomeStep()));
    addPath(new SetupJdkPath());
    addPath(new SingleStepPath(new InstallationTypeWizardStep(KEY_CUSTOM_INSTALL)));
    addPath(new SetupAndroidSdkPath(KEY_CUSTOM_INSTALL));
    addPath(new SetupEmulatorPath(KEY_CUSTOM_INSTALL));
    addPath(new DownloadComponentsPath(getDisposable()));

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
