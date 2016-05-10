/*
 * Copyright (C) 2016 The Android Open Source Project
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
package com.android.tools.idea.npw.cpp;

import com.android.tools.idea.wizard.WizardConstants;
import com.android.tools.idea.wizard.dynamic.DynamicWizardPath;
import com.intellij.openapi.Disposable;
import org.jetbrains.annotations.NotNull;

/**
 * One-step path for additional configuration of native (C++) code in new project wizard
 */
public class ConfigureCppSupportPath extends DynamicWizardPath {
  private final Disposable myParentDisposable;

  public ConfigureCppSupportPath(@NotNull Disposable parentDisposable) {
    myParentDisposable = parentDisposable;
  }

  @Override
  protected void init() {
    addStep(new ConfigureCppSupportStep(myParentDisposable));
  }

  @Override
  public boolean isPathVisible() {
    return myState.getNotNull(WizardConstants.INCLUDE_CPP_SUPPORT_KEY, false);
  }

  @NotNull
  @Override
  public String getPathName() {
    return "Customize C++ Support";
  }

  @Override
  public boolean performFinishingActions() {
    return false;
  }
}
