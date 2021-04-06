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
import static com.android.tools.idea.gradle.project.AndroidGradleProjectSettingsControlBuilder.ANDROID_STUDIO_DEFAULT_JDK_NAME;
import static com.android.tools.idea.wizard.WizardConstants.KEY_JDK_LOCATION;
import static com.intellij.openapi.util.text.StringUtil.isEmpty;

import com.android.prefs.AndroidLocationsSingleton;
import com.android.repository.api.RemotePackage;
import com.android.repository.api.RepoManager;
import com.android.repository.impl.meta.TypeDetails;
import com.android.sdklib.AndroidVersion;
import com.android.sdklib.repository.AndroidSdkHandler;
import com.android.sdklib.repository.meta.DetailsTypes;
import com.android.tools.idea.flags.StudioFlags;
import com.android.tools.idea.gradle.util.EmbeddedDistributionPaths;
import com.android.tools.idea.sdk.IdeSdks;
import com.android.tools.idea.sdk.SdkMerger;
import com.android.tools.idea.sdk.StudioDownloader;
import com.android.tools.idea.sdk.StudioSettingsController;
import com.android.tools.idea.sdk.progress.StudioLoggerProgressIndicator;
import com.android.tools.idea.sdk.progress.StudioProgressRunner;
import com.android.tools.idea.sdk.wizard.SdkQuickfixUtils;
import com.android.tools.idea.ui.ApplicationUtils;
import com.android.tools.idea.welcome.SdkLocationUtils;
import com.android.tools.idea.welcome.config.AndroidFirstRunPersistentData;
import com.android.tools.idea.welcome.config.FirstRunWizardMode;
import com.android.tools.idea.welcome.install.AndroidSdk;
import com.android.tools.idea.welcome.install.AndroidVirtualDevice;
import com.android.tools.idea.welcome.install.CheckSdkOperation;
import com.android.tools.idea.welcome.install.ComponentCategory;
import com.android.tools.idea.welcome.install.ComponentInstaller;
import com.android.tools.idea.welcome.install.ComponentTreeNode;
import com.android.tools.idea.welcome.install.Gvm;
import com.android.tools.idea.welcome.install.Haxm;
import com.android.tools.idea.welcome.install.InstallComponentsOperation;
import com.android.tools.idea.welcome.install.InstallContext;
import com.android.tools.idea.welcome.install.InstallOperation;
import com.android.tools.idea.welcome.install.InstallableComponent;
import com.android.tools.idea.welcome.install.InstallationCancelledException;
import com.android.tools.idea.welcome.install.InstallationIntention;
import com.android.tools.idea.welcome.install.Platform;
import com.android.tools.idea.welcome.install.WizardException;
import com.android.tools.idea.wizard.WizardConstants;
import com.android.tools.idea.wizard.dynamic.DynamicWizardPath;
import com.android.tools.idea.wizard.dynamic.DynamicWizardStep;
import com.android.tools.idea.wizard.dynamic.ScopedStateStore;
import com.google.common.base.Function;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Lists;
import com.intellij.execution.ui.ConsoleViewContentType;
import com.intellij.openapi.application.ModalityState;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.project.ProjectManager;
import com.intellij.openapi.projectRoots.Sdk;
import com.intellij.openapi.util.io.FileUtil;
import java.io.File;
import java.io.IOException;
import java.nio.file.Paths;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Supplier;
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

  // This will be different than the actual handler, since this will change as and when we change the path in the UI.
  private AndroidSdkHandler myLocalHandler;

  private ComponentTreeNode myComponentTree;
  private final ProgressStep myProgressStep;
  @NotNull private ComponentInstaller myComponentInstaller;
  private final boolean myInstallUpdates;
  private SdkComponentsStep myComponentsStep;

  public InstallComponentsPath(@NotNull FirstRunWizardMode mode,
                               @NotNull File sdkLocation,
                               @NotNull ProgressStep progressStep,
                               boolean installUpdates) {
    myMode = mode;

    // Create a new instance for use during installation
    myLocalHandler = AndroidSdkHandler.getInstance(AndroidLocationsSingleton.INSTANCE, sdkLocation.toPath());

    myProgressStep = progressStep;
    myComponentInstaller = new ComponentInstaller(myLocalHandler);
    myInstallUpdates = installUpdates;
  }

  private ComponentTreeNode createComponentTree(@NotNull FirstRunWizardMode reason,
                                                boolean createAvd) {
    List<ComponentTreeNode> components = Lists.newArrayList();
    components.add(new AndroidSdk(myInstallUpdates));

    RepoManager sdkManager = myLocalHandler.getSdkManager(new StudioLoggerProgressIndicator(getClass()));
    sdkManager.loadSynchronously(RepoManager.DEFAULT_EXPIRATION_PERIOD_MS, null, null, null,
                    new StudioProgressRunner(true, false, "Finding Available SDK Components", null),
                    new StudioDownloader(), StudioSettingsController.getInstance());
    Map<String, RemotePackage> remotePackages = sdkManager.getPackages().getRemotePackages();
    ComponentTreeNode platforms = Platform.Companion.createSubtree(remotePackages, myInstallUpdates);
    if (platforms != null) {
      components.add(platforms);
    }
    InstallationIntention installationIntention = myInstallUpdates ? InstallationIntention.INSTALL_WITH_UPDATES
                                                                   : InstallationIntention.INSTALL_WITHOUT_UPDATES;
    if (reason == FirstRunWizardMode.NEW_INSTALL && Haxm.InstallerInfo.canRun()) {
      components.add(new Haxm(installationIntention, FirstRunWizard.KEY_CUSTOM_INSTALL));
    }
    if (reason == FirstRunWizardMode.NEW_INSTALL && Gvm.InstallerInfo.canRun()) {
      components.add(new Gvm(installationIntention, FirstRunWizard.KEY_CUSTOM_INSTALL));
    }
    if (createAvd) {
      components.add(new AndroidVirtualDevice(remotePackages, myInstallUpdates));
    }
    return new ComponentCategory("Root", "Root node that is not supposed to appear in the UI", components);
  }

  private static File createTempDir() throws WizardException {
    File tempDirectory;
    try {
      tempDirectory = FileUtil.createTempDirectory("AndroidStudio", "FirstRun", true);
    }
    catch (IOException e) {
      throw new WizardException("Unable to create temporary folder: " + e.getMessage(), e);
    }
    return tempDirectory;
  }

  @Nullable
  private File getHandoffAndroidSdkSource() {
    File androidSrc = myMode.getAndroidSrc();
    if (androidSrc != null) {
      File[] files = androidSrc.listFiles();
      if (androidSrc.isDirectory() && files != null && files.length > 0) {
        return androidSrc;
      }
    }
    return null;
  }

  /**
   * <p>Creates an operation that will prepare SDK so the components can be installed.</p>
   * <p>Supported scenarios:</p>
   * <ol>
   * <li>Install wizard leaves SDK repository to merge - merge will happen whether destination exists or not.</li>
   * <li>Valid SDK at destination - do nothing, the wizard will update components later</li>
   * <li>No handoff, no valid SDK at destination - SDK "seed" will be downloaded and unpacked</li>
   * </ol>
   *
   * @return install operation object that will perform the setup
   */
  private InstallOperation<File, File> createInitSdkOperation(InstallContext installContext, double progressRatio) {
    File handoffSource = getHandoffAndroidSdkSource();
    if (handoffSource != null) {
      return new MergeOperation(handoffSource, installContext, progressRatio);
    }
    // Nothing to merge, first operation simply passes path through
    return InstallOperation.wrap(installContext, new ReturnValue(), 0);
  }

  @Override
  protected void init() {
    File location = myLocalHandler.getLocation().toFile();
    assert location != null;

    myState.put(WizardConstants.KEY_SDK_INSTALL_LOCATION, location.getAbsolutePath());
    myState.put(WizardConstants.KEY_JDK_LOCATION, EmbeddedDistributionPaths.getInstance().getEmbeddedJdkPath().getPath());

    myComponentTree = createComponentTree(myMode, !isChromeOSAndIsNotHWAccelerated() && myMode.shouldCreateAvd());
    myComponentTree.init(myProgressStep);

    myComponentsStep = new SdkComponentsStep(
      myComponentTree, FirstRunWizard.KEY_CUSTOM_INSTALL, WizardConstants.KEY_SDK_INSTALL_LOCATION, myMode, myWizard.getDisposable());
    addStep(myComponentsStep);

    myComponentTree.init(myProgressStep);
    myComponentTree.updateState(myLocalHandler);
    for (DynamicWizardStep step : myComponentTree.createSteps()) {
      addStep(step);
    }
    if (myMode != FirstRunWizardMode.INSTALL_HANDOFF) {
      Supplier<Collection<RemotePackage>> supplier = () -> {
        Iterable<InstallableComponent> components = myComponentTree.getChildrenToInstall();
        try {
          return myComponentInstaller.getPackagesToInstall(components);
        }
        catch (SdkQuickfixUtils.PackageResolutionException e) {
          Logger.getInstance(InstallComponentsPath.class).warn(e);
          return null;
        }
      };

      addStep(new InstallSummaryStep(FirstRunWizard.KEY_CUSTOM_INSTALL, WizardConstants.KEY_SDK_INSTALL_LOCATION,
                                     WizardConstants.KEY_JDK_LOCATION, supplier));
    }
  }

  @Override
  public void deriveValues(Set<? extends ScopedStateStore.Key> modified) {
    super.deriveValues(modified);
    if (modified.contains(WizardConstants.KEY_SDK_INSTALL_LOCATION)) {
      String sdkPath = myState.get(WizardConstants.KEY_SDK_INSTALL_LOCATION);
      if (sdkPath != null) {
        File sdkLocation = new File(sdkPath);
        if (!FileUtil.filesEqual(myLocalHandler.getLocation().toFile(), sdkLocation)) {
          myLocalHandler = AndroidSdkHandler.getInstance(AndroidLocationsSingleton.INSTANCE, myLocalHandler.getFileOp().toPath(sdkLocation));
          StudioLoggerProgressIndicator progress = new StudioLoggerProgressIndicator(getClass());
          myComponentsStep.startLoading();
          myLocalHandler.getSdkManager(progress)
            .load(RepoManager.DEFAULT_EXPIRATION_PERIOD_MS, null, ImmutableList.of(packages -> {
                    myComponentInstaller = new ComponentInstaller(myLocalHandler);
                    myComponentTree.updateState(myLocalHandler);
                    myComponentsStep.stopLoading();
                  }), ImmutableList.of(() -> myComponentsStep.loadingError()),
                  new StudioProgressRunner(false, false, "Finding Available SDK Components", getProject()), new StudioDownloader(),
                  StudioSettingsController.getInstance());
        }
      }
    }
  }

  @NotNull
  @Override
  public String getPathName() {
    return "Setup Android Studio Components";
  }

  @Override
  public void runLongOperation() throws WizardException {
    final double INIT_SDK_OPERATION_PROGRESS_SHARE = 0.3;
    final double INSTALL_COMPONENTS_OPERATION_PROGRESS_SHARE = 1.0 - INIT_SDK_OPERATION_PROGRESS_SHARE;

    final InstallContext installContext = new InstallContext(createTempDir(), myProgressStep);
    final File destination = getDestination();
    final InstallOperation<File, File> initialize = createInitSdkOperation(installContext, INIT_SDK_OPERATION_PROGRESS_SHARE);

    final Collection<? extends InstallableComponent> selectedComponents = myComponentTree.getChildrenToInstall();
    CheckSdkOperation checkSdk = new CheckSdkOperation(installContext);
    InstallComponentsOperation install =
      new InstallComponentsOperation(installContext, selectedComponents, myComponentInstaller, INSTALL_COMPONENTS_OPERATION_PROGRESS_SHARE);

    Sdk jdk = null;
    String jdkLocation = myState.get(KEY_JDK_LOCATION);
    if (jdkLocation != null) {
      jdk = IdeSdks.getInstance().setJdkPath(new File(jdkLocation));
    }
    SetPreference setPreference = new SetPreference(myMode.getInstallerTimestamp(),
                                                    ModalityState.stateForComponent(myWizard.getContentPane()),
                                                    jdk);
    if (selectedComponents.isEmpty()) {
      myProgressStep.print("Nothing to do!", ConsoleViewContentType.NORMAL_OUTPUT);
    }
    try {
      initialize.then(install).then(setPreference)
        .then(new ConfigureComponents(installContext, selectedComponents, myLocalHandler)).then(checkSdk).execute(destination);
    }
    catch (InstallationCancelledException e) {
      installContext.print("Android Studio setup was canceled", ConsoleViewContentType.ERROR_OUTPUT);
      myProgressStep.print("Android Studio setup was canceled", ConsoleViewContentType.ERROR_OUTPUT);
    }
  }

  @Nullable
  public static RemotePackage findLatestPlatform(@Nullable Map<String, RemotePackage> remotePackages) {
    if (remotePackages == null) {
      return null;
    }
    AndroidVersion max = null;
    RemotePackage latest = null;
    for (RemotePackage pkg : remotePackages.values()) {
      TypeDetails details = pkg.getTypeDetails();
      if (!(details instanceof DetailsTypes.PlatformDetailsType)) {
        continue;
      }
      DetailsTypes.PlatformDetailsType platformDetails = (DetailsTypes.PlatformDetailsType)details;
      AndroidVersion version = platformDetails.getAndroidVersion();
      if (version.isPreview()) {
        // We only want stable platforms
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

  @Override
  public boolean isPathVisible() {
    return true;
  }

  public boolean shouldDownloadingComponentsStepBeShown() {
    String path = myState.get(WizardConstants.KEY_SDK_INSTALL_LOCATION);
    assert path != null;

    return SdkLocationUtils.isWritable(Paths.get(path));
  }

  private static class MergeOperation extends InstallOperation<File, File> {
    private final File myRepo;
    private final InstallContext myContext;
    private boolean myRepoWasMerged = false;

    MergeOperation(File repo, InstallContext context, double progressRatio) {
      super(context, progressRatio);
      myRepo = repo;
      myContext = context;
    }

    @NotNull
    @Override
    protected File perform(@NotNull ProgressIndicator indicator, @NotNull File destination) throws WizardException {
      indicator.setText("Installing Android SDK");
      try {
        FileUtil.ensureExists(destination);
        if (!FileUtil.filesEqual(destination.getCanonicalFile(), myRepo.getCanonicalFile())) {
          SdkMerger.mergeSdks(myRepo, destination, indicator);
          myRepoWasMerged = true;
        }
        myContext.print(String.format("Android SDK was installed to %1$s\n", destination), ConsoleViewContentType.SYSTEM_OUTPUT);
        return destination;
      }
      catch (IOException e) {
        throw new WizardException(e.getMessage(), e);
      }
      finally {
        indicator.stop();
      }
    }

    @Override
    public void cleanup(@NotNull File result) {
      if (myRepoWasMerged && myRepo.exists()) {
        FileUtil.delete(myRepo);
      }
    }
  }

  private static class ReturnValue implements Function<File, File> {
    @Override
    public File apply(@Nullable File input) {
      assert input != null;
      return input;
    }
  }

  private static class SetPreference implements Function<File, File> {
    @NotNull private final ModalityState myModalityState;
    @Nullable private final String myInstallerTimestamp;
    @Nullable private final Sdk myJdk;

    SetPreference(@Nullable String installerTimestamp, @NotNull ModalityState modalityState, @Nullable Sdk jdk) {
      myInstallerTimestamp = installerTimestamp;
      myModalityState = modalityState;
      myJdk = jdk;
    }

    @Override
    public File apply(@Nullable final File input) {
      assert input != null;

      ApplicationUtils.invokeWriteActionAndWait(myModalityState, () -> {
        IdeSdks.getInstance().setAndroidSdkPath(input, myJdk, ProjectManager.getInstance().getDefaultProject());
        if (myJdk != null && !isEmpty(myJdk.getHomePath()) && StudioFlags.ALLOW_JDK_PER_PROJECT.get()) {
          // Add as Android Studio default JDK
          IdeSdks.findOrCreateJdk(ANDROID_STUDIO_DEFAULT_JDK_NAME, new File(myJdk.getHomePath()));
        }
        AndroidFirstRunPersistentData.getInstance().markSdkUpToDate(myInstallerTimestamp);
      });

      return input;
    }
  }

  private static class ConfigureComponents implements Function<File, File> {
    private final InstallContext myInstallContext;
    private final Collection<? extends InstallableComponent> mySelectedComponents;
    private final AndroidSdkHandler mySdkHandler;

    ConfigureComponents(InstallContext installContext,
                        Collection<? extends InstallableComponent> selectedComponents,
                        AndroidSdkHandler sdkHandler) {
      myInstallContext = installContext;
      mySelectedComponents = selectedComponents;
      mySdkHandler = sdkHandler;
    }

    @Override
    public File apply(@Nullable File input) {
      assert input != null;
      for (InstallableComponent component : mySelectedComponents) {
        component.configure(myInstallContext, mySdkHandler);
      }
      return input;
    }
  }
}