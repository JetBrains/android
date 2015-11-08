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
import com.android.tools.idea.sdk.SdkState;
import com.android.tools.idea.sdk.remote.UpdatablePkgInfo;
import com.android.tools.idea.wizard.dynamic.DialogWrapperHost;
import com.android.tools.idea.wizard.dynamic.DynamicWizard;
import com.android.tools.idea.wizard.dynamic.DynamicWizardPath;
import com.android.tools.idea.wizard.dynamic.ScopedStateStore;
import com.google.common.collect.Sets;
import com.intellij.icons.AllIcons;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ApplicationNamesInfo;
import com.intellij.openapi.application.ex.ApplicationManagerEx;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.SystemInfo;
import com.intellij.util.ui.UIUtil;
import org.jetbrains.android.actions.RunAndroidSdkManagerAction;
import org.jetbrains.android.sdk.AndroidSdkData;
import org.jetbrains.android.sdk.AndroidSdkUtils;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.util.List;
import java.util.Set;

import static com.android.tools.idea.wizard.WizardConstants.INSTALL_REQUESTS_KEY;
import static com.android.tools.idea.wizard.WizardConstants.SKIPPED_INSTALL_REQUESTS_KEY;

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
  private boolean myIsExiting = false;

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
    addPath(new SdkQuickfixPath(getDisposable()));

    Set<IPkgDesc> problems = findProblemPackages();
    int selectedOption = 1; // Install all, default when there are no problems.

    if (!problems.isEmpty()) {
      StringBuilder warningBuilder = new StringBuilder("Due to your system configuration and the packages to be installed, \n" +
                                                       "it is likely that the following packages cannot be successfully installed while ");
      warningBuilder.append(ApplicationNamesInfo.getInstance().getFullProductName());
      warningBuilder.append(" is running. \n\nPlease exit and install the following packages using the standalone SDK manager:");
      for (IPkgDesc problemPkg : problems) {
        warningBuilder.append("\n    -");
        warningBuilder.append(problemPkg.getListDescription());
      }
      if (problems.size() == myRequestedPackages.size()) {
        selectedOption = Messages.showDialog(getProject(), warningBuilder.toString(), "Warning", new String[]{
          String.format("Exit %s and launch SDK Manager", ApplicationNamesInfo.getInstance().getProductName()),
          "Attempt to install packages"}, 0, AllIcons.General.Warning);
      }
      else {
        String[] options = new String[] {
          String.format("Exit %s and launch SDK Manager", ApplicationNamesInfo.getInstance().getProductName()),
          "Attempt to install all packages",
          "Install safe packages"
        };

        selectedOption = Messages.showDialog(
          getProject(), warningBuilder.toString(), "Warning",
          options,
          2, AllIcons.General.Warning);
      }
    }
    if (selectedOption == 0) {
      startSdkManagerAndExit();
      myIsExiting = true;
      return;
    }

    for (IPkgDesc desc : myRequestedPackages) {
      if (selectedOption == 2 && problems.contains(desc)) {
        state.listPush(SKIPPED_INSTALL_REQUESTS_KEY, desc);
      }
      else {
        state.listPush(INSTALL_REQUESTS_KEY, desc);
      }
    }
    super.init();
  }

  @Override
  public boolean showAndGet() {
    if (myIsExiting) {
      Disposer.dispose(myHost.getDisposable());
      return false;
    }
    return super.showAndGet();
  }

  @Override
  public void show() {
    if (myIsExiting) {
      Disposer.dispose(myHost.getDisposable());
      return;
    }
    super.show();
  }

  private void startSdkManagerAndExit() {
    // We know that the SDK exists since we already used it to look up the fact that a package is an upgrade.
    //noinspection ConstantConditions
    RunAndroidSdkManagerAction.runSpecificSdkManagerSynchronously(getProject(), AndroidSdkUtils.tryToChooseAndroidSdk().getLocation());
    ApplicationManager.getApplication().invokeLater(new Runnable() {
      @Override
      public void run() {
        ApplicationManagerEx.getApplicationEx().exit(true, true);
      }
    });
  }

  /**
   * Find packages that might not be able to be installed while studio is running.
   * Currently this means packages that are upgrades on windows systems, since windows locks files that are in use.
   * @return
   */
  private Set<IPkgDesc> findProblemPackages() {
    Set<IPkgDesc> result = Sets.newHashSet();
    if (!SystemInfo.isWindows) {
      return result;
    }
    SdkState state = SdkState.getInstance(AndroidSdkUtils.tryToChooseAndroidSdk());
    state.loadSynchronously(SdkState.DEFAULT_EXPIRATION_PERIOD_MS, false, null, null, null, false);
    Set<String> available = Sets.newHashSet();
    for (UpdatablePkgInfo update : state.getPackages().getUpdatedPkgs()) {
      if (update.hasRemote(false)) {
        available.add(update.getRemote(false).getPkgDesc().getInstallId());
      }
      if (update.hasPreview()) {
        available.add(update.getRemote(true).getPkgDesc().getInstallId());
      }
    }
    for (IPkgDesc request : myRequestedPackages) {
      if (available.contains(request.getInstallId())) {
        // This is an update
        result.add(request);
      }
    }
    return result;
  }

  @Override
  public void performFinishingActions() {
    List<IPkgDesc> skipped = myState.get(SKIPPED_INSTALL_REQUESTS_KEY);
    if (skipped != null && !skipped.isEmpty()) {
      final StringBuilder warningBuilder = new StringBuilder("The following packages were not installed.\n\n Would you like to exit ");
      warningBuilder.append(ApplicationNamesInfo.getInstance().getFullProductName());
      warningBuilder.append(" and install the following packages using the standalone SDK manager?");
      for (IPkgDesc problemPkg : skipped) {
        warningBuilder.append("\n");
        warningBuilder.append(problemPkg.getListDescription());
      }
      UIUtil.invokeLaterIfNeeded(new Runnable() {
        @Override
        public void run() {
          String restartOption = String.format("Exit %s and launch SDK Manager", ApplicationNamesInfo.getInstance().getProductName());
          int result = Messages
            .showDialog(getProject(), warningBuilder.toString(), "Warning", new String[]{restartOption, "Skip installation"}, 0, AllIcons.General.Warning);
          if (result == 0) {
            startSdkManagerAndExit();
          }
        }
      });
    }
    // We've already installed things, so clearly there's an SDK.
    AndroidSdkData data = AndroidSdkUtils.tryToChooseAndroidSdk();
    SdkState.getInstance(data).loadAsync(SdkState.DEFAULT_EXPIRATION_PERIOD_MS, false, null, null, null, true);
  }

  @NotNull
  @Override
  protected String getProgressTitle() {
    return "Finishing install...";
  }

  @Override
  protected String getWizardActionDescription() {
    return "Provides a method for handling quickfix SDK installation actions";
  }

  private static class SdkQuickfixPath extends DynamicWizardPath {
    private Disposable myDisposable;
    private LicenseAgreementStep myLicenseAgreementStep;

    public SdkQuickfixPath(Disposable disposable) {
      myDisposable = disposable;
    }

    @Override
    protected void init() {
      myLicenseAgreementStep = new LicenseAgreementStep(myDisposable);
      addStep(myLicenseAgreementStep);
      addStep(new SmwOldApiDirectInstall(myDisposable));
    }

    @NotNull
    @Override
    public String getPathName() {
      return "SDK Installation Quickfix";
    }

    @Override
    public boolean performFinishingActions() {
      myLicenseAgreementStep.performFinishingActions();
      return true;
    }
  }
}
