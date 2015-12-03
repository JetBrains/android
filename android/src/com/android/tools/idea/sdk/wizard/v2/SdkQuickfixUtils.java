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
package com.android.tools.idea.sdk.wizard.v2;

import com.android.annotations.NonNull;
import com.android.annotations.VisibleForTesting;
import com.android.repository.api.*;
import com.android.sdklib.repositoryv2.AndroidSdkHandler;
import com.android.tools.idea.sdkv2.StudioDownloader;
import com.android.tools.idea.sdkv2.StudioLoggerProgressIndicator;
import com.android.tools.idea.sdkv2.StudioProgressRunner;
import com.android.tools.idea.sdkv2.StudioSettingsController;
import com.android.tools.idea.ui.wizard.StudioWizardDialogBuilder;
import com.android.tools.idea.wizard.model.ModelWizard;
import com.android.tools.idea.wizard.model.ModelWizardDialog;
import com.android.utils.HtmlBuilder;
import com.google.common.collect.Lists;
import com.google.common.collect.Sets;
import com.intellij.icons.AllIcons;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ApplicationNamesInfo;
import com.intellij.openapi.application.ex.ApplicationManagerEx;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.openapi.ui.Messages;
import org.jetbrains.android.actions.RunAndroidSdkManagerAction;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.awt.*;
import java.io.File;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;

public final class SdkQuickfixUtils {
  private static final ProgressIndicator REPO_LOGGER = new StudioLoggerProgressIndicator(SdkQuickfixUtils.class);

  /**
   * Create an SdkQuickFix dialog.
   *
   * @param parent The component to use as a parent for the wizard dialog.
   * @param requestedPaths The package paths to install. See {@link RepoPackage#getPath()}. Callers should make reasonably sure that there
   *                       is a package with the given path available.
   */
  @Nullable
  public static ModelWizardDialog createDialogForPaths(@Nullable Component parent, @NotNull List<String> requestedPaths) {
    return createDialog(null, parent, requestedPaths, null, AndroidSdkHandler.getInstance());
  }

  /**
   * Create an SdkQuickFix dialog.
   *
   * @param parent The component to use as a parent for the wizard dialog.
   * @param requestedPackages The component to use as a parent for the wizard dialog. Callers should ensure that the given packages
   *                          include remote versions.
   */
  @Nullable
  public static ModelWizardDialog createDialog(@Nullable Component parent, @NotNull List<UpdatablePackage> requestedPackages) {
    return createDialog(null, parent, null, requestedPackages, AndroidSdkHandler.getInstance());
  }

  @VisibleForTesting
  @Nullable
  static ModelWizardDialog createDialog(@Nullable Project project,
                                        @Nullable Component parent,
                                        @Nullable List<String> requestedPaths,
                                        @Nullable List<UpdatablePackage> requestedPackages,
                                        @NonNull AndroidSdkHandler sdkHandler) {

    RepoManager mgr = sdkHandler.getSdkManager(REPO_LOGGER);

    if (mgr.getLocalPath() == null) {
      String title = "SDK Problem";
      String msg = "<html>" + "Your Android SDK is missing or out of date." + "<br>" +
                   "You can configure your SDK via <b>Configure | Project Defaults | Project Structure | SDKs</b></html>";
      Messages.showErrorDialog(msg, title);

      return null;
    }

    List<String> unknownPaths = Lists.newArrayList();
    if (requestedPackages == null) {
      requestedPackages = Lists.newArrayList();
      resolve(requestedPaths, mgr, requestedPackages, unknownPaths);
    }
    List<UpdatablePackage> unavailableDownloads = Lists.newArrayList();
    verifyAvailability(requestedPackages, unavailableDownloads);

    // If we didn't find anything, show an error.
    if (!unknownPaths.isEmpty() || !unavailableDownloads.isEmpty()) {
      String title = "Packages Unavailable";
      HtmlBuilder builder = new HtmlBuilder();
      builder.openHtmlBody().add(String.format("%1$s packages are not available for download!", requestedPackages.isEmpty() ? "All" : "Some"))
             .newline().newline().add("The following packages are not available:").beginList();
      for (UpdatablePackage p: unavailableDownloads) {
        builder.listItem().add(p.getRepresentative().getDisplayName());
      }
      for (String p : unknownPaths) {
        builder.listItem().add("Package id " + p);
      }
      builder.endList().closeHtmlBody();
      Messages.showErrorDialog(builder.getHtml(), title);
      // If everything was removed, don't continue.
      if (requestedPackages.isEmpty()) {
        return null;
      }
    }

    List<RemotePackage> installRequests = Lists.newArrayList();
    List<RemotePackage> skippedInstallRequests = Lists.newArrayList();

    if (!checkForProblems(project, parent, requestedPackages, installRequests, skippedInstallRequests, sdkHandler)) {
      startSdkManagerAndExit(project, mgr.getLocalPath());
      assert false; // At this point we've exited Android Studio!
    }

    if (installRequests.isEmpty()) {
      return null;
    }

    ModelWizard.Builder wizardBuilder = new ModelWizard.Builder();
    wizardBuilder.addStep(new LicenseAgreementStep(new LicenseAgreementModel(mgr.getLocalPath()), installRequests));
    wizardBuilder.addStep(new InstallSelectedPackagesStep(installRequests, mgr, sdkHandler));
    if (!skippedInstallRequests.isEmpty()) {
      HandleSkippedInstallationsModel handleSkippedInstallationsModel =
        new HandleSkippedInstallationsModel(project, skippedInstallRequests, mgr.getLocalPath());
      wizardBuilder.addStep(new InstallMissingPackagesStep(handleSkippedInstallationsModel));
    }
    ModelWizard wizard = wizardBuilder.build();

    String title = "SDK Quickfix Installation";

    return new StudioWizardDialogBuilder(wizard, title, parent).setProject(project).setModalityType(DialogWrapper.IdeModalityType.IDE)
      .build();
  }

  /**
   * Verifies that the given {@link UpdatablePackage}s contain a remove version that can be installed.
   *
   * @param requestedPackages    The {@link UpdatablePackage}s to check. Any packages that do not include an update will be removed from
   *                             this list.
   * @param unavailableDownloads Will be populated with any {@link UpdatablePackage}s in {@code requestedPackages} that do not contain an
   *                             update.
   */
  private static void verifyAvailability(List<UpdatablePackage> requestedPackages, List<UpdatablePackage> unavailableDownloads) {
    for (Iterator<UpdatablePackage> iter = requestedPackages.listIterator(); iter.hasNext(); ) {
      UpdatablePackage p = iter.next();
      // TODO: channels
      if (!p.hasRemote(true)) {
        iter.remove();
        unavailableDownloads.add(p);
      }
    }
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
   * @param project {@link Project} used to parent any dialog shown.
   * @param parent Parent component for any dialog shown.
   * @param requestedPackages The packages to check.
   * @param installRequests Will be populated with the {@link RemotePackage}s to install.
   * @param skippedInstalls Will be populated with any {@link RemotePackage}s that are skipped.
   * @param handler {@link AndroidSdkHandler} instance.
   *
   * @return false if we should exit the application to continue the installation in the standalone SDK manager.
   */
  private static boolean checkForProblems(@Nullable Project project,
                                          @Nullable Component parent,
                                          @NotNull List<UpdatablePackage> requestedPackages,
                                          @NotNull List<RemotePackage> installRequests,
                                          @NotNull List<RemotePackage> skippedInstalls,
                                          @NotNull AndroidSdkHandler handler) {

    Set<RemotePackage> problems = Sets.newHashSet();
    findProblemPackages(requestedPackages, handler, problems, installRequests);
    InstallSdkOption selectedOption = InstallSdkOption.ATTEMPT_ALL;

    if (!problems.isEmpty()) {
      StringBuilder warningBuilder = new StringBuilder("Due to your system configuration and the packages to be installed, \n" +
                                                       "it is likely that the following packages cannot be successfully installed while ");
      warningBuilder.append(ApplicationNamesInfo.getInstance().getFullProductName());
      warningBuilder.append(" is running. \n\nPlease exit and install the following packages using the standalone SDK manager:");

      for (RepoPackage problemPkg : problems) {
        warningBuilder.append("\n    -");
        warningBuilder.append(problemPkg.getDisplayName());
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

      int result;
      if (parent != null) {
        result = Messages.showDialog(parent, warningBuilder.toString(), "Warning", options, defaultOptionIndex, AllIcons.General.Warning);
      }
      else {
        result = Messages.showDialog(project, warningBuilder.toString(), "Warning", options, defaultOptionIndex, AllIcons.General.Warning);
      }
      selectedOption = InstallSdkOption.values()[result];
    }

    if (selectedOption == InstallSdkOption.EXIT_AND_LAUNCH_STANDALONE) {
      return false;
    }

    if (selectedOption == InstallSdkOption.INSTALL_SAFE) {
      skippedInstalls.addAll(problems);
    }
    else if (selectedOption == InstallSdkOption.ATTEMPT_ALL) {
      installRequests.addAll(problems);
    }

    return true;
  }

  /**
   * Load the SDK if needed and look up packages corresponding to the given paths.
   *
   * @param requestedPackages The package paths to look up.
   * @param mgr A RepoManager to use to get the available packages.
   * @param resolved Will be populated with the {@link UpdatablePackage}s corresponding to the given paths.
   * @param notFound Will be populated with any paths for which a corresponding package was not found.
   */
  private static void resolve(List<String> requestedPackages, RepoManager mgr, List<UpdatablePackage> resolved, List<String> notFound) {
    mgr.load(RepoManager.DEFAULT_EXPIRATION_PERIOD_MS, null, null, null,
             new StudioProgressRunner(true, false, true, "Loading Remote Packages...", false, null),
             StudioDownloader.getInstance(), StudioSettingsController.getInstance(), true);
    Map<String, UpdatablePackage> packages = mgr.getPackages().getConsolidatedPkgs();
    for (String path : requestedPackages) {
      UpdatablePackage p = packages.get(path);
      // TODO: channels
      if (p == null || !p.hasRemote(true)) {
        notFound.add(path);
      }
      else {
        resolved.add(p);
      }
    }
  }

  /**
   * Find packages that might not be able to be installed while studio is running.
   * Currently this means packages that are upgrades on windows systems, since windows locks files that are in use.
   */
  private static void findProblemPackages(List<UpdatablePackage> installRequests,
                                          AndroidSdkHandler handler,
                                          Set<RemotePackage> problems,
                                          List<RemotePackage> nonProblems) {
    boolean isWindows = handler.getFileOp().isWindows();

    for (UpdatablePackage p : installRequests) {
      // At this point we know everything has a remote
      // TODO: channels
      RemotePackage remote = p.getRemote(true);
      if (isWindows && p.isUpdate(true)) {
        problems.add(remote);
      }
      else {
        nonProblems.add(remote);
      }
    }
  }

  /**
   * Closes the current application and launches the standalone SDK manager
   */
  public static void startSdkManagerAndExit(@Nullable Project project, @NotNull File sdkPath) {
    RunAndroidSdkManagerAction.runSpecificSdkManagerSynchronously(project, sdkPath);
    ApplicationManager.getApplication().invokeLater(new Runnable() {
      @Override
      public void run() {
        ApplicationManagerEx.getApplicationEx().exit(true, true);
      }
    });
  }
}
