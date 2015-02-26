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

import com.android.sdklib.repository.descriptors.IPkgDesc;
import com.android.tools.idea.wizard.DialogWrapperHost;
import com.android.tools.idea.wizard.DynamicWizard;
import com.android.tools.idea.wizard.DynamicWizardPath;
import com.android.tools.idea.wizard.ScopedStateStore;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.util.List;

import static com.android.tools.idea.wizard.WizardConstants.INSTALL_REQUESTS_KEY;

/**
 * Provides a wizard which can install a list of items.
 * <p>
 * Example usage:
 * <pre>
 * {@code
  public static class LaunchMe extends AnAction {
      public LaunchMe() {
        super("Launch SDK Quickfix Wizard");
      }
      @Override
      public void actionPerformed(AnActionEvent e) {
        List<IPkgDesc> requestedPackages = Lists.newArrayListWithCapacity(3);
        FullRevision minBuildToolsRev = FullRevision.parseRevision(SdkConstants.MIN_BUILD_TOOLS_VERSION);
        requestedPackages.add(PkgDesc.Builder.newBuildTool(minBuildToolsRev).create());
        requestedPackages.add(PkgDesc.Builder.newPlatform(new AndroidVersion(19, null), new MajorRevision(1), minBuildToolsRev).create());
        SdkQuickfixWizard sdkQuickfixWizard = new SdkQuickfixWizard(null, null, requestedPackages);
        sdkQuickfixWizard.init();
        sdkQuickfixWizard.show();
      }
    }
 }
 * </pre>
 */
public class SdkQuickfixWizard extends DynamicWizard {
  private final List<IPkgDesc> myRequestedPackages;

  public SdkQuickfixWizard(@Nullable Project project, @Nullable Module module, List<IPkgDesc> requestedPackages) {
    this(project, module, requestedPackages, new DialogWrapperHost(project));
  }

  public SdkQuickfixWizard(@Nullable Project project, @Nullable Module module, List<IPkgDesc> requestedPackages, DialogWrapperHost host) {
    super(project, module, "SDK Quickfix Installation", host);
    myRequestedPackages = requestedPackages;
  }

  @Nullable
  @Override
  public Icon getIcon() {
    return null;
  }

  @Override
  public void init() {
    ScopedStateStore state = getState();
    for (IPkgDesc desc : myRequestedPackages) {
      state.listPush(INSTALL_REQUESTS_KEY, desc);
    }
    addPath(new SdkQuickfixPath(getDisposable()));
    super.init();
  }

  @Override
  public void performFinishingActions() {
    /* Pass, the installation actions are done in {@link SmwOldApiDirectInstall} */
  }

  @Override
  protected String getWizardActionDescription() {
    return "Provides a method for handling quickfix SDK installation actions";
  }

  private class SdkQuickfixPath extends DynamicWizardPath {
    private Disposable myDisposable;

    public SdkQuickfixPath(Disposable disposable) {
      myDisposable = disposable;
    }

    @Override
    protected void init() {
      addStep(new LicenseAgreementStep(myDisposable));
      addStep(new SmwOldApiDirectInstall(myDisposable));
    }

    @NotNull
    @Override
    public String getPathName() {
      return "SDK Installation Quickfix";
    }

    @Override
    public boolean performFinishingActions() {
      return true;
    }
  }
}
