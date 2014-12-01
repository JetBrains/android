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
import com.android.sdklib.repository.FullRevision;
import com.android.sdklib.repository.NoPreviewRevision;
import com.android.sdklib.repository.descriptors.IPkgDesc;
import com.android.sdklib.repository.descriptors.IdDisplay;
import com.android.sdklib.repository.descriptors.PkgDesc;
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
import com.google.common.collect.*;
import com.intellij.execution.ui.ConsoleViewContentType;
import com.intellij.openapi.application.Application;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.util.SystemInfo;
import com.intellij.openapi.util.ThrowableComputable;
import com.intellij.openapi.util.io.FileUtil;
import org.jetbrains.annotations.Contract;
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
  private InstallableComponent[] myComponents;
  @NotNull private final File mySdkLocation;
  private InstallationTypeWizardStep myInstallationTypeWizardStep;
  private SdkComponentsStep mySdkComponentsStep;

  public InstallComponentsPath(@NotNull ProgressStep progressStep, @NotNull FirstRunWizardMode mode,
                               @NotNull File sdkLocation, @Nullable Multimap<PkgType, RemotePkgInfo> remotePackages) {
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

  @VisibleForTesting
  static boolean downloadAndUnzipSdkSeed(@NotNull InstallContext context, @NotNull File destination, double progressShare)
    throws WizardException {
    final double DOWNLOAD_OPERATION_PROGRESS_SHARE = progressShare * 0.8;
    final double UNZIP_OPERATION_PROGRESS_SHARE = progressShare - DOWNLOAD_OPERATION_PROGRESS_SHARE;

    File file = new DownloadOperation(context, FirstRunWizardDefaults.getSdkDownloadUrl(), DOWNLOAD_OPERATION_PROGRESS_SHARE).execute();
    try {
      return unzip(context, file, destination, UNZIP_OPERATION_PROGRESS_SHARE);
    }
    finally {
      if (file != null && file.isFile() && file.getAbsolutePath().startsWith(context.getTempDirectory().getAbsolutePath())) {
        FileUtil.delete(file);
      }
    }
  }

  private static boolean unzip(@NotNull InstallContext context, @Nullable File archive, @NotNull File destination, double progressShare)
    throws WizardException {
    if (archive == null) {
      return false;
    }
    try {
      FileUtil.ensureExists(destination.getParentFile());
      File unpacked = new UnpackOperation(context, archive, progressShare).execute();
      if (unpacked != null) {
        try {
          if (!destination.mkdirs() && !FileUtil.moveDirWithContent(getSdkRoot(unpacked), destination)) {
            throw new WizardException("Unable to prepare Android SDK");
          }
        }
        finally {
          if (unpacked.isDirectory()) {
            FileUtil.delete(unpacked);
          }
        }
        return true;
      }
    }
    catch (IOException e) {
      throw new WizardException("Unable to prepare Android SDK", e);
    }
    return false;
  }

  @Nullable
  @Contract("false, _, _ -> null;true, _, _ -> !null")
  public static IPkgDesc createExtra(boolean shouldInstallFlag, IdDisplay vendor, String path) {
    if (!shouldInstallFlag) {
      return null;
    }
    return PkgDesc.Builder.newExtra(vendor, path, "", null,
                                    new NoPreviewRevision(FullRevision.MISSING_MAJOR_REV)).create();
  }

  private static File mergeRepoIntoDestination(final InstallContext context,
                                               @NotNull final File repo,
                                               @NotNull final File destination,
                                               double progressRatio) throws WizardException {
    try {
      return context.run(new MergeOperation(destination, repo, context), progressRatio);
    }
    catch (IOException e) {
      throw new WizardException(e.getMessage(), e);
    }
  }

  private static boolean existsAndIsVisible(DynamicWizardStep step) {
    return step != null && step.isStepVisible();
  }

  @VisibleForTesting
  void setupSdkComponents(@NotNull InstallContext installContext,
                          @NotNull File sdk,
                          @NotNull Collection<? extends InstallableComponent> selectedComponents,
                          @Nullable Multimap<PkgType, RemotePkgInfo> remotePackages,
                          double progressRatio) throws WizardException {
    new InstallComponentsOperation(selectedComponents, remotePackages).run(installContext, sdk, progressRatio);
    for (InstallableComponent component : selectedComponents) {
      component.configure(installContext, sdk);
    }
  }

  private static void setSdkInPreferences(final File sdk) {
    final Application application = ApplicationManager.getApplication();
    // SDK can only be set from write action, write action can only be started from UI thread
    ApplicationManager.getApplication().invokeAndWait(new Runnable() {
      @Override
      public void run() {
        application.runWriteAction(new Runnable() {
          @Override
          public void run() {
            DefaultSdks.setDefaultAndroidHome(sdk, null);
            AndroidFirstRunPersistentData.getInstance().markSdkUpToDate();
          }
        });
      }
    }, application.getAnyModalityState());
  }

  @Nullable
  private static File getHandoffAndroidSdkSource() {
    InstallerData data = InstallerData.get();
    if (data == null) {
      return null;
    }
    File androidSrc = data.getAndroidSrc();
    if (androidSrc != null) {
      File[] files = androidSrc.listFiles();
      if (androidSrc.isDirectory() && files != null && files.length > 0) {
        return androidSrc;
      }
    }
    return null;
  }

  @Override
  protected void init() {
    boolean createAvd = true;
    if (myMode == FirstRunWizardMode.NEW_INSTALL) {
      myInstallationTypeWizardStep = new InstallationTypeWizardStep(KEY_CUSTOM_INSTALL, myMode);
      addStep(myInstallationTypeWizardStep);
    }
    else if (myMode == FirstRunWizardMode.INSTALL_HANDOFF) {
      InstallerData data = InstallerData.get();
      assert data != null;
      createAvd = data.shouldCreateAvd();
    }
    myState.put(KEY_SDK_INSTALL_LOCATION, mySdkLocation.getAbsolutePath());

    myComponents = createComponents(myMode, createAvd);
    mySdkComponentsStep = new SdkComponentsStep(myComponents, KEY_CUSTOM_INSTALL, KEY_SDK_INSTALL_LOCATION);
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
      ArrayList<String> installIds =
        new InstallComponentsOperation(getSelectedComponents(), myRemotePackages).getPackagesToDownload(manager);
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

    InstallContext installContext = new InstallContext(createTempDir(), myProgressStep);
    final File sdk = initializeSdk(installContext, INIT_SDK_OPERATION_PROGRESS_SHARE);
    if (sdk != null) {
      setupSdkComponents(installContext, sdk, getSelectedComponents(), myRemotePackages, INSTALL_COMPONENTS_OPERATION_PROGRESS_SHARE);
      setSdkInPreferences(sdk);
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

  /**
   * @return SDK location or <code>null</code> if the operation was cancelled
   */
  @Nullable
  private File initializeSdk(InstallContext installContext, double progressRatio) throws WizardException {
    String destinationPath = myState.get(KEY_SDK_INSTALL_LOCATION);
    assert destinationPath != null;
    final File destination = new File(destinationPath);
    if (destination.isFile()) {
      throw new WizardException(String.format("Path %s does not point to a directory", destination));
    }
    else if (destination.isDirectory()) {
      SdkManager manager = SdkManager.createManager(destination.getAbsolutePath(), new NullLogger());
      if (manager != null) {
        installContext.advance(progressRatio);
        // We got ourselves an SDK
        return destination;
      }
    }
    File handoffSource = getHandoffAndroidSdkSource();
    if (handoffSource == null) {
      return downloadAndUnzipSdkSeed(installContext, destination, progressRatio) ? destination : null;
    }
    else {
      return mergeRepoIntoDestination(installContext, handoffSource, destination, progressRatio);
    }
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

  private static class MergeOperation implements ThrowableComputable<File, IOException> {
    private final File myDestination;
    private final File myRepo;
    private final InstallContext myContext;

    public MergeOperation(File destination, File repo, InstallContext context) {
      myDestination = destination;
      myRepo = repo;
      myContext = context;
    }

    @Override
    public File compute() throws IOException {
      ProgressIndicator indicator = ProgressManager.getInstance().getProgressIndicator();
      indicator.setText("Installing Android SDK");
      indicator.setIndeterminate(true);
      FileUtil.ensureExists(myDestination);
      if (!FileUtil.filesEqual(myDestination.getCanonicalFile(), myRepo.getCanonicalFile())) {
        SdkMerger.mergeSdks(myRepo, myDestination, indicator);
      }
      myContext.print(String.format("Android SDK was installed to %s", myDestination), ConsoleViewContentType.SYSTEM_OUTPUT);
      return myDestination;
    }
  }

}
