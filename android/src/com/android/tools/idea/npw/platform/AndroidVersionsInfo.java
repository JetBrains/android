/*
 * Copyright (C) 2016 The Android Open Source Project
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
package com.android.tools.idea.npw.platform;

import com.android.SdkConstants;
import com.android.annotations.VisibleForTesting;
import com.android.ide.common.repository.GradleCoordinate;
import com.android.ide.common.repository.SdkMavenRepository;
import com.android.repository.Revision;
import com.android.repository.api.*;
import com.android.repository.impl.meta.RepositoryPackages;
import com.android.repository.impl.meta.TypeDetails;
import com.android.sdklib.*;
import com.android.sdklib.repository.AndroidSdkHandler;
import com.android.sdklib.repository.IdDisplay;
import com.android.sdklib.repository.meta.DetailsTypes;
import com.android.sdklib.repository.targets.AndroidTargetManager;
import com.android.sdklib.repository.targets.SystemImage;
import com.android.tools.idea.npw.FormFactor;
import com.android.tools.idea.sdk.*;
import com.android.tools.idea.sdk.progress.StudioLoggerProgressIndicator;
import com.android.tools.idea.sdk.progress.StudioProgressRunner;
import com.android.tools.idea.sdk.wizard.SdkQuickfixUtils;
import com.android.tools.idea.templates.TemplateMetadata;
import com.android.tools.idea.templates.TemplateUtils;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Lists;
import com.google.common.collect.Sets;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ModalityState;
import org.jetbrains.android.sdk.AndroidSdkUtils;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.util.*;
import java.util.stream.Collectors;

import static com.android.sdklib.SdkVersionInfo.HIGHEST_KNOWN_API;
import static com.android.sdklib.SdkVersionInfo.HIGHEST_KNOWN_STABLE_API;
import static com.android.tools.idea.gradle.npw.project.GradleBuildSettings.getRecommendedBuildToolsRevision;
import static com.intellij.openapi.util.text.StringUtil.isEmptyOrSpaces;

/**
 * Lists the available Android Versions from local, remote, and statically-defined sources.
 * The list can be filtered by min sdk level and a callback mechanism allows information to be provided asynchronously.
 * It is also possible to query the list of packages that the system needs to install to satisfy the requirements of an API level.
 */
public class AndroidVersionsInfo {

  /**
   * Call back interface to notify the caller that the requested items were loaded.
   * @see AndroidVersionsInfo#loadTargetVersions(FormFactor, int, ItemsLoaded)
   */
  public interface ItemsLoaded {
    void onDataLoadedFinished(List<VersionItem> items);
  }

  private static final ProgressIndicator REPO_LOG = new StudioLoggerProgressIndicator(AndroidVersionsInfo.class);
  private static final IdDisplay NO_MATCH = IdDisplay.create("no_match", "No Match");

  private final List<VersionItem> myTargetVersions = Lists.newArrayList(); // All versions that we know about
  private final Set<AndroidVersion> myInstalledVersions = Sets.newHashSet();
  private IAndroidTarget myHighestInstalledApiTarget;

  public void load() {
    loadTargetVersions();
    loadInstalledVersions();
  }

  /**
   * Load the installed android versions from the installed SDK
   */
  private void loadInstalledVersions() {
    myInstalledVersions.clear();

    IAndroidTarget highestInstalledTarget = null;
    for (IAndroidTarget target : getCompilationTargets()) {
      if (target.isPlatform() && target.getVersion().getFeatureLevel() >= SdkVersionInfo.LOWEST_COMPILE_SDK_VERSION &&
          (highestInstalledTarget == null ||
           target.getVersion().getFeatureLevel() > highestInstalledTarget.getVersion().getFeatureLevel() &&
           !target.getVersion().isPreview())) {
        highestInstalledTarget = target;
      }
      if (target.getVersion().isPreview() || !target.getAdditionalLibraries().isEmpty()) {
        myInstalledVersions.add(target.getVersion());
      }
    }
    myHighestInstalledApiTarget = highestInstalledTarget;
  }

  @Nullable ("If we don't know (yet) the highest installed version")
  AndroidVersion getHighestInstalledVersion() {
    return  (myHighestInstalledApiTarget == null) ? null : myHighestInstalledApiTarget.getVersion();
  }

  @Nullable
  public static File getSdkManagerLocalPath() {
    return IdeSdks.getInstance().getAndroidSdkPath();
  }

  @NotNull
  public List<UpdatablePackage> loadInstallPackageList(@NotNull List<AndroidVersionsInfo.VersionItem> installItems) {
    Set<String> requestedPaths = Sets.newHashSet();
    AndroidSdkHandler sdkHandler = AndroidSdks.getInstance().tryToChooseSdkHandler();

    // TODO: remove once maven dependency downloading is available in studio
    GradleCoordinate constraintCoordinate = GradleCoordinate.parseCoordinateString(SdkConstants.CONSTRAINT_LAYOUT_LIB_ARTIFACT + ":+");
    RepositoryPackages packages = sdkHandler.getSdkManager(REPO_LOG).getPackages();
    RepoPackage constraintPackage = SdkMavenRepository.findBestPackageMatching(constraintCoordinate, packages.getLocalPackages().values());
    if (constraintPackage == null) {
      constraintPackage = SdkMavenRepository.findBestPackageMatching(constraintCoordinate, packages.getRemotePackages().values());
      if (constraintPackage != null) {
        requestedPaths.add(constraintPackage.getPath());
      }
    }

    // Install build tools, if not already installed
    requestedPaths.add(DetailsTypes.getBuildToolsPath(getRecommendedBuildToolsRevision(sdkHandler, REPO_LOG)));

    for (VersionItem versionItem : installItems) {
      AndroidVersion androidVersion = versionItem.myAndroidVersion;
      String platformPath = DetailsTypes.getPlatformPath(androidVersion);

      // Check to see if this is installed. If not, request that we install it
      if (versionItem.myAddon != null) {
        // The user selected a non platform SDK (e.g. for Google Glass). Let us install it:
        requestedPaths.add(versionItem.myAddon.getPath());

        // We also need the platform if not already installed:
        AndroidTargetManager targetManager = sdkHandler.getAndroidTargetManager(REPO_LOG);
        if (targetManager.getTargetFromHashString(AndroidTargetHash.getPlatformHashString(androidVersion), REPO_LOG) == null) {
          requestedPaths.add(platformPath);
        }
      }
      else {
        // TODO: If the user has no APIs installed that are at least of api level LOWEST_COMPILE_SDK_VERSION,
        // then we request (for now) to install HIGHEST_KNOWN_STABLE_API.
        // Instead, we should choose to install the highest stable API possible. However, users having no SDK at all installed is pretty
        // unlikely, so this logic can wait for a followup CL.
        if (myHighestInstalledApiTarget == null ||
            (androidVersion.getApiLevel() > myHighestInstalledApiTarget.getVersion().getApiLevel() &&
             !myInstalledVersions.contains(androidVersion))) {

          // Let us install the HIGHEST_KNOWN_STABLE_API.
          requestedPaths.add(DetailsTypes.getPlatformPath(new AndroidVersion(HIGHEST_KNOWN_STABLE_API, null)));
        }
      }
    }

    return getPackageList(requestedPaths, sdkHandler);
  }

  /**
   * Get the list of versions, notably by populating the available values from local, remote, and statically-defined sources.
   */
  public void loadTargetVersions(@NotNull FormFactor formFactor, int minSdkLevel, ItemsLoaded itemsLoadedCallback) {
    List<VersionItem> versionItemList = new ArrayList<>();

    for (VersionItem target : myTargetVersions) {
      if (isFormFactorAvailable(formFactor, minSdkLevel, target.getApiLevel())
          || (target.getAndroidTarget() != null && target.getAndroidTarget().getVersion().isPreview())) {
        versionItemList.add(target);
      }
    }

    loadRemoteTargets(formFactor, minSdkLevel, versionItemList, itemsLoadedCallback);
  }

  /**
   * Load the definitions of the android compilation targets
   */
  private void loadTargetVersions() {
    myTargetVersions.clear();

    if (AndroidSdkUtils.isAndroidSdkAvailable()) {
      String[] knownVersions = TemplateUtils.getKnownVersions();
      for (int i = 0; i < knownVersions.length; i++) {
        myTargetVersions.add(new VersionItem(knownVersions[i], i + 1));
      }
    }

    for (IAndroidTarget target : getCompilationTargets()) {
      if (target.getVersion().isPreview() || !target.getAdditionalLibraries().isEmpty()) {
        myTargetVersions.add(new VersionItem(target));
      }
    }
  }

  /**
   * @return a list of android compilation targets (platforms and add-on SDKs)
   */
  @NotNull
  private static IAndroidTarget[] getCompilationTargets() {
    AndroidTargetManager targetManager = AndroidSdks.getInstance().tryToChooseSdkHandler().getAndroidTargetManager(REPO_LOG);
    List<IAndroidTarget> result = Lists.newArrayList();
    for (IAndroidTarget target : targetManager.getTargets(REPO_LOG)) {
      if (target.isPlatform() || !target.getAdditionalLibraries().isEmpty()) {
        result.add(target);
      }
    }
    return result.toArray(new IAndroidTarget[result.size()]);
  }

  private void loadRemoteTargets(@NotNull FormFactor myFormFactor, int minSdkLevel, @NotNull List<VersionItem> versionItemList,
                                        ItemsLoaded completedCallback) {
    AndroidSdkHandler sdkHandler = AndroidSdks.getInstance().tryToChooseSdkHandler();

    final Runnable runCallbacks = () -> {
      if (completedCallback != null) {
        completedCallback.onDataLoadedFinished(versionItemList);
      }
    };

    RepoManager.RepoLoadedCallback onComplete = packages ->
      ApplicationManager.getApplication().invokeLater(() -> {
        addPackages(myFormFactor, versionItemList, packages.getNewPkgs(), minSdkLevel);
        addOfflineLevels(myFormFactor, versionItemList);
        runCallbacks.run();
      }, ModalityState.any());

    // We need to pick up addons that don't have a target created due to the base platform not being installed.
    RepoManager.RepoLoadedCallback onLocalComplete =
      packages ->
        ApplicationManager.getApplication().invokeLater(
          () -> addPackages(myFormFactor, versionItemList, packages.getLocalPackages().values(), minSdkLevel),
          ModalityState.any());

    Runnable onError = () -> ApplicationManager.getApplication().invokeLater(() -> {
      addOfflineLevels(myFormFactor, versionItemList);
      runCallbacks.run();
    }, ModalityState.any());

    StudioProgressRunner runner = new StudioProgressRunner(false, false, "Refreshing Targets", null);
    sdkHandler.getSdkManager(REPO_LOG).load(
      RepoManager.DEFAULT_EXPIRATION_PERIOD_MS,
      ImmutableList.of(onLocalComplete), ImmutableList.of(onComplete), ImmutableList.of(onError),
      runner, new StudioDownloader(), StudioSettingsController.getInstance(), false);
  }

  @NotNull
  private static List<UpdatablePackage> getPackageList(@NotNull Collection<String> requestedPaths,
                                                       @NotNull AndroidSdkHandler sdkHandler) {
    List<UpdatablePackage> requestedPackages = new ArrayList<>();
    RepositoryPackages packages = sdkHandler.getSdkManager(REPO_LOG).getPackages();
    Map<String, UpdatablePackage> consolidated = packages.getConsolidatedPkgs();
    for (String path : requestedPaths) {
      UpdatablePackage p = consolidated.get(path);
      if (p != null && p.hasRemote()) {
        requestedPackages.add(p);
      }
    }

    List<UpdatablePackage> resolvedPackages = new ArrayList<>();
    try {
      resolvedPackages = SdkQuickfixUtils.resolve(requestedPackages, packages);
    }
    catch (SdkQuickfixUtils.PackageResolutionException e) {
      REPO_LOG.logError("Error Resolving Packages", e);
    }

    return resolvedPackages;
  }

  private static boolean filterPkgDesc(@NotNull RepoPackage p, @NotNull FormFactor formFactor, int minSdkLevel) {
    return isApiType(p) && doFilter(formFactor, minSdkLevel, getTag(p), getFeatureLevel(p));
  }

  private static boolean doFilter(@NotNull FormFactor formFactor, int minSdkLevel, @Nullable IdDisplay tag, int targetSdkLevel) {
    return formFactor.isSupported(tag, targetSdkLevel) && targetSdkLevel >= minSdkLevel;
  }

  private static boolean isApiType(@NotNull RepoPackage repoPackage) {
    return repoPackage.getTypeDetails() instanceof DetailsTypes.ApiDetailsType;
  }

  private static int getFeatureLevel(@NotNull RepoPackage repoPackage) {
    return getAndroidVersion(repoPackage).getFeatureLevel();
  }

  private static boolean isFormFactorAvailable(@NotNull FormFactor formFactor, int minSdkLevel, int targetSdkLevel) {
    return doFilter(formFactor,  minSdkLevel, SystemImage.DEFAULT_TAG, targetSdkLevel);
  }

  @NotNull
  private static AndroidVersion getAndroidVersion(@NotNull RepoPackage repoPackage) {
    TypeDetails details = repoPackage.getTypeDetails();
    if (details instanceof DetailsTypes.ApiDetailsType) {
      return ((DetailsTypes.ApiDetailsType)details).getAndroidVersion();
    }
    throw new RuntimeException("Could not determine version");
  }

  /**
   * Return the tag for the specified repository package.
   * We are only interested in 2 package types.
   */
  @Nullable
  private static IdDisplay getTag(@NotNull RepoPackage repoPackage) {
    TypeDetails details = repoPackage.getTypeDetails();
    IdDisplay tag = NO_MATCH;
    if (details instanceof DetailsTypes.AddonDetailsType) {
      tag = ((DetailsTypes.AddonDetailsType)details).getTag();
    }
    if (details instanceof DetailsTypes.SysImgDetailsType) {
      DetailsTypes.SysImgDetailsType imgDetailsType = (DetailsTypes.SysImgDetailsType)details;
      if (imgDetailsType.getAbi().equals(SdkConstants.CPU_ARCH_INTEL_ATOM)) {
        tag = imgDetailsType.getTag();
      }
    }
    return tag;
  }

  private void addPackages(@NotNull FormFactor myFormFactor, @NotNull List<VersionItem> versionItemList,
                                  @NotNull Collection<? extends RepoPackage> packages, int minSdkLevel) {

    List<RepoPackage> sorted = packages.stream()
      .filter(repoPackage -> repoPackage != null && filterPkgDesc(repoPackage, myFormFactor, minSdkLevel))
      .collect(Collectors.toList());

    sorted.sort(Comparator.comparing(AndroidVersionsInfo::getAndroidVersion));

    int existingApiLevel = -1;
    int prevInsertedApiLevel = -1;
    int index = -1;
    for (RepoPackage info : sorted) {
      int apiLevel = getFeatureLevel(info);
      while (apiLevel > existingApiLevel) {
        existingApiLevel = ++index < versionItemList.size() ? versionItemList.get(index).myApiLevel : Integer.MAX_VALUE;
      }
      if (apiLevel != existingApiLevel && apiLevel != prevInsertedApiLevel) {
        versionItemList.add(index++, new VersionItem(info));
        prevInsertedApiLevel = apiLevel;
      }
    }
  }

  private void addOfflineLevels(@NotNull FormFactor myFormFactor, @NotNull List<VersionItem> versionItemList) {
    int existingApiLevel = -1;
    int prevInsertedApiLevel = -1;
    int index = -1;
    for (int apiLevel = myFormFactor.getMinOfflineApiLevel(); apiLevel <= myFormFactor.getMaxOfflineApiLevel(); apiLevel++) {
      if (myFormFactor.isSupported(null, apiLevel) || apiLevel <= 0) {
        continue;
      }
      while (apiLevel > existingApiLevel) {
        existingApiLevel = ++index < versionItemList.size() ? versionItemList.get(index).myApiLevel : Integer.MAX_VALUE;
      }
      if (apiLevel != existingApiLevel && apiLevel != prevInsertedApiLevel) {
        versionItemList.add(index++, new VersionItem(apiLevel));
        prevInsertedApiLevel = apiLevel;
      }
    }
  }

  public class VersionItem {
    private final AndroidVersion myAndroidVersion;
    private final String myLabel;
    private final int myApiLevel;
    private final String myApiLevelStr; // Can be a number or a Code Name (eg "L", "N", etc)

    private IAndroidTarget myAndroidTarget;
    private RemotePackage myAddon;

    VersionItem(@NotNull AndroidVersion androidVersion, @NotNull IdDisplay tag, @Nullable IAndroidTarget target) {
      myAndroidVersion = androidVersion;
      myLabel = getLabel(androidVersion, tag, target);
      myAndroidTarget = target;
      myApiLevel = androidVersion.getFeatureLevel();
      myApiLevelStr = androidVersion.getApiString();
    }

    VersionItem(@NotNull String label, int apiLevel) {
      myAndroidVersion = new AndroidVersion(apiLevel, null);
      myLabel = label;
      myApiLevel = apiLevel;
      myApiLevelStr = Integer.toString(apiLevel);
    }

    VersionItem(int apiLevel) {
      this(new AndroidVersion(apiLevel, null), SystemImage.DEFAULT_TAG, null);
    }

    @VisibleForTesting
    public VersionItem(@NotNull IAndroidTarget target) {
      this(target.getVersion(), SystemImage.DEFAULT_TAG, target);
    }

    VersionItem(@NotNull RepoPackage info) {
      this(getAndroidVersion(info), getTag(info), null);
      if (info instanceof RemotePackage && SystemImage.GLASS_TAG.equals(getTag(info))) {
        // If this is Glass then prepare to install this add-on package.
        // All platform are installed by a different mechanism.
        myAddon = (RemotePackage)info;
      }
    }

    @Nullable ("null except for preview releases")
    public IAndroidTarget getAndroidTarget() {
      return myAndroidTarget;
    }

    @Nullable ("null except when an addon needs to be installed (eg Google Glass)")
    public RemotePackage getAddon() {
      // TODO: We may no longer need this, as we only show Google Glass if we have it already installed
      return myAddon;
    }

    public int getApiLevel() {
      return myApiLevel;
    }

    @NotNull
    public String getApiLevelStr() {
      return myApiLevelStr;
    }

    public int getBuildApiLevel() {
      int apiLevel;
      if (myAddon != null) {
        apiLevel = myApiLevel;
      }
      else if (myAndroidTarget == null) {
        apiLevel = HIGHEST_KNOWN_STABLE_API;
      }
      else if (myAndroidTarget.getVersion().isPreview() || !myAndroidTarget.isPlatform()) {
        apiLevel = myApiLevel;
      }
      else  {
        apiLevel = getHighestInstalledVersion() == null ? HIGHEST_KNOWN_STABLE_API : getHighestInstalledVersion().getFeatureLevel();
      }
      return apiLevel;
    }

    @NotNull
    public String getBuildApiLevelStr() {
      if (myAndroidTarget == null) {
        return Integer.toString(getBuildApiLevel());
      }
      if (myAndroidTarget.isPlatform()) {
        return TemplateMetadata.getBuildApiString(myAndroidTarget.getVersion());
      }
      return AndroidTargetHash.getTargetHashString(myAndroidTarget);
    }

    public int getTargetApiLevel() {
      int buildApiLevel = getBuildApiLevel();
      if (buildApiLevel >= HIGHEST_KNOWN_API || (myAndroidTarget != null && myAndroidTarget.getVersion().isPreview())) {
        return buildApiLevel;
      }

      return getHighestInstalledVersion() == null ? HIGHEST_KNOWN_API  : getHighestInstalledVersion().getApiLevel();
    }

    @NotNull
    public String getTargetApiLevelStr() {
      int buildApiLevel = getBuildApiLevel();
      if (buildApiLevel >= HIGHEST_KNOWN_API || (myAndroidTarget != null && myAndroidTarget.getVersion().isPreview())) {
        return myAndroidTarget == null ? Integer.toString(buildApiLevel) : myAndroidTarget.getVersion().getApiString();
      }

      return getHighestInstalledVersion() == null ? Integer.toString(HIGHEST_KNOWN_API) : getHighestInstalledVersion().getApiString();
    }

    @NotNull
    public String getLabel() {
      return myLabel;
    }

    @NotNull
    private String getLabel(@NotNull AndroidVersion version, @Nullable IdDisplay tag, @Nullable IAndroidTarget target) {
      int featureLevel = version.getFeatureLevel();
      if (SystemImage.GLASS_TAG.equals(tag)) {
        return String.format("Glass Development Kit Preview (API %1$d)", featureLevel);
      }
      if (featureLevel <= HIGHEST_KNOWN_API) {
        if (version.isPreview()) {
          return String.format("API %1$s: Android %2$s (%3$s preview)",
                               SdkVersionInfo.getCodeName(featureLevel),
                               SdkVersionInfo.getVersionString(featureLevel),
                               SdkVersionInfo.getCodeName(featureLevel));
        }
        else if (target == null || target.isPlatform()){
          return SdkVersionInfo.getAndroidName(featureLevel);
        }
        else if (!isEmptyOrSpaces(target.getDescription())) {
          return String.format("API %1$d: %2$s", featureLevel, target.getDescription());
        }
        else {
          return AndroidTargetHash.getTargetHashString(target);
        }
      }
      else {
        if (version.isPreview()) {
          return String.format("API %1$d: Android (%2$s)", featureLevel, version.getCodename());
        }
        else {
          return String.format("API %1$d: Android", featureLevel);
        }
      }
    }

    @Override
    public String toString() {
      return myLabel;
    }
  }
}