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

import static org.jetbrains.android.util.AndroidBundle.message;

import com.android.annotations.concurrency.Slow;
import com.android.repository.api.LocalPackage;
import com.android.repository.api.ProgressIndicator;
import com.android.repository.api.ProgressIndicatorAdapter;
import com.android.repository.api.RemotePackage;
import com.android.repository.api.RepoManager;
import com.android.repository.api.RepoPackage;
import com.android.repository.api.UpdatablePackage;
import com.android.repository.impl.meta.RepositoryPackages;
import com.android.repository.util.InstallerUtil;
import com.android.sdklib.repository.AndroidSdkHandler;
import com.android.tools.idea.progress.StudioLoggerProgressIndicator;
import com.android.tools.idea.progress.StudioProgressRunner;
import com.android.tools.idea.sdk.AndroidSdks;
import com.android.tools.idea.sdk.StudioDownloader;
import com.android.tools.idea.sdk.StudioSettingsController;
import com.android.tools.idea.wizard.model.ModelWizard;
import com.android.tools.idea.wizard.model.ModelWizardDialog;
import com.android.tools.idea.wizard.ui.StudioWizardDialogBuilder;
import com.android.tools.sdk.AndroidSdkData;
import com.android.utils.HtmlBuilder;
import com.google.common.annotations.VisibleForTesting;
import com.google.common.collect.Lists;
import com.intellij.CommonBundle;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.options.ShowSettingsUtil;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ProjectManager;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.openapi.ui.Messages;
import com.intellij.util.containers.ContainerUtil;
import java.awt.Component;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public final class SdkQuickfixUtils {
  private static final ProgressIndicator REPO_LOGGER = new StudioLoggerProgressIndicator(SdkQuickfixUtils.class);

  /**
   * Create an SdkQuickFix dialog.
   *
   * @param parent         The component to use as a parent for the wizard dialog.
   * @param requestedPaths The package paths to install. See {@link RepoPackage#getPath()}. Callers should make reasonably sure that there
   *                       is a package with the given path available.
   */
  @Nullable
  public static ModelWizardDialog createDialogForPaths(@Nullable Component parent,
                                                       @NotNull Collection<String> requestedPaths,
                                                       boolean backgroundable) {
    return createDialog(null, parent, requestedPaths, null, null, getSdkHandler(), null, backgroundable);
  }

  /**
   * Create an SdkQuickFix dialog.
   *
   * @param parent            The component to use as a parent for the wizard dialog.
   * @param requestedPackages The packages to install. Callers should ensure that the given packages include remote versions.
   * @param backgroundable    Whether the dialog should show a "background" button on the progress step.
   */
  @Nullable
  public static ModelWizardDialog createDialogForPackages(@Nullable Component parent,
                                                          @NotNull Collection<UpdatablePackage> requestedPackages,
                                                          @NotNull Collection<LocalPackage> uninstallPackages,
                                                          boolean backgroundable) {
    return createDialog(null, parent, null, requestedPackages, uninstallPackages, getSdkHandler(), null, backgroundable);
  }

  /**
   * Create an SdkQuickFix dialog.
   *
   * @param project        The {@link Project} to use as a parent for the wizard dialog.
   * @param requestedPaths The paths of packages to install. Callers should ensure that the given packages include remote versions.
   */
  @Nullable
  public static ModelWizardDialog createDialogForPaths(@Nullable Project project, @NotNull Collection<String> requestedPaths) {
    return createDialogForPaths(project, requestedPaths, null);
  }

  /**
   * Create an SdkQuickFix dialog.
   *
   * @param project        The {@link Project} to use as a parent for the wizard dialog.
   * @param requestedPaths The paths of packages to install. Callers should ensure that the given packages include remote versions.
   * @param noOpMessage    Error message to show when nothing is going to be installed or uninstalled after resolving the resolved paths or
   *                       null if there is no need to show an error.
   */
  @Nullable
  public static ModelWizardDialog createDialogForPaths(@Nullable Project project,
                                                       @NotNull Collection<String> requestedPaths,
                                                       @Nullable String noOpMessage) {
    return createDialog(project, null, requestedPaths, null, null, getSdkHandler(), noOpMessage, false);
  }

  /**
   * Create an SdkQuickFix dialog.
   *
   * @param project        The {@link Project} to use as a parent for the wizard dialog.
   * @param requestedPaths The paths of packages to install. Callers should ensure that the given packages include remote versions.
   * @param backgroundable Whether the dialog should show a "background" button on the progress step.
   */
  @Nullable
  public static ModelWizardDialog createDialogForPaths(@Nullable Project project,
                                                       @NotNull Collection<String> requestedPaths,
                                                       boolean backgroundable) {
    return createDialog(project, null, requestedPaths, null, null, getSdkHandler(), null, backgroundable);
  }

  public static void showSdkMissingDialog() {
    String msg = message("android.sdk.missing.msg");
    String title = message("android.sdk.missing.title");
    String okText = message("android.sdk.open.manager");
    String cancelText = CommonBundle.getCancelButtonText();

    if (Messages.showOkCancelDialog((Project)null, msg, title, okText, cancelText, Messages.getErrorIcon()) == Messages.OK) {
      showAndroidSdkManager();
    }
  }

  public static void showAndroidSdkManager() {
    ShowSettingsUtil.getInstance().showSettingsDialog(Arrays.stream(ProjectManager.getInstance().getOpenProjects()).findFirst().orElse(null), "Android");
  }

  private static AndroidSdkHandler getSdkHandler() {
    AndroidSdkData data = AndroidSdks.getInstance().tryToChooseAndroidSdk();

    if (data == null) {
      showSdkMissingDialog();
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
                                        @Nullable Collection<LocalPackage> requestedUninstalls,
                                        @Nullable AndroidSdkHandler sdkHandler,
                                        @Nullable String noOpMessage,
                                        boolean backgroundable) {
    if (sdkHandler == null) {
      return null;
    }

    RepoManager mgr = sdkHandler.getSdkManager(REPO_LOGGER);

    if (mgr.getLocalPath() == null) {
      showSdkMissingDialog();
      return null;
    }

    List<String> unknownPaths = new ArrayList<>();
    List<UpdatablePackage> resolvedPackages = new ArrayList<>();
    if (requestedPackages != null && !requestedPackages.isEmpty()
        || requestedPaths != null && !requestedPaths.isEmpty()) {
      // This is an expensive call involving a number of manifest download operations,
      // so make it only when some installations are requested.
      mgr.loadSynchronously(RepoManager.DEFAULT_EXPIRATION_PERIOD_MS, null, null, null,
               new StudioProgressRunner(true, false, "Finding Available SDK Components", project),
               new StudioDownloader(), StudioSettingsController.getInstance());
      RepositoryPackages packages = mgr.getPackages();
      if (requestedPackages == null) {
        requestedPackages = new ArrayList<>();
      }
      requestedPackages.addAll(lookupPaths(requestedPaths, packages, unknownPaths));

      try {
        resolvedPackages = resolve(requestedPackages, packages);
      }
      catch (PackageResolutionException e) {
        Messages.showErrorDialog(e.getMessage(), "Error Resolving Packages");
        return null;
      }
    }

    Set<LocalPackage> resolvedUninstalls = new HashSet<>();
    if (requestedUninstalls != null) {
      resolvedUninstalls.addAll(requestedUninstalls);
      // We don't want to uninstall something required by a package we're installing
      resolvedPackages.forEach(updatable -> resolvedUninstalls.remove(updatable.getLocal()));
    }

    List<UpdatablePackage> unavailableDownloads = new ArrayList<>();
    verifyAvailability(resolvedPackages, unavailableDownloads);

    // If there were requests we didn't understand or can't download, show an error.
    if (!unknownPaths.isEmpty() || !unavailableDownloads.isEmpty()) {
      String title = "Packages Unavailable";
      HtmlBuilder builder = new HtmlBuilder();
      builder.openHtmlBody()
        .add(String.format("%1$s packages are not available for download!", resolvedPackages.isEmpty() ? "All" : "Some"))
        .newline().newline().add("The following packages are not available:").beginList();
      for (UpdatablePackage p : unavailableDownloads) {
        builder.listItem().add(p.getRepresentative().getDisplayName());
      }
      for (String p : unknownPaths) {
        builder.listItem().add("Package id " + p);
      }
      builder.endList().closeHtmlBody();
      Messages.showErrorDialog(builder.getHtml(), title);
    }

    // If everything was removed, don't continue.
    if (resolvedPackages.isEmpty() && resolvedUninstalls.isEmpty()) {
      if (noOpMessage != null) {
        Messages.showErrorDialog(project, noOpMessage, "SDK Manager");
      }
      return null;
    }
    List<RemotePackage> installRequests = ContainerUtil.map(resolvedPackages, UpdatablePackage::getRemote);
    ModelWizard.Builder wizardBuilder = new ModelWizard.Builder();
    wizardBuilder.addStep(new LicenseAgreementStep(new LicenseAgreementModel(mgr.getLocalPath()), installRequests));
    InstallSelectedPackagesStep installStep =
      new InstallSelectedPackagesStep(resolvedPackages, resolvedUninstalls, sdkHandler, backgroundable);
    wizardBuilder.addStep(installStep);
    ModelWizard wizard = wizardBuilder.build();

    String title = "SDK Quickfix Installation";

    return new StudioWizardDialogBuilder(wizard, title, parent).setProject(project)
      .setModalityType(DialogWrapper.IdeModalityType.IDE)
      .setCancellationPolicy(ModelWizardDialog.CancellationPolicy.CAN_CANCEL_UNTIL_CAN_FINISH)
      .build();
  }

  @NotNull
  private static Collection<UpdatablePackage> lookupPaths(Collection<String> requestedPaths,
                                                          RepositoryPackages packages,
                                                          List<String> unknownPaths) {
    Map<String, UpdatablePackage> consolidated = packages.getConsolidatedPkgs();
    List<UpdatablePackage> result = new ArrayList<>();
    if (requestedPaths != null) {
      for (String path : requestedPaths) {
        UpdatablePackage p = consolidated.get(path);
        if (p == null || !p.hasRemote()) {
          unknownPaths.add(path);
        }
        else {
          result.add(p);
        }
      }
    }
    return result;
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

  /**
   * Checks whether a given package path is available for download.
   *
   * @param path The package path to check, corresponding to {@link RepoPackage#getPath()}.
   */
  @Slow
  public static boolean checkPathIsAvailableForDownload(String path) {
    // Loading the manager below can require waiting for something on the EDT. If this code has a read lock, this can result in deadlock.
    ApplicationManager.getApplication().assertReadAccessNotAllowed();

    RepoManager mgr = AndroidSdks.getInstance().tryToChooseSdkHandler().getSdkManager(REPO_LOGGER);
    mgr.loadSynchronously(
      RepoManager.DEFAULT_EXPIRATION_PERIOD_MS,
      null,
      null,
      null,
      new StudioProgressRunner(false, false, "Finding Available SDK Components", null),
      new StudioDownloader(),
      StudioSettingsController.getInstance());
    RepositoryPackages packages = mgr.getPackages();

    return packages.getRemotePackages().containsKey(path);
  }

  /**
   * Finds and adds dependencies for the given packages.
   *
   * @return The requested packages and dependencies.
   * @throws PackageResolutionException If the required packages have dependencies that are invalid or cannot be met.
   */
  // TODO: Once welcome wizard is rewritten using ModelWizard this should be refactored as needed.
  public static List<UpdatablePackage> resolve(@Nullable Collection<UpdatablePackage> requestedPackages,
                                               @NotNull RepositoryPackages packages) throws PackageResolutionException {
    List<UpdatablePackage> result = new ArrayList<>();
    if (requestedPackages == null) {
      requestedPackages = new ArrayList<>();
    }
    List<UpdatablePackage> resolved = Lists.newArrayList(requestedPackages);

    List<RemotePackage> remotes = new ArrayList<>();
    for (UpdatablePackage p : resolved) {
      if (p.hasRemote()) {
        remotes.add(p.getRemote());
      }
    }
    final AtomicReference<String> warning = new AtomicReference<>();
    ProgressIndicator errorCollector = new ProgressIndicatorAdapter() {
      @Override
      public void logWarning(@NotNull String s) {
        warning.set(s);
      }
    };
    List<RemotePackage> requiredPackages = InstallerUtil.computeRequiredPackages(remotes, packages, errorCollector);
    if (requiredPackages == null) {
      // there was a problem computing dependencies
      throw new PackageResolutionException(warning.get());
    }
    Set<String> requiredPaths = requiredPackages.stream().map(RemotePackage::getPath).collect(Collectors.toCollection(LinkedHashSet::new));
    Map<String, UpdatablePackage> allPackages = packages.getConsolidatedPkgs();
    for (String path : requiredPaths) {
      UpdatablePackage requiredPackage = allPackages.get(path);
      if (requiredPackage == null) {
        throw new PackageResolutionException("Failed to find package with key " + path);
      }
      result.add(requiredPackage);
    }

    return result;
  }

  public static class PackageResolutionException extends Exception {
    public PackageResolutionException(String message) {
      super(message);
    }
  }
}
