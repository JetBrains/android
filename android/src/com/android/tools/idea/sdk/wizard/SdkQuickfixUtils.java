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

import com.android.annotations.NonNull;
import com.android.annotations.VisibleForTesting;
import com.android.repository.api.*;
import com.android.repository.util.InstallerUtil;
import com.android.sdklib.repository.AndroidSdkHandler;
import com.android.tools.idea.sdk.StudioDownloader;
import com.android.tools.idea.sdk.progress.StudioLoggerProgressIndicator;
import com.android.tools.idea.sdk.progress.StudioProgressRunner;
import com.android.tools.idea.sdk.StudioSettingsController;
import com.android.tools.idea.ui.wizard.StudioWizardDialogBuilder;
import com.android.tools.idea.wizard.model.ModelWizard;
import com.android.tools.idea.wizard.model.ModelWizardDialog;
import com.android.utils.HtmlBuilder;
import com.google.common.collect.Lists;
import com.google.common.collect.Sets;
import com.intellij.icons.AllIcons;
import com.intellij.ide.AppLifecycleListener;
import com.intellij.openapi.application.ApplicationNamesInfo;
import com.intellij.openapi.application.ex.ApplicationEx;
import com.intellij.openapi.application.ex.ApplicationManagerEx;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.openapi.ui.Messages;
import com.intellij.util.messages.MessageBusConnection;
import org.jetbrains.android.actions.RunAndroidSdkManagerAction;
import org.jetbrains.android.sdk.AndroidSdkData;
import org.jetbrains.android.sdk.AndroidSdkUtils;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.awt.*;
import java.io.File;
import java.util.*;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

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
  public static ModelWizardDialog createDialogForPaths(@Nullable Component parent,
                                                       @NotNull Collection<String> requestedPaths,
                                                       boolean backgroundable) {
    return createDialog(null, parent, requestedPaths, null, getSdkHandler(), backgroundable);
  }

  /**
   * Create an SdkQuickFix dialog.
   *
   * @param parent The component to use as a parent for the wizard dialog.
   * @param requestedPackages The packages to install. Callers should ensure that the given packages include remote versions.
   * @param backgroundable Whether the dialog should show a "background" button on the progress step.
   */
  @Nullable
  public static ModelWizardDialog createDialogForPackages(@Nullable Component parent,
                                                          @NotNull Collection<UpdatablePackage> requestedPackages, boolean backgroundable) {
    return createDialog(null, parent, null, requestedPackages, getSdkHandler(), backgroundable);
  }

  /**
   * Create an SdkQuickFix dialog.
   *
   * @param project The {@link Project} to use as a parent for the wizard dialog.
   * @param requestedPaths The paths of packages to install. Callers should ensure that the given packages include remote versions.
   */
  @Nullable
  public static ModelWizardDialog createDialogForPaths(@Nullable Project project, @NotNull Collection<String> requestedPaths) {
    return createDialog(project, null, requestedPaths, null, getSdkHandler(), false);
  }

  private static AndroidSdkHandler getSdkHandler() {
    AndroidSdkData data = AndroidSdkUtils.tryToChooseAndroidSdk();

    if (data == null) {
      String title = "SDK Problem";
      String msg = "<html>" + "Your Android SDK is missing or out of date." + "<br>" +
                   "You can configure your SDK via <b>Configure | Project Defaults | Project Structure | SDKs</b></html>";
      Messages.showErrorDialog(msg, title);

      return null;
    }

    return data.getSdkHandler();
  }

  @VisibleForTesting
  @Nullable
  static ModelWizardDialog createDialog(@Nullable Project project,
                                        @Nullable Component parent,
                                        @Nullable Collection<String> requestedPaths,
                                        @Nullable Collection<UpdatablePackage> requestedPackages,
                                        @Nullable AndroidSdkHandler sdkHandler,
                                        boolean backgroundable) {
    if (sdkHandler == null) {
      return null;
    }

    RepoManager mgr = sdkHandler.getSdkManager(REPO_LOGGER);

    if (mgr.getLocalPath() == null) {
      String title = "SDK Problem";
      String msg = "<html>" + "Your Android SDK is missing or out of date." + "<br>" +
                   "You can configure your SDK via <b>Configure | Project Defaults | Project Structure | SDKs</b></html>";
      Messages.showErrorDialog(msg, title);

      return null;
    }

    List<String> unknownPaths = Lists.newArrayList();
    List<UpdatablePackage> resolvedPackages = Lists.newArrayList();
    String error = resolve(requestedPaths, requestedPackages, mgr, resolvedPackages, unknownPaths);

    if (error != null) {
      Messages.showErrorDialog(error, "Error Resolving Packages");
      return null;
    }

    List<UpdatablePackage> unavailableDownloads = Lists.newArrayList();
    verifyAvailability(resolvedPackages, unavailableDownloads);

    // If we didn't find anything, show an error.
    if (!unknownPaths.isEmpty() || !unavailableDownloads.isEmpty()) {
      String title = "Packages Unavailable";
      HtmlBuilder builder = new HtmlBuilder();
      builder.openHtmlBody().add(String.format("%1$s packages are not available for download!", resolvedPackages.isEmpty() ? "All" : "Some"))
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
      if (resolvedPackages.isEmpty()) {
        return null;
      }
    }
    List<RemotePackage> installRequests = Lists.newArrayList();
    for (UpdatablePackage p : resolvedPackages) {
      installRequests.add(p.getRemote());
    }
    ModelWizard.Builder wizardBuilder = new ModelWizard.Builder();
    wizardBuilder.addStep(new LicenseAgreementStep(new LicenseAgreementModel(mgr.getLocalPath()), installRequests));
    InstallSelectedPackagesStep installStep = new InstallSelectedPackagesStep(installRequests, mgr, sdkHandler, backgroundable);
    wizardBuilder.addStep(installStep);
    ModelWizard wizard = wizardBuilder.build();

    String title = "SDK Quickfix Installation";

    return new StudioWizardDialogBuilder(wizard, title, parent).setProject(project).setModalityType(DialogWrapper.IdeModalityType.IDE)
      .build();
  }

  /**
   * Verifies that the given {@link UpdatablePackage}s contain a remote version that can be installed.
   *
   * @param requestedPackages    The {@link UpdatablePackage}s to check. Any packages that do not include an update will be removed from
   *                             this list.
   * @param unavailableDownloads Will be populated with any {@link UpdatablePackage}s in {@code requestedPackages} that do not contain an
   *                             update.
   */
  private static void verifyAvailability(List<UpdatablePackage> requestedPackages, List<UpdatablePackage> unavailableDownloads) {
    for (Iterator<UpdatablePackage> iter = requestedPackages.listIterator(); iter.hasNext(); ) {
      UpdatablePackage p = iter.next();
      if (!p.hasRemote()) {
        iter.remove();
        unavailableDownloads.add(p);
      }
    }
  }

  private enum InstallSdkOption {
    EXIT_AND_LAUNCH_STANDALONE(String.format("Exit %s and launch SDK Manager", ApplicationNamesInfo.getInstance().getProductName())),
    ATTEMPT_ALL("Attempt to install all packages"),
    INSTALL_SAFE("Install safe packages"),
    CANCEL("Cancel");

    private String myDescription;

    InstallSdkOption(@NotNull String description) {
      myDescription = description;
    }

    public String getDescription() {
      return myDescription;
    }
  }

  /**
   * Load the SDK if needed and look up packages corresponding to the given paths. Also finds and adds dependencies based on the given
   * paths and packages.
   *
   * @param requestedPaths Requested packages, by path.
   * @param requestedPackages Requested packages.
   * @param mgr A RepoManager to use to get the available packages.
   * @param result Will be populated with the resolved packages, including dependencies.
   * @param notFound Will be populated with any paths for which a corresponding package was not found.
   *
   * @return {@code null} on success, or an error message if there was a problem.
   */
  // TODO: Once welcome wizard is rewritten using ModelWizard this should be refactored as needed.
  public static String resolve(@Nullable Collection<String> requestedPaths,
                               @Nullable Collection<UpdatablePackage> requestedPackages,
                               @NonNull RepoManager mgr,
                               @NonNull List<UpdatablePackage> result,
                               @NonNull List<String> notFound) {
    mgr.load(RepoManager.DEFAULT_EXPIRATION_PERIOD_MS, null, null, null,
             new StudioProgressRunner(true, false, true, "Loading Remote Packages...", false, null),
             new StudioDownloader(), StudioSettingsController.getInstance(), true);
    Map<String, UpdatablePackage> packages = mgr.getPackages().getConsolidatedPkgs();
    if (requestedPackages == null) {
      requestedPackages = Lists.newArrayList();
    }
    List<UpdatablePackage> resolved = Lists.newArrayList(requestedPackages);
    if (requestedPaths != null) {
      for (String path : requestedPaths) {
        UpdatablePackage p = packages.get(path);
        if (p == null || !p.hasRemote()) {
          notFound.add(path);
        }
        else {
          resolved.add(p);
        }
      }
    }

    List<RemotePackage> remotes = Lists.newArrayList();
    for (UpdatablePackage p : resolved) {
      if (p.hasRemote()) {
        remotes.add(p.getRemote());
      }
    }
    final AtomicReference<String> warning = new AtomicReference<String>();
    ProgressIndicator errorCollector = new ProgressIndicatorAdapter() {
      @Override
      public void logWarning(@NonNull String s) {
        warning.set(s);
      }
    };
    List<RemotePackage> withDependencies =
      InstallerUtil.computeRequiredPackages(remotes, mgr.getPackages(), errorCollector);
    if (withDependencies == null) {
      // there was a problem computing dependencies
      return warning.get();
    }
    for (RemotePackage remote : withDependencies) {
      result.add(packages.get(remote.getPath()));
    }
    return null;
  }

  /**
   * Find packages that might not be able to be installed while studio is running, and separate them out from the
   * packages that are safe to go ahead with (non-problems).
   * Currently this means packages that are upgrades on windows systems, since windows locks files that are in use.
   */
  private static void findProblemPackages(List<UpdatablePackage> installRequests,
                                          AndroidSdkHandler handler,
                                          Set<RemotePackage> problems,
                                          List<RemotePackage> nonProblems) {
    boolean isWindows = handler.getFileOp().isWindows();

    for (UpdatablePackage p : installRequests) {
      // At this point we know everything has a remote
      RemotePackage remote = p.getRemote();
      if (isWindows && p.isUpdate()) {
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
  public static void startSdkManagerAndExit(@Nullable final Project project, @NotNull final File sdkPath) {
    final ApplicationEx app = ApplicationManagerEx.getApplicationEx();
    MessageBusConnection connection = app.getMessageBus().connect(app);
    connection.subscribe(AppLifecycleListener.TOPIC, new AppLifecycleListener.Adapter() {
      @Override
      public void appClosing() {
        RunAndroidSdkManagerAction.runSpecificSdkManagerSynchronously(project, sdkPath);
      }
    });

    app.exit(true, true);
  }
}
