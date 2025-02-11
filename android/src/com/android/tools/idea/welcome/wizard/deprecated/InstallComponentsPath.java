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
package com.android.tools.idea.welcome.wizard.deprecated;

import static com.android.tools.idea.avdmanager.HardwareAccelerationCheck.isChromeOSAndIsNotHWAccelerated;

import com.android.prefs.AndroidLocationsSingleton;
import com.android.repository.api.RemotePackage;
import com.android.repository.api.RepoManager;
import com.android.repository.impl.meta.TypeDetails;
import com.android.sdklib.AndroidVersion;
import com.android.sdklib.repository.AndroidSdkHandler;
import com.android.sdklib.repository.meta.DetailsTypes;
import com.android.tools.idea.observable.core.ObjectValueProperty;
import com.android.tools.idea.progress.StudioLoggerProgressIndicator;
import com.android.tools.idea.progress.StudioProgressRunner;
import com.android.tools.idea.sdk.StudioDownloader;
import com.android.tools.idea.sdk.StudioSettingsController;
import com.android.tools.idea.sdk.wizard.SdkQuickfixUtils;
import com.android.tools.idea.sdk.wizard.legacy.LicenseAgreementStep;
import com.android.tools.idea.welcome.SdkLocationUtils;
import com.android.tools.idea.welcome.config.FirstRunWizardMode;
import com.android.tools.idea.welcome.install.AndroidSdkComponentTreeNode;
import com.android.tools.idea.welcome.install.AndroidVirtualDeviceSdkComponentTreeNode;
import com.android.tools.idea.welcome.install.SdkComponentCategoryTreeNode;
import com.android.tools.idea.welcome.install.SdkComponentInstaller;
import com.android.tools.idea.welcome.install.SdkComponentTreeNode;
import com.android.tools.idea.welcome.install.AehdSdkComponentTreeNode;
import com.android.tools.idea.welcome.install.InstallContext;
import com.android.tools.idea.welcome.install.InstallableSdkComponentTreeNode;
import com.android.tools.idea.welcome.install.AndroidPlatformSdkComponentTreeNode;
import com.android.tools.idea.welcome.install.WizardException;
import com.android.tools.idea.welcome.wizard.FirstRunWizardTracker;
import com.android.tools.idea.wizard.WizardConstants;
import com.android.tools.idea.wizard.dynamic.DynamicWizardPath;
import com.intellij.openapi.application.ModalityState;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.io.FileUtil;
import java.io.File;
import java.io.IOException;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Wizard path that manages component installation flow. It will prompt the user
 * for the components to install and for install parameters. On wizard
 * completion it will download and unzip component archives and will
 * perform component setup.
 */
public class InstallComponentsPath extends DynamicWizardPath implements LongRunningOperationPath {
  @NotNull private final FirstRunWizardMode myMode;

  // This will be different from the actual handler, since this will change as and when we change the path in the UI.
  private final ObjectValueProperty<AndroidSdkHandler> myLocalHandlerProperty;
  private final @NotNull FirstRunWizardTracker myTracker;

  private SdkComponentTreeNode myComponentTree;
  private final AbstractProgressStep myProgressStep;
  @NotNull private final SdkComponentInstaller mySdkComponentInstaller;
  private final boolean myInstallUpdates;
  private SdkComponentsStep myComponentsStep;
  @Nullable private LicenseAgreementStep myLicenseAgreementStep;

  public InstallComponentsPath(@NotNull FirstRunWizardMode mode,
                               @NotNull File sdkLocation,
                               @NotNull AbstractProgressStep progressStep,
                               @NotNull SdkComponentInstaller sdkComponentInstaller,
                               boolean installUpdates,
                               @NotNull FirstRunWizardTracker tracker) {
    myMode = mode;

    // Create a new instance for use during installation
    myLocalHandlerProperty = new ObjectValueProperty<>(AndroidSdkHandler.getInstance(AndroidLocationsSingleton.INSTANCE, sdkLocation.toPath()));

    myProgressStep = progressStep;
    mySdkComponentInstaller = sdkComponentInstaller;
    myInstallUpdates = installUpdates;
    myTracker = tracker;
  }

  private SdkComponentTreeNode createComponentTree(@NotNull FirstRunWizardMode reason,
                                                   boolean createAvd) {
    List<SdkComponentTreeNode> components = new ArrayList<>();
    components.add(new AndroidSdkComponentTreeNode(myInstallUpdates));

    AndroidSdkHandler localHandler = myLocalHandlerProperty.get();
    RepoManager sdkManager = localHandler.getRepoManager(new StudioLoggerProgressIndicator(getClass()));
    sdkManager.loadSynchronously(RepoManager.DEFAULT_EXPIRATION_PERIOD_MS, null, null, null,
                    new StudioProgressRunner(true, false, "Finding Available SDK Components", null),
                    new StudioDownloader(), StudioSettingsController.getInstance());

    Collection<RemotePackage> remotePackages = sdkManager.getPackages().getRemotePackages().values();
    components.add(AndroidPlatformSdkComponentTreeNode.Companion.createSubtree(remotePackages, myInstallUpdates));

    AehdSdkComponentTreeNode.InstallationIntention installationIntention =
      myInstallUpdates ? AehdSdkComponentTreeNode.InstallationIntention.INSTALL_WITH_UPDATES
                                                    : AehdSdkComponentTreeNode.InstallationIntention.INSTALL_WITHOUT_UPDATES;
    if (reason == FirstRunWizardMode.NEW_INSTALL && AehdSdkComponentTreeNode.InstallerInfo.canRun()) {
      components.add(new AehdSdkComponentTreeNode(installationIntention));
    }
    if (createAvd) {
      AndroidVirtualDeviceSdkComponentTreeNode avdCreator = new AndroidVirtualDeviceSdkComponentTreeNode(remotePackages, myInstallUpdates);
      if (avdCreator.isAvdCreationNeeded(localHandler)) {
        components.add(avdCreator);
      }
    }
    return new SdkComponentCategoryTreeNode("Root", "Root node that is not supposed to appear in the UI", components);
  }

  @Override
  protected void init() {
    AndroidSdkHandler localHandler = myLocalHandlerProperty.get();
    File location = localHandler.getLocation().toFile();
    assert location != null;

    myState.put(WizardConstants.KEY_SDK_INSTALL_LOCATION, location.getAbsolutePath());

    myComponentTree = createComponentTree(myMode, !isChromeOSAndIsNotHWAccelerated() && myMode.shouldCreateAvd());
    myComponentTree.updateState(localHandler);

    Supplier<Collection<RemotePackage>> supplier = () -> {
      Iterable<InstallableSdkComponentTreeNode> components = myComponentTree.getChildrenToInstall();
      try {
        return mySdkComponentInstaller.getPackagesToInstall(myLocalHandlerProperty.get(), components);
      }
      catch (SdkQuickfixUtils.PackageResolutionException e) {
        Logger.getInstance(InstallComponentsPath.class).warn(e);
        return null;
      }
    };
    Supplier<List<String>> installRequests = () -> {
      Collection<RemotePackage> remotePackages = supplier.get();
      return remotePackages == null ? null : remotePackages.stream().map(it -> it.getPath()).collect(Collectors.toList());
    };
    myLicenseAgreementStep = new LicenseAgreementStep(myWizard.getDisposable(), installRequests, myLocalHandlerProperty::get, myTracker);

    myComponentsStep = new SdkComponentsStep(
      getProject(),
      myComponentTree,
      FirstRunWizard.KEY_CUSTOM_INSTALL,
      WizardConstants.KEY_SDK_INSTALL_LOCATION,
      myMode,
      myLocalHandlerProperty,
      myLicenseAgreementStep,
      myWizard.getDisposable(),
      myTracker
    );
    addStep(myComponentsStep);

    if (myMode != FirstRunWizardMode.INSTALL_HANDOFF) {
      addStep(new InstallSummaryStep(FirstRunWizard.KEY_CUSTOM_INSTALL, WizardConstants.KEY_SDK_INSTALL_LOCATION, supplier, myTracker));
      addStep(myLicenseAgreementStep);
    }
  }

  @NotNull
  @Override
  public String getPathName() {
    return "Setup Android Studio Components";
  }

  @Override
  public void runLongOperation() throws WizardException {
    if (myLicenseAgreementStep != null) {
      myLicenseAgreementStep.performFinishingActions();
    }

    Collection<InstallableSdkComponentTreeNode> componentsToInstall = myComponentTree.getChildrenToInstall();
    myTracker.trackSdkComponentsToInstall(componentsToInstall.stream().map(InstallableSdkComponentTreeNode::sdkComponentsMetricKind).toList());

    AndroidSdkHandler localHandler = myLocalHandlerProperty.get();
    mySdkComponentInstaller.installComponents(
      componentsToInstall,
      new InstallContext(createTempDir(), myProgressStep),
      myMode.getInstallerTimestamp(),
      ModalityState.stateForComponent(myWizard.getContentPane()),
      localHandler,
      getDestination()
    );
  }

  /**
   * Returns the latest platform from a given list.
   *
   * It is possible to select whether one wants the last extension of the latest platform or whether
   * one wants the latest base extension.
   *
   *
   * @param remotePackages the list of packages to search for the last platform.
   * @param returnBaseExtension whether to always return the base extension of the latest platform.
   * @return
   */
  @Nullable
  public static RemotePackage findLatestPlatform(
    @NotNull Collection<RemotePackage> remotePackages,
    boolean returnBaseExtension
  ) {
    AndroidVersion max = null;
    RemotePackage latest = null;
    for (RemotePackage pkg : remotePackages) {
      TypeDetails details = pkg.getTypeDetails();
      if (!(details instanceof DetailsTypes.PlatformDetailsType)) {
        continue;
      }
      DetailsTypes.PlatformDetailsType platformDetails = (DetailsTypes.PlatformDetailsType)details;
      AndroidVersion version = platformDetails.getAndroidVersion();
      if (version.isPreview() || (returnBaseExtension && !version.isBaseExtension())) {
        // We only want stable platforms, and possibly only base extension if requested
        continue;
      }
      if (max == null || version.compareTo(max) > 0) {
        latest = pkg;
        max = version;
      }
    }
    return latest;
  }

  @NotNull
  private File getDestination() throws WizardException {
    String destinationPath = myState.get(WizardConstants.KEY_SDK_INSTALL_LOCATION);
    assert destinationPath != null;

    final File destination = new File(destinationPath);
    if (destination.isFile()) {
      throw new WizardException(String.format("Path %s does not point to a directory", destination));
    }
    return destination;
  }

  @Override
  public boolean performFinishingActions() {
    // Everything happens after wizard completion
    return true;
  }

  public boolean shouldDownloadingComponentsStepBeShown() {
    String path = myState.get(WizardConstants.KEY_SDK_INSTALL_LOCATION);
    assert path != null;

    return SdkLocationUtils.isWritable(Paths.get(path));
  }

  public static File createTempDir() throws WizardException {
    File tempDirectory;
    try {
      tempDirectory = FileUtil.createTempDirectory("AndroidStudio", "FirstRun", true);
    }
    catch (IOException e) {
      throw new WizardException("Unable to create temporary folder: " + e.getMessage(), e);
    }
    return tempDirectory;
  }
}
