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
package com.android.tools.idea.sdk.wizard;

import com.android.sdklib.repository.descriptors.PkgDesc;
import com.android.tools.idea.wizard.DynamicWizard;
import com.android.tools.idea.wizard.DynamicWizardPath;
import com.android.tools.idea.wizard.WizardConstants;
import com.intellij.openapi.Disposable;
import org.jetbrains.annotations.NotNull;

/**
 * A path that can be added to a {@link DynamicWizard} to install required SDK components.
 * To request an installation, push a {@link PkgDesc} onto the list-key WizardConstants.INSTALL_REQUESTS_KEY
 */
public class SdkComponentInstallPath extends DynamicWizardPath {

  @NotNull private final Disposable myParentDisposable;

  public SdkComponentInstallPath(@NotNull Disposable parentDisposable) {
    myParentDisposable = parentDisposable;
  }

  @Override
  protected void init() {
    addStep(new LicenseAgreementStep(myParentDisposable));
    addStep(new SmwOldApiDirectInstall(myParentDisposable));
  }

  @Override
  public boolean isPathVisible() {
    return getState().listSize(WizardConstants.INSTALL_REQUESTS_KEY) > 0;
  }

  @NotNull
  @Override
  public String getPathName() {
    return "SDK Component Installation";
  }

  @Override
  public boolean performFinishingActions() {
    return true;
  }
}
