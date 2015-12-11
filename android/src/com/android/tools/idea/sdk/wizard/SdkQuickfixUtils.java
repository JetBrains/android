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
import com.android.tools.idea.sdk.SdkState;
import com.android.tools.idea.sdk.remote.UpdatablePkgInfo;
import com.android.tools.idea.ui.wizard.StudioWizardDialogBuilder;
import com.android.tools.idea.wizard.model.ModelWizard;
import com.android.tools.idea.wizard.model.ModelWizardDialog;
import com.google.common.collect.Sets;
import com.intellij.icons.AllIcons;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ApplicationNamesInfo;
import com.intellij.openapi.application.ex.ApplicationManagerEx;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.util.SystemInfo;
import org.jetbrains.android.actions.RunAndroidSdkManagerAction;
import org.jetbrains.android.sdk.AndroidSdkData;
import org.jetbrains.android.sdk.AndroidSdkUtils;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.awt.*;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

public final class SdkQuickfixUtils {

  /**
   * Create an SdkQuickFix dialog which is parented (and therefore scoped) by some existing
   * component.
   */
  @Nullable
  public static ModelWizardDialog createDialogWithParent(@NotNull Component parent, @NotNull List<IPkgDesc> requestedPackages) {
    return createDialog(null, parent, requestedPackages);
  }

  /**
   * Create an SdkQuickFix dialog which is optionally associated with the current project - whether
   * it is set or not determines what parent this dialog should be associated with. See
   * {@link DialogWrapper#DialogWrapper(Project, boolean)} for further details.
   */
  @Nullable
  public static ModelWizardDialog createDialog(@Nullable Project project, @NotNull List<IPkgDesc> requestedPackages) {
    return createDialog(project, null, requestedPackages);
  }

  @Nullable
  private static ModelWizardDialog createDialog(@Nullable Project project,
                                                @Nullable Component parent,
                                                @NotNull List<IPkgDesc> requestedPackages) {
    AndroidSdkData data = AndroidSdkUtils.tryToChooseAndroidSdk();

    if (data == null) {
      String title = "SDK Problem";
      String msg = "<html>" + "Your Android SDK is missing or out of date." + "<br>" +
                   "You can configure your SDK via <b>Configure | Project Defaults | Project Structure | SDKs</b></html>";
      Messages.showErrorDialog(msg, title);

      return null;
    }

    List<IPkgDesc> installRequests = new ArrayList<IPkgDesc>();
    List<IPkgDesc> skippedInstallRequests = new ArrayList<IPkgDesc>();

    if (!checkForErrors(project, requestedPackages, installRequests, skippedInstallRequests, data)) {
      startSdkManagerAndExit(project, data);
      assert false; // At this point we've exited Android Studio!
    }

    if (installRequests.isEmpty()) {
      return null;
    }

    ModelWizard.Builder wizardBuilder = new ModelWizard.Builder();
    wizardBuilder.addStep(new LicenseAgreementStep(new LicenseAgreementModel(data), installRequests));
    wizardBuilder.addStep(new InstallSelectedPackagesStep(installRequests, data));
    if (!skippedInstallRequests.isEmpty()) {
      HandleSkippedInstallationsModel handleSkippedInstallationsModel =
        new HandleSkippedInstallationsModel(project, skippedInstallRequests, data);
      wizardBuilder.addStep(new InstallMissingPackagesStep(handleSkippedInstallationsModel));
    }
    ModelWizard wizard = wizardBuilder.build();

    String title = "SDK Quickfix Installation";

    return new StudioWizardDialogBuilder(wizard, title, parent).setProject(project).setModalityType(DialogWrapper.IdeModalityType.IDE)
      .build();
  }

  private enum InstallSdkOption {
    EXIT_AND_LAUNCH_STANDALONE(String.format("Exit %s and launch SDK Manager", ApplicationNamesInfo.getInstance().getProductName())),
    ATTEMPT_ALL("Attempt to install all packages"),
    INSTALL_SAFE("Install safe packages");

    private String myDescription;

    InstallSdkOption(@NotNull String description) {
      myDescription = description;
    }

    public String getDescription() {
      return myDescription;
    }
  }

  /**
   * Returns false if we should exit the application to continue the installation in the standalone SDK manager.
   */
  private static boolean checkForErrors(@Nullable Project project,
                                        @NotNull List<IPkgDesc> requestedPackages,
                                        @NotNull List<IPkgDesc> installRequests,
                                        @NotNull List<IPkgDesc> skippedInstalls,
                                        AndroidSdkData data) {

    Set<IPkgDesc> problems = findProblemPackages(requestedPackages, data);
    InstallSdkOption selectedOption = InstallSdkOption.ATTEMPT_ALL;

    if (!problems.isEmpty()) {
      StringBuilder warningBuilder = new StringBuilder("Due to your system configuration and the packages to be installed, \n" +
                                                       "it is likely that the following packages cannot be successfully installed while ");
      warningBuilder.append(ApplicationNamesInfo.getInstance().getFullProductName());
      warningBuilder.append(" is running. \n\nPlease exit and install the following packages using the standalone SDK manager:");

      for (IPkgDesc problemPkg : problems) {
        warningBuilder.append("\n    -");
        warningBuilder.append(problemPkg.getListDescription());
      }

      String[] options;
      int defaultOptionIndex;

      if (problems.size() == requestedPackages.size()) {
        options =
          new String[]{InstallSdkOption.EXIT_AND_LAUNCH_STANDALONE.getDescription(), InstallSdkOption.ATTEMPT_ALL.getDescription(),};
        defaultOptionIndex = InstallSdkOption.EXIT_AND_LAUNCH_STANDALONE.ordinal();
      }
      else {
        options = new String[]{InstallSdkOption.EXIT_AND_LAUNCH_STANDALONE.getDescription(), InstallSdkOption.ATTEMPT_ALL.getDescription(),
          InstallSdkOption.INSTALL_SAFE.getDescription(),};
        defaultOptionIndex = InstallSdkOption.INSTALL_SAFE.ordinal();
      }

      int result =
        Messages.showDialog(project, warningBuilder.toString(), "Warning", options, defaultOptionIndex, AllIcons.General.Warning);
      selectedOption = InstallSdkOption.values()[result];
    }

    if (selectedOption == InstallSdkOption.EXIT_AND_LAUNCH_STANDALONE) {
      return false;
    }

    for (IPkgDesc desc : requestedPackages) {
      if (selectedOption == InstallSdkOption.INSTALL_SAFE && problems.contains(desc)) {
        skippedInstalls.add(desc);
      }
      else {
        installRequests.add(desc);
      }
    }
    return true;
  }

  /**
   * Find packages that might not be able to be installed while studio is running.
   * Currently this means packages that are upgrades on windows systems, since windows locks files that are in use.
   */
  private static Set<IPkgDesc> findProblemPackages(List<IPkgDesc> myInstallRequests, AndroidSdkData data) {
    Set<IPkgDesc> result = Sets.newHashSet();
    if (!SystemInfo.isWindows) {
      return result;
    }
    SdkState state = SdkState.getInstance(data);
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
    for (IPkgDesc request : myInstallRequests) {
      if (available.contains(request.getInstallId())) {
        // This is an update
        result.add(request);
      }
    }
    return result;
  }

  /**
   * Closes the current application and launches the standalone SDK manager
   */
  public static void startSdkManagerAndExit(@Nullable Project project, @NotNull AndroidSdkData data) {
    RunAndroidSdkManagerAction.runSpecificSdkManagerSynchronously(project, data.getLocation());
    ApplicationManager.getApplication().invokeLater(new Runnable() {
      @Override
      public void run() {
        ApplicationManagerEx.getApplicationEx().exit(true, true);
      }
    });
  }
}
