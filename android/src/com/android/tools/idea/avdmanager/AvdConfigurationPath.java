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
package com.android.tools.idea.avdmanager;

import com.android.tools.idea.wizard.dynamic.DynamicWizardPath;
import com.intellij.openapi.Disposable;
import org.jetbrains.annotations.NotNull;

/**
 * Creates an Android Virtual Device
 */
public class AvdConfigurationPath extends DynamicWizardPath {
  private final Disposable myDisposable;

  public AvdConfigurationPath(Disposable disposable) {
    myDisposable = disposable;
  }

  @Override
  protected void init() {
    addStep(new ChooseDeviceDefinitionStep(myDisposable));
    addStep(new ChooseSystemImageStep(getProject(), myDisposable));
  }

  @Override
  public boolean isPathVisible() {
    return getVisibleStepCount() > 0;
  }

  @NotNull
  @Override
  public String getPathName() {
    return "AVD Configuration";
  }

  @Override
  public boolean performFinishingActions() {
    return false;
  }
}
