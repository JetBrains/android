/*
 * Copyright (C) 2013 The Android Open Source Project
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


import com.android.annotations.Nullable;
import com.android.tools.idea.sdk.SdkState;
import com.android.tools.idea.wizard.TemplateParameterStep;
import com.android.tools.idea.wizard.TemplateWizard;
import com.intellij.ide.util.projectWizard.ModuleWizardStep;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Disposer;
import org.jetbrains.android.sdk.AndroidSdkData;
import org.jetbrains.android.sdk.AndroidSdkUtils;

public class SdkManagerWizard2 extends TemplateWizard implements TemplateParameterStep.UpdateListener {

  private SmwState myWizardState;
  private SmwSelectionStep mySelectionStep;
  private SmwConfirmationStep myConfirmationStep;
  private SmwProgressStep myProgressStep;

  public SdkManagerWizard2(@Nullable Project project) {
    super("fake SDK Manager wizard", project);
    init();
  }

  @Override
  protected void init() {
    // TODO it's possible to not have an SDK, in which case we need to either redirect
    // to JG's "setup an SDK" wizard or include it's first page here. So right now let's
    // be conservative and deal with the fact that SdkData or SdkState can be null.
    AndroidSdkData sdkData = AndroidSdkUtils.tryToChooseAndroidSdk();
    SdkState sdkState = sdkData == null ? null : SdkState.getInstance(sdkData);
    myWizardState = new SmwState(sdkState);

    // TODO even if we do have an SdkData structure, we should account for the path to
    // be invalid (folder missing or not an SDK) and include as first step JG's page
    // about setting up an SDK. TL;DR: this is temporary, need to adjust workflow.
    mySelectionStep = new SmwSelectionStep(myWizardState, this);
    myConfirmationStep = new SmwConfirmationStep(myWizardState, this);
    myProgressStep = new SmwProgressStep(myWizardState, this);

    addStep(new SmwOldApiDirectInstall(myWizardState, this)); // for "demo" purposes only
    addStep(mySelectionStep);
    addStep(myConfirmationStep);
    addStep(myProgressStep);

    for (ModuleWizardStep step : mySteps) {
      if (step instanceof Disposable) {
        Disposer.register(getDisposable(), (Disposable)step);
      }
    }

    super.init();
  }

  @Override
  protected void dispose() {
    super.dispose();

  }

  @Override
  @Nullable
  protected String getHelpID() {
    return null;
  }

}
