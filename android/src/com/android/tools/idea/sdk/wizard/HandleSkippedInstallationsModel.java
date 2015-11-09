/*
 * Copyright (C) 2015 The Android Open Source Project
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
import com.android.sdklib.repository.descriptors.IPkgDesc;
import com.android.tools.idea.ui.properties.core.BoolProperty;
import com.android.tools.idea.ui.properties.core.BoolValueProperty;
import com.android.tools.idea.wizard.model.WizardModel;
import com.intellij.openapi.project.Project;
import com.intellij.util.ui.UIUtil;
import org.jetbrains.android.sdk.AndroidSdkData;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;
/**
 * A {@link WizardModel} which based on the user's input exits the current application and installs packages that were
 * skipped over by the normal installation process.
 */
public final class HandleSkippedInstallationsModel extends WizardModel {
  private final Project myProject;
  private List<IPkgDesc> mySkippedInstallRequests;
  private AndroidSdkData mySdkData;
  private BoolProperty myUseStandaloneSdkManager = new BoolValueProperty();

  public HandleSkippedInstallationsModel(@Nullable Project project, @NotNull List<IPkgDesc> skippedInstallRequests, AndroidSdkData data) {
    myProject = project;
    mySkippedInstallRequests = skippedInstallRequests;
    mySdkData = data;
  }

  /**
   * Some users (Windows users in particular) may have trouble with the SDK Manager provided by Studio,
   * so we offer them an option to fallback to our old, standalone SDK manager, which runs outside of Android Studio.
   */
  public BoolProperty useStandaloneSdkManager() {
    return myUseStandaloneSdkManager;
  }

  public List<IPkgDesc> getSkippedInstallRequests() {
    return mySkippedInstallRequests;
  }

  @Override
  protected void handleFinished() {
    if (!myUseStandaloneSdkManager.get()) {
      // User went through the wizard but didn't choose to install any of the packages skipped during installation.
      return;
    }

    UIUtil.invokeLaterIfNeeded(new Runnable() {
      @Override
      public void run() {
        SdkQuickfixUtils.startSdkManagerAndExit(myProject, mySdkData);
      }
    });
  }
}
