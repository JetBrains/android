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
package com.android.tools.idea.welcome;

import com.android.SdkConstants;
import com.android.sdklib.AndroidVersion;
import com.android.sdklib.SdkManager;
import com.android.sdklib.repository.descriptors.IPkgDesc;
import com.android.sdklib.repository.descriptors.PkgType;
import com.android.sdklib.repository.remote.RemotePkgInfo;
import com.android.tools.idea.sdk.DefaultSdks;
import com.android.tools.idea.sdk.SdkMerger;
import com.android.tools.idea.wizard.DynamicWizardPath;
import com.android.tools.idea.wizard.DynamicWizardStep;
import com.android.tools.idea.wizard.ScopedStateStore;
import com.android.tools.idea.wizard.WizardConstants;
import com.android.utils.NullLogger;
import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Function;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Lists;
import com.google.common.collect.Multimap;
import com.intellij.execution.ui.ConsoleViewContentType;
import com.intellij.openapi.application.Application;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.util.SystemInfo;
import com.intellij.openapi.util.io.FileUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Set;

/**
 * Wizard path that manages component installation flow. It will prompt the user
 * for the components to install and for install parameters. On wizard
 * completion it will download and unzip component archives and will
 * perform component setup.
 */
public class InstallComponentsPath extends DynamicWizardPath implements LongRunningOperationPath {
  public static final ScopedStateStore.Key<Boolean> KEY_CUSTOM_INSTALL =
    ScopedStateStore.createKey("custom.install", ScopedStateStore.Scope.PATH, Boolean.class);
  public static final AndroidVersion LATEST_ANDROID_VERSION = new AndroidVersion(21, null);
  private static final ScopedStateStore.Key<String> KEY_SDK_INSTALL_LOCATION =
    ScopedStateStore.createKey("download.sdk.location", ScopedStateStore.Scope.PATH, String.class);
  private final ProgressStep myProgressStep;
  @NotNull private final FirstRunWizardMode myMode;
  @Nullable private final Multimap<PkgType, RemotePkgInfo> myRemotePackages;
  @NotNull private final File mySdkLocation;
  private InstallableComponent[] myComponents;
  private InstallationTypeWizardStep myInstallationTypeWizardStep;
  private SdkComponentsStep mySdkComponentsStep;

  public InstallComponentsPath(@NotNull ProgressStep progressStep,
                               @NotNull FirstRunWizardMode mode,
                               @NotNull File sdkLocation,
                               @Nullable Multimap<PkgType, RemotePkgInfo> remotePackages) {
    myProgressStep = progressStep;
    myMode = mode;
    mySdkLocation = sdkLocation;
    myRemotePackages = remotePackages;
  }

  private static InstallableComponent[] createComponents(@NotNull FirstRunWizardMode reason, boolean createAvd) {
    AndroidSdk androidSdk = new AndroidSdk();
    List<InstallableComponent> components = Lists.newArrayList();
    components.add(androidSdk);
    if (Haxm.canRun() && reason == FirstRunWizardMode.NEW_INSTALL) {
      components.add(new Haxm(KEY_CUSTOM_INSTALL));
    }
    if (createAvd) {
      components.add(new AndroidVirtualDevice());
    }

    return components.toArray(new InstallableComponent[components.size()]);
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
  @VisibleForTesting
  static InstallOperation<File, File> downloadAndUnzipSdkSeed(@NotNull InstallContext context,
                                                              @NotNull File destination,
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

  private static boolean existsAndIsVisible(DynamicWizardStep step) {
    return step != null && step.isStepVisible();
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
    if (destination.isDirectory()) {
      SdkManager manager = SdkManager.createManager(destination.getAbsolutePath(), new NullLogger());
      if (manager != null) {
        // We have SDK, first operation simply passes path through
        return InstallOperation.wrap(installContext, new ReturnValue(), 0);
      }
    }
    return downloadAndUnzipSdkSeed(installContext, destination, progressRatio);
  }

  @Override
  protected void init() {
    boolean createAvd = myMode.shouldCreateAvd();
    if (myMode == FirstRunWizardMode.NEW_INSTALL) {
      myInstallationTypeWizardStep = new InstallationTypeWizardStep(KEY_CUSTOM_INSTALL);
      addStep(myInstallationTypeWizardStep);
    }
    myState.put(KEY_SDK_INSTALL_LOCATION, mySdkLocation.getAbsolutePath());

    myComponents = createComponents(myMode, createAvd);
    mySdkComponentsStep = new SdkComponentsStep(myComponents, KEY_CUSTOM_INSTALL, KEY_SDK_INSTALL_LOCATION, myMode);
    addStep(mySdkComponentsStep);

    for (InstallableComponent component : myComponents) {
      component.init(myState, myProgressStep);
      for (DynamicWizardStep step : component.createSteps()) {
        addStep(step);
      }
    }
    if (SystemInfo.isLinux && myMode != FirstRunWizardMode.INSTALL_HANDOFF) {
      addStep(new LinuxHaxmInfoStep());
    }
  }

  @Override
  public void deriveValues(Set<ScopedStateStore.Key> modified) {
    super.deriveValues(modified);
    if (modified.contains(KEY_CUSTOM_INSTALL) || modified.contains(KEY_SDK_INSTALL_LOCATION) || containsComponentVisibilityKey(modified)) {
      String sdkPath = myState.get(KEY_SDK_INSTALL_LOCATION);
      SdkManager manager = null;
      if (sdkPath != null) {
        manager = SdkManager.createManager(sdkPath, new NullLogger());
      }
      ArrayList<String> installIds = new ComponentInstaller(getSelectedComponents(), myRemotePackages).getPackagesToInstall(manager);
      final List<IPkgDesc> packages = getPackagesList(installIds);
      myState.put(WizardConstants.INSTALL_REQUESTS_KEY, packages);
    }
  }

  @NotNull
  private List<IPkgDesc> getPackagesList(@NotNull Collection<String> installIds) {
    if (myRemotePackages != null) {
      ImmutableSet<String> ids = ImmutableSet.copyOf(installIds);
      ImmutableList.Builder<IPkgDesc> packages = ImmutableList.builder();
      for (RemotePkgInfo remotePkgInfo : myRemotePackages.values()) {
        IPkgDesc desc = remotePkgInfo.getDesc();
        if (ids.contains(desc.getInstallId())) {
          packages.add(desc);
        }
      }
      return packages.build();
    }
    else {
      return ImmutableList.of();
    }
  }

  private boolean containsComponentVisibilityKey(@NotNull Set<ScopedStateStore.Key> modified) {
    if (myComponents == null) {
      return false;
    }
    else {
      for (InstallableComponent component : myComponents) {
        if (modified.contains(component.getKey())) {
          return true;
        }
      }
    }
    return false;
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

    final Collection<? extends InstallableComponent> selectedComponents = getSelectedComponents();
    InstallComponentsOperation install =
      new InstallComponentsOperation(installContext, selectedComponents, myRemotePackages, INSTALL_COMPONENTS_OPERATION_PROGRESS_SHARE);

    SetPreference setPreference = new SetPreference(myMode.getInstallerTimestamp());
    try {
      initialize.then(install).then(setPreference, 0).then(new ConfigureComponents(installContext, selectedComponents), 0)
        .execute(destination);
    }
    catch (InstallationCancelledException e) {
      // Ok, we don't abort the wizard as this was an explicit user action.
    }
  }

  private List<InstallableComponent> getSelectedComponents() {
    boolean customInstall = myState.getNotNull(KEY_CUSTOM_INSTALL, true);
    List<InstallableComponent> selectedOperations = Lists.newArrayListWithCapacity(myComponents.length);

    for (InstallableComponent component : myComponents) {
      if (!customInstall || myState.getNotNull(component.getKey(), true)) {
        selectedOperations.add(component);
      }
    }
    return selectedOperations;
  }

  @NotNull
  private File getDestination() throws WizardException {
    String destinationPath = myState.get(KEY_SDK_INSTALL_LOCATION);
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

  public boolean showsStep() {
    return isPathVisible() && (existsAndIsVisible(myInstallationTypeWizardStep) || existsAndIsVisible(mySdkComponentsStep));
  }

  private static class MergeOperation extends InstallOperation<File, File> {
    private final File myRepo;
    private final InstallContext myContext;

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
        }
        myContext.print(String.format("Android SDK was installed to %s", destination), ConsoleViewContentType.SYSTEM_OUTPUT);
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
      if (myRepo.exists()) {
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
        FileUtil.ensureExists(myDestination);
        if (!FileUtil.moveDirWithContent(getSdkRoot(file), myDestination)) {
          throw new WizardException("Unable to move Android SDK");
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
      final Application application = ApplicationManager.getApplication();
      // SDK can only be set from write action, write action can only be started from UI thread
      ApplicationManager.getApplication().invokeAndWait(new Runnable() {
        @Override
        public void run() {
          application.runWriteAction(new Runnable() {
            @Override
            public void run() {
              DefaultSdks.setDefaultAndroidHome(input, null);
              AndroidFirstRunPersistentData.getInstance().markSdkUpToDate(myInstallerTimestamp);
            }
          });
        }
      }, application.getAnyModalityState());
      return input;
    }
  }

  private static class ConfigureComponents implements Function<File, File> {
    private final InstallContext myInstallContext;
    private final Collection<? extends InstallableComponent> mySelectedComponents;

    public ConfigureComponents(InstallContext installContext, Collection<? extends InstallableComponent> selectedComponents) {
      myInstallContext = installContext;
      mySelectedComponents = selectedComponents;
    }

    @Override
    public File apply(@Nullable File input) {
      assert input != null;
      for (InstallableComponent component : mySelectedComponents) {
        component.configure(myInstallContext, input);
      }
      return input;
    }
  }
}
