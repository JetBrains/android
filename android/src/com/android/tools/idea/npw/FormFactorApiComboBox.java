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
package com.android.tools.idea.npw;

import com.android.repository.api.ProgressIndicator;
import com.android.repository.api.RemotePackage;
import com.android.repository.api.RepoManager;
import com.android.repository.api.RepoPackage;
import com.android.repository.impl.meta.RepositoryPackages;
import com.android.sdklib.AndroidTargetHash;
import com.android.sdklib.AndroidVersion;
import com.android.sdklib.IAndroidTarget;
import com.android.sdklib.SdkVersionInfo;
import com.android.sdklib.repository.AndroidSdkHandler;
import com.android.sdklib.repository.IdDisplay;
import com.android.sdklib.repository.meta.DetailsTypes;
import com.android.sdklib.repository.targets.AndroidTargetManager;
import com.android.sdklib.repository.targets.SystemImage;
import com.android.tools.idea.sdk.StudioDownloader;
import com.android.tools.idea.sdk.StudioSettingsController;
import com.android.tools.idea.sdk.progress.StudioLoggerProgressIndicator;
import com.android.tools.idea.sdk.progress.StudioProgressRunner;
import com.android.tools.idea.templates.TemplateMetadata;
import com.android.tools.idea.templates.TemplateUtils;
import com.android.tools.idea.ui.ApiComboBoxItem;
import com.android.tools.idea.wizard.dynamic.ScopedDataBinder;
import com.android.tools.idea.wizard.dynamic.ScopedStateStore;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Iterables;
import com.google.common.collect.Lists;
import com.google.common.collect.Sets;
import com.intellij.ide.util.PropertiesComponent;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ModalityState;
import com.intellij.openapi.projectRoots.JavaSdk;
import com.intellij.openapi.projectRoots.JavaSdkVersion;
import com.intellij.openapi.projectRoots.ProjectJdkTable;
import com.intellij.openapi.projectRoots.Sdk;
import org.jetbrains.android.sdk.AndroidSdkData;
import org.jetbrains.android.sdk.AndroidSdkUtils;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.event.ActionListener;
import java.util.*;

import static com.android.tools.idea.wizard.WizardConstants.INSTALL_REQUESTS_KEY;
import static com.android.tools.idea.wizard.dynamic.ScopedStateStore.Key;

/**
* A labeled combo box of SDK options for a given FormFactor.
*/
public final class FormFactorApiComboBox extends JComboBox {

  // Set of installed targets and versions. TODO: These fields should not be static; that causes
  // the versions to not stay up to date when new versions are installed.  In the constructor
  // we've removed the lazy evaluation, so for every new FormFactorApiComboBox that we construct,
  // this (shared) list is reinitialized. Ideally we'd make these instance lists, but there's
  // some control flow now where a wizard page updates these lists, so without making bigger
  // changes to the code we'll leave it this way for now.
  private static final Set<AndroidVersion> ourInstalledVersions = Sets.newHashSet();
  private static final List<AndroidTargetComboBoxItem> ourTargets = Lists.newArrayList();
  private static IAndroidTarget ourHighestInstalledApiTarget;

  private FormFactor myFormFactor;

  private final List<String> myInstallRequests = Lists.newArrayList();
  private Key<String> myBuildApiKey;
  private Key<Integer> myBuildApiLevelKey;
  private Key<Integer> myTargetApiLevelKey;
  private Key<String> myTargetApiStringKey;
  private Key<AndroidTargetComboBoxItem> myTargetComboBoxKey;
  private Key<Boolean> myInclusionKey;

  private static final ProgressIndicator REPO_LOG = new StudioLoggerProgressIndicator(FormFactorApiComboBox.class);

  /**
   * Initializes this component, notably by populating the available values from local, remote, and statically-defined sources.
   *
   * @param formFactor         The form factor for which we're showing available api levels
   * @param minSdkLevel        The minimum sdk level we should show.
   * @param completedCallback  A Runnable that will be run when we've finished looking for items (with success or failure).
   * @param foundItemsCallback A Runnable that will be run once we've determined that there are available items to show.
   * @param noItemsCallback    A Runnable that will be run when we've finished looking for items without finding any.
   */
  public void init(@NotNull FormFactor formFactor, int minSdkLevel, @Nullable Runnable completedCallback,
                   @Nullable Runnable foundItemsCallback, @Nullable Runnable noItemsCallback) {
    myFormFactor = formFactor;

    // These target lists used to be initialized just once. However, that resulted
    // in a bug where after installing new targets, it keeps believing the new targets
    // to not be available and requiring another install. We should just compute them
    // once - it's not an expensive operation (calling both takes 1-2 ms.)
    loadTargets();
    loadInstalledVersions();

    myBuildApiKey = FormFactorUtils.getBuildApiKey(formFactor);
    myBuildApiLevelKey = FormFactorUtils.getBuildApiLevelKey(formFactor);
    myTargetApiLevelKey = FormFactorUtils.getTargetApiLevelKey(formFactor);
    myTargetApiStringKey = FormFactorUtils.getTargetApiStringKey(formFactor);
    myTargetComboBoxKey = FormFactorUtils.getTargetComboBoxKey(formFactor);
    myInclusionKey = FormFactorUtils.getInclusionKey(formFactor);
    populateComboBox(formFactor, minSdkLevel);
    if (getItemCount() > 0) {
      if (foundItemsCallback != null) {
        foundItemsCallback.run();
      }
    }
    loadSavedApi();
    loadRemoteTargets(minSdkLevel, completedCallback, foundItemsCallback, noItemsCallback);
  }

  /**
   * Registers this component with the given ScopedDataBinder.
   */
  public void registerWith(@NotNull ScopedDataBinder binder) {
    assert myFormFactor != null : "register() called on FormFactorApiComboBox before init()";
    binder.register(FormFactorUtils.getTargetComboBoxKey(myFormFactor), this, TARGET_COMBO_BINDING);
  }

  /**
   * Load the saved value for this ComboBox
   */
  public void loadSavedApi() {
    // Check for a saved value for the min api level
    String savedApiLevel = PropertiesComponent.getInstance().getValue(FormFactorUtils.getPropertiesComponentMinSdkKey(myFormFactor),
                                                                      Integer.toString(myFormFactor.defaultApi));
    setSelectedApiLevel(savedApiLevel);
    // If the savedApiLevel is not available, just pick the first target in the list
    // which is guaranteed to be a valid target because of the filtering done by populateComboBox()
    if (getSelectedIndex() < 0 && getItemCount() > 0) {
      setSelectedIndex(0);
    }
  }

  /**
   * Fill in the values that can be derived from the selected min SDK level:
   *
   * minApiLevel will be set to the selected api level (string or number)
   * minApi will be set to the numerical equivalent
   * buildApi will be set to the highest installed platform, or to the preview platform if a preview is selected
   * buildApiString will be set to the corresponding string
   * targetApi will be set to the highest installed platform or to the preview platform if a preview is selected
   * targetApiString will be set to the corresponding string
   *
   * @param stateStore
   * @param modified
   */
  public void deriveValues(@NotNull ScopedStateStore stateStore, @NotNull Set<Key> modified) {
    if (modified.contains(myTargetComboBoxKey) || modified.contains(myInclusionKey)) {
      // First remove the last request, no need to install more than one platform
      if (!myInstallRequests.isEmpty()) {
        for (String request : myInstallRequests) {
          stateStore.listRemove(INSTALL_REQUESTS_KEY, request);
        }
        myInstallRequests.clear();
      }

      // If this form factor is not included then there is nothing to do:
      AndroidTargetComboBoxItem targetItem = stateStore.get(myTargetComboBoxKey);
      if (targetItem == null || !stateStore.getNotNull(myInclusionKey, false)) {
        return;
      }

      stateStore.put(FormFactorUtils.getMinApiKey(myFormFactor), targetItem.getData());
      stateStore.put(FormFactorUtils.getMinApiLevelKey(myFormFactor), targetItem.myApiLevel);
      IAndroidTarget target = targetItem.target;
      if (target != null && (target.getVersion().isPreview() || !target.isPlatform())) {
        // Make sure we set target and build to the preview version as well
        populateApiLevels(targetItem.myApiLevel, target, stateStore);
      }
      else {
        int targetApiLevel;
        if (ourHighestInstalledApiTarget != null) {
          targetApiLevel = ourHighestInstalledApiTarget.getVersion().getFeatureLevel();
        }
        else {
          targetApiLevel = 0;
        }
        populateApiLevels(targetApiLevel, ourHighestInstalledApiTarget, stateStore);
      }
      AndroidVersion androidVersion = targetItem.myAndroidVersion;
      String platformPath = DetailsTypes.getPlatformPath(androidVersion);


      // Check to see if this is installed. If not, request that we install it
      if (targetItem.myAddon != null) {
        // The user selected a non platform SDK (e.g. for Google Glass). Let us install it:
        RepoPackage p = targetItem.myAddon;
        stateStore.listPush(INSTALL_REQUESTS_KEY, p.getPath());
        myInstallRequests.add(p.getPath());

        // We also need the platform if not already installed:
        AndroidTargetManager targetManager = AndroidSdkUtils.tryToChooseSdkHandler().getAndroidTargetManager(REPO_LOG);
        if (targetManager.getTargetFromHashString(AndroidTargetHash.getPlatformHashString(androidVersion), REPO_LOG) == null) {
          stateStore.listPush(INSTALL_REQUESTS_KEY, platformPath);
          myInstallRequests.add(platformPath);
        }

        // The selected minVersion should also be the buildApi:
        populateApiLevels(targetItem.myApiLevel, null, stateStore);
      }
      else if (target == null) {
        // TODO: If the user has no APIs installed, we then request to install whichever version the user has targeted here.
        // Instead, we should choose to install the highest stable API possible. However, users having no SDK at all installed is pretty
        // unlikely, so this logic can wait for a followup CL.
        if (ourHighestInstalledApiTarget == null ||
            (androidVersion.getApiLevel() > ourHighestInstalledApiTarget.getVersion().getApiLevel() &&
             !ourInstalledVersions.contains(androidVersion))) {

          // The user selected a stable platform minSDK that is higher than any installed SDK. Let us install it.
          stateStore.listPush(INSTALL_REQUESTS_KEY, platformPath);
          myInstallRequests.add(platformPath);

          // The selected minVersion would also be the highest sdkVersion after this install, so specify buildApi again here:
          populateApiLevels(androidVersion.getApiLevel(), null, stateStore);
        }
      }
      PropertiesComponent.getInstance()
        .setValue(FormFactorUtils.getPropertiesComponentMinSdkKey(myFormFactor), targetItem.getData());

      // Check Java language level; should be 7 for L; eventually this will be automatically defaulted by the Android Gradle plugin
      // instead: https://code.google.com/p/android/issues/detail?id=76252
      String javaVersion = null;
      if (ourHighestInstalledApiTarget != null && ourHighestInstalledApiTarget.getVersion().getFeatureLevel() >= 21) {
        AndroidSdkData sdkData = AndroidSdkUtils.tryToChooseAndroidSdk();
        if (sdkData != null) {
          JavaSdk jdk = JavaSdk.getInstance();
          Sdk sdk = ProjectJdkTable.getInstance().findMostRecentSdkOfType(jdk);
          if (sdk != null) {
            JavaSdkVersion version = jdk.getVersion(sdk);
            if (version != null && version.isAtLeast(JavaSdkVersion.JDK_1_7)) {
              javaVersion = JavaSdkVersion.JDK_1_7.getDescription();
            }
          }
        }
      }
      stateStore.put(FormFactorUtils.getLanguageLevelKey(myFormFactor), javaVersion);
    }
  }

  public void setSelectedApiLevel(String item) {
    ScopedDataBinder.setSelectedItem(this, item);
  }

  private void populateComboBox(@NotNull FormFactor formFactor, int minSdk) {
    for (AndroidTargetComboBoxItem target :
      Iterables.filter(ourTargets, FormFactorUtils.getMinSdkComboBoxFilter(formFactor, minSdk))) {
      if (target.myApiLevel >= minSdk || (target.target != null && target.target.getVersion().isPreview())) {
        addItem(target);
      }
    }
  }

  /**
   * Load the definitions of the android compilation targets
   */
  private static void loadTargets() {
    ourTargets.clear();
    IAndroidTarget[] targets = getCompilationTargets();

    if (AndroidSdkUtils.isAndroidSdkAvailable()) {
      String[] knownVersions = TemplateUtils.getKnownVersions();

      for (int i = 0; i < knownVersions.length; i++) {
        AndroidTargetComboBoxItem targetInfo = new AndroidTargetComboBoxItem(knownVersions[i], i + 1);
        ourTargets.add(targetInfo);
      }
    }

    for (IAndroidTarget target : targets) {
      if (target.getVersion().isPreview() || !target.getAdditionalLibraries().isEmpty()) {
        AndroidTargetComboBoxItem targetInfo = new AndroidTargetComboBoxItem(target);
        ourTargets.add(targetInfo);
      }
    }
  }

  /**
   * Load the installed android versions from the SDK
   */
  public static void loadInstalledVersions() {
    IAndroidTarget[] targets = getCompilationTargets();

    IAndroidTarget highestInstalledTarget = null;
    ourInstalledVersions.clear();
    for (IAndroidTarget target : targets) {
      if (target.isPlatform() &&
          (highestInstalledTarget == null ||
           target.getVersion().getFeatureLevel() > highestInstalledTarget.getVersion().getFeatureLevel() &&
           !target.getVersion().isPreview())) {
        highestInstalledTarget = target;
      }
      if (target.getVersion().isPreview() || !target.getAdditionalLibraries().isEmpty()) {
        AndroidTargetComboBoxItem targetInfo = new AndroidTargetComboBoxItem(target);
        ourInstalledVersions.add(targetInfo.target.getVersion());
      }
    }
    ourHighestInstalledApiTarget = highestInstalledTarget;
  }

  /**
   * @return a list of android compilation targets (platforms and add-on SDKs)
   */
  @NotNull
  private static IAndroidTarget[] getCompilationTargets() {
    AndroidTargetManager targetManager = AndroidSdkUtils.tryToChooseSdkHandler().getAndroidTargetManager(REPO_LOG);
    List<IAndroidTarget> result = Lists.newArrayList();
    for (IAndroidTarget target : targetManager.getTargets(REPO_LOG)) {
      if (!target.isPlatform()) {
        continue;
      }
      result.add(target);
    }
    return result.toArray(new IAndroidTarget[result.size()]);
  }

  public static class AndroidTargetComboBoxItem extends ApiComboBoxItem<String> {
    private int myApiLevel = -1;
    private AndroidVersion myAndroidVersion;

    public IAndroidTarget target = null;

    public RemotePackage myAddon = null;

    private AndroidTargetComboBoxItem(@NotNull AndroidVersion androidVersion, IdDisplay tag) {
      super(androidVersion.getApiString(), getLabel(androidVersion, tag), 1, 1);
      myAndroidVersion = androidVersion;
      myApiLevel = androidVersion.getFeatureLevel();
    }


    public AndroidTargetComboBoxItem(String label, int apiLevel) {
      super(Integer.toString(apiLevel), label, 1, 1);
      myAndroidVersion = new AndroidVersion(apiLevel, null);
      myApiLevel = apiLevel;
    }

    public AndroidTargetComboBoxItem(int apiLevel) {
      this(new AndroidVersion(apiLevel, null), SystemImage.DEFAULT_TAG);
    }

    public AndroidTargetComboBoxItem(@NotNull IAndroidTarget target) {
      this(target.getVersion(), SystemImage.DEFAULT_TAG);
      this.target = target;
    }

    public AndroidTargetComboBoxItem(@NotNull RepoPackage info) {
      this(FormFactorUtils.getAndroidVersion(info), FormFactorUtils.getTag(info));
      if (info instanceof RemotePackage && SystemImage.GLASS_TAG.equals(FormFactorUtils.getTag(info))) {
        // If this is Glass then prepare to install this add-on package.
        // All platform are installed by a different mechanism.
        myAddon = (RemotePackage) info;
      }
    }

    public int getApiLevel() {
      return myApiLevel;
    }

    private static String getLabel(@NotNull AndroidVersion version, @Nullable IdDisplay tag) {
      int featureLevel = version.getFeatureLevel();
      if (SystemImage.GLASS_TAG.equals(tag)) {
        return String.format("Glass Development Kit Preview (API %1$d)", featureLevel);
      }
      if (featureLevel <= SdkVersionInfo.HIGHEST_KNOWN_API) {
        if (version.isPreview()) {
          return String.format("API %1$s: Android %2$s (%3$s preview)",
                               SdkVersionInfo.getCodeName(featureLevel),
                               SdkVersionInfo.getVersionString(featureLevel),
                               SdkVersionInfo.getCodeName(featureLevel));
        }
        else {
          return SdkVersionInfo.getAndroidName(featureLevel);
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
  }

  static final ScopedDataBinder.ComponentBinding<AndroidTargetComboBoxItem, JComboBox> TARGET_COMBO_BINDING =
    new ScopedDataBinder.ComponentBinding<AndroidTargetComboBoxItem, JComboBox>() {
      @Override
      public void setValue(@Nullable AndroidTargetComboBoxItem newValue, @NotNull JComboBox component) {
        component.setSelectedItem(newValue);
      }

      @Nullable
      @Override
      public AndroidTargetComboBoxItem getValue(@NotNull JComboBox component) {
        return (AndroidTargetComboBoxItem)component.getItemAt(component.getSelectedIndex());
      }

      @Override
      public void addActionListener(@NotNull ActionListener listener, @NotNull JComboBox component) {
        component.addActionListener(listener);
      }
    };

  /**
   * Populate the api variables in the given state store
   *
   * @param apiLevel  the chosen build api level
   * @param apiTarget the chosen target api level
   * @param state     the state in which the given variables will be set
   */
  public void populateApiLevels(int apiLevel, @Nullable IAndroidTarget apiTarget, @NotNull ScopedStateStore state) {
    if (apiLevel >= 1) {
      if (apiTarget == null) {
        state.put(myBuildApiKey, Integer.toString(apiLevel));
      }
      else if (!apiTarget.isPlatform()) {
        state.put(myBuildApiKey, AndroidTargetHash.getTargetHashString(apiTarget));
      }
      else {
        state.put(myBuildApiKey, TemplateMetadata.getBuildApiString(apiTarget.getVersion()));
      }
      state.put(myBuildApiLevelKey, apiLevel);
      if (apiLevel >= SdkVersionInfo.HIGHEST_KNOWN_API || (apiTarget != null && apiTarget.getVersion().isPreview())) {
        state.put(myTargetApiLevelKey, apiLevel);
        if (apiTarget != null) {
          state.put(myTargetApiStringKey, apiTarget.getVersion().getApiString());
        }
        else {
          state.put(myTargetApiStringKey, Integer.toString(apiLevel));
        }
      }
      else if (ourHighestInstalledApiTarget != null) {
        state.put(myTargetApiLevelKey, ourHighestInstalledApiTarget.getVersion().getApiLevel());
        state.put(myTargetApiStringKey, ourHighestInstalledApiTarget.getVersion().getApiString());
      }
    }
  }

  private void loadRemoteTargets(final int minSdkLevel, final Runnable completedCallback,
                                 final Runnable foundItemsCallback, final Runnable noItemsCallback) {
    AndroidSdkHandler sdkHandler = AndroidSdkUtils.tryToChooseSdkHandler();

    final Runnable runCallbacks = new Runnable() {
      @Override
      public void run() {
        if (completedCallback != null) {
          completedCallback.run();
        }
        if (getItemCount() > 0) {
          if (foundItemsCallback != null) {
            foundItemsCallback.run();
          }
        }
        else {
          if (noItemsCallback != null) {
            noItemsCallback.run();
          }
        }
      }
    };

    RepoManager.RepoLoadedCallback onComplete = new RepoManager.RepoLoadedCallback() {
      @Override
      public void doRun(@NotNull RepositoryPackages packages) {
        addPackages(packages.getNewPkgs(), minSdkLevel);
        addOfflineLevels();
        loadSavedApi();
        runCallbacks.run();
      }
    };

    // We need to pick up addons that don't have a target created due to the base platform not being installed.
    RepoManager.RepoLoadedCallback onLocalComplete = new RepoManager.RepoLoadedCallback() {
      @Override
      public void doRun(@NotNull RepositoryPackages packages) {
        addPackages(packages.getLocalPackages().values(), minSdkLevel);
      }
    };
    Runnable onError = new Runnable() {
      @Override
      public void run() {
        ApplicationManager.getApplication().invokeLater(new Runnable() {
          @Override
          public void run() {
            addOfflineLevels();
            runCallbacks.run();
          }
        }, ModalityState.any());
      }
    };

    StudioProgressRunner runner = new StudioProgressRunner(false, true, false, "Refreshing Targets", true, null);
    sdkHandler.getSdkManager(REPO_LOG).load(
      RepoManager.DEFAULT_EXPIRATION_PERIOD_MS,
      ImmutableList.of(onLocalComplete), ImmutableList.of(onComplete), ImmutableList.of(onError),
      runner, new StudioDownloader(), StudioSettingsController.getInstance(), false);
  }

  private void addPackages(@NotNull Collection<? extends RepoPackage> packages, int minSdkLevel) {
    Iterable<? extends RepoPackage> filter = Iterables.filter(packages, FormFactorUtils.getMinSdkPackageFilter(myFormFactor, minSdkLevel));
    List<RepoPackage> sorted = Lists.newArrayList(filter);
    Collections.sort(sorted, new Comparator<RepoPackage>() {
      @Override
      public int compare(@NotNull RepoPackage repoPackage, @NotNull RepoPackage other) {
        return FormFactorUtils.getAndroidVersion(repoPackage).compareTo(FormFactorUtils.getAndroidVersion(other));
      }
    });

    int existingApiLevel = -1;
    int prevInsertedApiLevel = -1;
    int index = -1;
    for (RepoPackage info : sorted) {
      int apiLevel = FormFactorUtils.getFeatureLevel(info);
      while (apiLevel > existingApiLevel) {
        existingApiLevel = ++index < getItemCount() ? ((AndroidTargetComboBoxItem)getItemAt(index)).myApiLevel : Integer.MAX_VALUE;
      }
      if (apiLevel != existingApiLevel && apiLevel != prevInsertedApiLevel) {
        insertItemAt(new AndroidTargetComboBoxItem(info), index++);
        prevInsertedApiLevel = apiLevel;
      }
    }
  }

  private void addOfflineLevels() {
    int existingApiLevel = -1;
    int prevInsertedApiLevel = -1;
    int index = -1;
    for (int apiLevel = myFormFactor.getMinOfflineApiLevel(); apiLevel <= myFormFactor.getMaxOfflineApiLevel(); apiLevel++) {
      if (myFormFactor.getApiBlacklist().contains(apiLevel) || apiLevel <= 0) {
        continue;
      }
      while (apiLevel > existingApiLevel) {
        existingApiLevel = ++index < getItemCount() ? ((AndroidTargetComboBoxItem)getItemAt(index)).myApiLevel : Integer.MAX_VALUE;
      }
      if (apiLevel != existingApiLevel && apiLevel != prevInsertedApiLevel) {
        insertItemAt(new AndroidTargetComboBoxItem(apiLevel), index++);
        prevInsertedApiLevel = apiLevel;
      }
    }
  }
}