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
package com.android.tools.idea.welcome.wizard;

import com.android.SdkConstants;
import com.android.repository.api.RemotePackage;
import com.android.repository.api.RepoManager;
import com.android.repository.impl.meta.TypeDetails;
import com.android.repository.io.FileOp;
import com.android.repository.io.FileOpUtils;
import com.android.sdklib.AndroidVersion;
import com.android.sdklib.repository.AndroidSdkHandler;
import com.android.sdklib.repository.meta.DetailsTypes;
import com.android.tools.idea.sdk.IdeSdks;
import com.android.tools.idea.sdk.SdkMerger;
import com.android.tools.idea.sdk.StudioDownloader;
import com.android.tools.idea.sdk.StudioSettingsController;
import com.android.tools.idea.sdk.progress.RepoProgressIndicatorAdapter;
import com.android.tools.idea.sdk.progress.StudioLoggerProgressIndicator;
import com.android.tools.idea.sdk.wizard.SdkQuickfixUtils;
import com.android.tools.idea.ui.ApplicationUtils;
import com.android.tools.idea.welcome.SdkLocationUtils;
import com.android.tools.idea.welcome.config.AndroidFirstRunPersistentData;
import com.android.tools.idea.welcome.config.FirstRunWizardMode;
import com.android.tools.idea.welcome.install.*;
import com.android.tools.idea.wizard.WizardConstants;
import com.android.tools.idea.wizard.dynamic.DynamicWizard;
import com.android.tools.idea.wizard.dynamic.DynamicWizardPath;
import com.android.tools.idea.wizard.dynamic.DynamicWizardStep;
import com.android.tools.idea.wizard.dynamic.ScopedStateStore;
import com.google.common.base.Function;
import com.google.common.collect.Lists;
import com.intellij.execution.ui.ConsoleViewContentType;
import com.intellij.openapi.application.ModalityState;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.util.ProgressWindow;
import com.intellij.openapi.project.ProjectManager;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.io.FileUtil;
import org.jetbrains.android.sdk.AndroidSdkData;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.io.FilenameFilter;
import java.io.IOException;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Supplier;

/**
 * Wizard path that manages component installation flow. It will prompt the user
 * for the components to install and for install parameters. On wizard
 * completion it will download and unzip component archives and will
 * perform component setup.
 */
public class InstallComponentsPath extends DynamicWizardPath implements LongRunningOperationPath {
  private final FileOp myFileOp;
  @NotNull private final FirstRunWizardMode myMode;

  // This will be different than the actual handler, since this will change as and when we change the path in the UI.
  private AndroidSdkHandler myLocalHandler;

  private ComponentTreeNode myComponentTree;
  private final ProgressStep myProgressStep;
  @NotNull private ComponentInstaller myComponentInstaller;
  private final boolean myInstallUpdates;

  public InstallComponentsPath(@NotNull FirstRunWizardMode mode,
                               @NotNull File sdkLocation,
                               @NotNull ProgressStep progressStep,
                               boolean installUpdates) {
    myFileOp = FileOpUtils.create();
    myMode = mode;

    // Create a new instance for use during installation
    myLocalHandler = AndroidSdkHandler.getInstance(sdkLocation);

    myProgressStep = progressStep;
    myComponentInstaller = new ComponentInstaller(myLocalHandler);
    myInstallUpdates = installUpdates;
  }

  private ComponentTreeNode createComponentTree(@NotNull FirstRunWizardMode reason,
                                                @NotNull ScopedStateStore stateStore,
                                                boolean createAvd) {
    List<ComponentTreeNode> components = Lists.newArrayList();
    components.add(new AndroidSdk(stateStore, myInstallUpdates));

    DynamicWizard wizard = getWizard();
    ProgressWindow progressWindow = new ProgressWindow(false, false, null);

    if (wizard != null) {
      Disposer.register(wizard.getDisposable(), progressWindow);
    }

    com.android.repository.api.ProgressIndicator progress = new RepoProgressIndicatorAdapter(progressWindow);
    RepoManager sdkManager = myLocalHandler.getSdkManager(new StudioLoggerProgressIndicator(getClass()));
    sdkManager.loadSynchronously(RepoManager.DEFAULT_EXPIRATION_PERIOD_MS, progress,
                                 new StudioDownloader(progressWindow), StudioSettingsController.getInstance());
    Map<String, RemotePackage> remotePackages = sdkManager.getPackages().getRemotePackages();
    ComponentTreeNode platforms = Platform.createSubtree(stateStore, remotePackages, myInstallUpdates);
    if (platforms != null) {
      components.add(platforms);
    }
    if (Haxm.canRun() && reason == FirstRunWizardMode.NEW_INSTALL) {
      components.add(new Haxm(stateStore, FirstRunWizard.KEY_CUSTOM_INSTALL, myInstallUpdates));
    }
    if (createAvd) {
      components.add(new AndroidVirtualDevice(stateStore, remotePackages, myInstallUpdates, myFileOp));
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

  private static boolean hasPlatformsDir(@Nullable File[] files) {
    if (files == null) {
      return false;
    }
    for (File file : files) {
      if (isPlatformsDir(file)) {
        return true;
      }
    }
    return false;
  }

  private static boolean isPlatformsDir(File file) {
    return file.isDirectory() && file.getName().equalsIgnoreCase(SdkConstants.FD_PLATFORMS);
  }

  /**
   * This is an attempt to isolate from SDK packaging peculiarities.
   */
  @NotNull
  private static File getSdkRoot(@NotNull File expandedLocation) {
    File[] files = expandedLocation.listFiles();
    // Breadth-first scan - to lower chance of false positive
    if (hasPlatformsDir(files)) {
      return expandedLocation;
    }
    // Only scan one level down (no recursion) - avoid false positives
    if (files != null) {
      for (File file : files) {
        if (hasPlatformsDir(file.listFiles())) {
          return file;
        }
      }
    }
    return expandedLocation;
  }

  /**
   * @return null if the user cancels from the UI
   */
  @NotNull
  private static InstallOperation<File, File> downloadAndUnzipSdkSeed(@NotNull InstallContext context,
                                                                      @NotNull final File destination,
                                                                      double progressShare) {
    final double DOWNLOAD_OPERATION_PROGRESS_SHARE = progressShare * 0.8;
    final double UNZIP_OPERATION_PROGRESS_SHARE = progressShare * 0.15;
    final double MOVE_OPERATION_PROGRESS_SHARE = progressShare - DOWNLOAD_OPERATION_PROGRESS_SHARE - UNZIP_OPERATION_PROGRESS_SHARE;

    DownloadOperation download =
      new DownloadOperation(context, FirstRunWizardDefaults.getSdkDownloadUrl(), DOWNLOAD_OPERATION_PROGRESS_SHARE);
    UnpackOperation unpack = new UnpackOperation(context, UNZIP_OPERATION_PROGRESS_SHARE);
    MoveSdkOperation move = new MoveSdkOperation(context, destination, MOVE_OPERATION_PROGRESS_SHARE);

    return download.then(unpack).then(move);
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
  private InstallOperation<File, File> createInitSdkOperation(InstallContext installContext, File destination, double progressRatio) {
    File handoffSource = getHandoffAndroidSdkSource();
    if (handoffSource != null) {
      return new MergeOperation(handoffSource, installContext, progressRatio);
    }
    if (isNonEmptyDirectory(destination)) {
      if (AndroidSdkData.getSdkData(destination) != null) {
        // We have SDK, first operation simply passes path through
        return InstallOperation.wrap(installContext, new ReturnValue(), 0);
      }
    }
    return downloadAndUnzipSdkSeed(installContext, destination, progressRatio);
  }

  private static boolean isNonEmptyDirectory(File file) {
    String[] contents = !file.isDirectory() ? null : file.list(new FilenameFilter() {
      @Override
      public boolean accept(File dir, String name) {
        return !(name.equalsIgnoreCase(".DS_Store") || name.equalsIgnoreCase("thumbs.db") || name.equalsIgnoreCase("desktop.ini"));
      }
    });
    return contents != null && contents.length > 0;
  }

  @Override
  protected void init() {
    File location = myLocalHandler.getLocation();
    assert location != null;

    myState.put(WizardConstants.KEY_SDK_INSTALL_LOCATION, location.getAbsolutePath());

    myComponentTree = createComponentTree(myMode, myState, myMode.shouldCreateAvd());
    myComponentTree.init(myProgressStep);

    addStep(new SdkComponentsStep(myComponentTree, FirstRunWizard.KEY_CUSTOM_INSTALL, WizardConstants.KEY_SDK_INSTALL_LOCATION, myMode));

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

      addStep(new InstallSummaryStep(FirstRunWizard.KEY_CUSTOM_INSTALL, WizardConstants.KEY_SDK_INSTALL_LOCATION, supplier));
    }
  }

  @Override
  public void deriveValues(Set<ScopedStateStore.Key> modified) {
    super.deriveValues(modified);
    if (modified.contains(WizardConstants.KEY_SDK_INSTALL_LOCATION)) {
      String sdkPath = myState.get(WizardConstants.KEY_SDK_INSTALL_LOCATION);
      if (sdkPath != null) {
        File sdkLocation = new File(sdkPath);
        if (!FileUtil.filesEqual(myLocalHandler.getLocation(), sdkLocation)) {
          myLocalHandler = AndroidSdkHandler.getInstance(sdkLocation);
          myComponentInstaller = new ComponentInstaller(myLocalHandler);

          myComponentTree.updateState(myLocalHandler);
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
    final InstallOperation<File, File> initialize = createInitSdkOperation(installContext, destination, INIT_SDK_OPERATION_PROGRESS_SHARE);

    final Collection<? extends InstallableComponent> selectedComponents = myComponentTree.getChildrenToInstall();
    CheckSdkOperation checkSdk = new CheckSdkOperation(installContext);
    InstallComponentsOperation install =
      new InstallComponentsOperation(installContext, selectedComponents, myComponentInstaller, INSTALL_COMPONENTS_OPERATION_PROGRESS_SHARE);

    SetPreference setPreference = new SetPreference(myMode.getInstallerTimestamp());
    try {
      initialize.then(checkSdk).then(install).then(setPreference)
        .then(new ConfigureComponents(installContext, selectedComponents, myLocalHandler)).execute(destination);
    }
    catch (InstallationCancelledException e) {
      installContext.print("Android Studio setup was canceled", ConsoleViewContentType.ERROR_OUTPUT);
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
      AndroidVersion version = DetailsTypes.getAndroidVersion(platformDetails);
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

    return SdkLocationUtils.isWritable(myFileOp, new File(path));
  }

  private static class MergeOperation extends InstallOperation<File, File> {
    private final File myRepo;
    private final InstallContext myContext;
    private boolean myRepoWasMerged = false;

    public MergeOperation(File repo, InstallContext context, double progressRatio) {
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

  private static class MoveSdkOperation extends InstallOperation<File, File> {
    @NotNull private final File myDestination;

    public MoveSdkOperation(@NotNull InstallContext context, @NotNull File destination, double progressShare) {
      super(context, progressShare);
      myDestination = destination;
    }

    @NotNull
    @Override
    protected File perform(@NotNull ProgressIndicator indicator, @NotNull File file) throws WizardException {
      indicator.setText("Moving downloaded SDK");
      indicator.start();
      try {
        File root = getSdkRoot(file);
        if (!root.renameTo(myDestination)) {
          FileUtil.copyDir(root, myDestination);
          FileUtil.delete(root); // Failure to delete it is not critical, the source is in temp folder.
          // No need to abort installation.
        }
        return myDestination;
      }
      catch (IOException e) {
        throw new WizardException("Unable to move Android SDK", e);
      }
      finally {
        indicator.setFraction(1.0);
        indicator.stop();
      }
    }


    @Override
    public void cleanup(@NotNull File result) {
      // Do nothing
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
    @Nullable private final String myInstallerTimestamp;

    public SetPreference(@Nullable String installerTimestamp) {
      myInstallerTimestamp = installerTimestamp;
    }

    @Override
    public File apply(@Nullable final File input) {
      assert input != null;

      ApplicationUtils.invokeWriteActionAndWait(ModalityState.any(), new Runnable() {
        @Override
        public void run() {
          IdeSdks.setAndroidSdkPath(input, ProjectManager.getInstance().getDefaultProject());
          AndroidFirstRunPersistentData.getInstance().markSdkUpToDate(myInstallerTimestamp);
        }
      });

      return input;
    }
  }

  private static class ConfigureComponents implements Function<File, File> {
    private final InstallContext myInstallContext;
    private final Collection<? extends InstallableComponent> mySelectedComponents;
    private final AndroidSdkHandler mySdkHandler;

    public ConfigureComponents(InstallContext installContext,
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
