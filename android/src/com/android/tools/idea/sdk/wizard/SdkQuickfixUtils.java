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

import com.android.annotations.VisibleForTesting;
import com.android.repository.api.*;
import com.android.repository.impl.meta.RepositoryPackages;
import com.android.repository.util.InstallerUtil;
import com.android.sdklib.repository.AndroidSdkHandler;
import com.android.tools.idea.sdk.StudioDownloader;
import com.android.tools.idea.sdk.StudioSettingsController;
import com.android.tools.idea.sdk.progress.StudioLoggerProgressIndicator;
import com.android.tools.idea.ui.wizard.StudioWizardDialogBuilder;
import com.android.tools.idea.wizard.model.ModelWizard;
import com.android.tools.idea.wizard.model.ModelWizardDialog;
import com.android.utils.HtmlBuilder;
import com.google.common.collect.Collections2;
import com.google.common.collect.Lists;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.openapi.ui.Messages;
import org.jetbrains.android.sdk.AndroidSdkData;
import org.jetbrains.android.sdk.AndroidSdkUtils;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.awt.*;
import java.util.*;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;

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
    return createDialog(null, parent, requestedPaths, null, null, getSdkHandler(), backgroundable);
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
    return createDialog(null, parent, null, requestedPackages, uninstallPackages, getSdkHandler(), backgroundable);
  }

  /**
   * Create an SdkQuickFix dialog.
   *
   * @param project        The {@link Project} to use as a parent for the wizard dialog.
   * @param requestedPaths The paths of packages to install. Callers should ensure that the given packages include remote versions.
   */
  @Nullable
  public static ModelWizardDialog createDialogForPaths(@Nullable Project project, @NotNull Collection<String> requestedPaths) {
    return createDialog(project, null, requestedPaths, null, null, getSdkHandler(), false);
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
                                        @Nullable Collection<LocalPackage> requestedUninstalls,
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

    List<String> unknownPaths = new ArrayList<>();
    List<UpdatablePackage> resolvedPackages;
    mgr.loadSynchronously(RepoManager.DEFAULT_EXPIRATION_PERIOD_MS, new StudioLoggerProgressIndicator(SdkQuickfixUtils.class),
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

    Set<LocalPackage> resolvedUninstalls = new HashSet<>();
    if (requestedUninstalls != null) {
      resolvedUninstalls.addAll(requestedUninstalls);
      // We don't want to uninstall something required by a package we're installing
      resolvedPackages.forEach(updatable -> resolvedUninstalls.remove(updatable.getLocal()));
    }

    List<UpdatablePackage> unavailableDownloads = Lists.newArrayList();
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
      return null;
    }
    List<RemotePackage> installRequests = resolvedPackages.stream().map(UpdatablePackage::getRemote).collect(Collectors.toList());
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
      requestedPackages = Lists.newArrayList();
    }
    List<UpdatablePackage> resolved = Lists.newArrayList(requestedPackages);

    List<RemotePackage> remotes = Lists.newArrayList();
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
    result.addAll(Collections2.transform(requiredPaths, packages.getConsolidatedPkgs()::get));

    return result;
  }

  public static class PackageResolutionException extends Exception {
    public PackageResolutionException(String message) {
      super(message);
    }
  }
}
