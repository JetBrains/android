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

import com.android.sdklib.AndroidTargetHash;
import com.android.sdklib.AndroidVersion;
import com.android.sdklib.IAndroidTarget;
import com.android.sdklib.SdkVersionInfo;
import com.android.sdklib.repository.FullRevision;
import com.android.sdklib.repository.MajorRevision;
import com.android.sdklib.repository.descriptors.IPkgDesc;
import com.android.sdklib.repository.descriptors.PkgDesc;
import com.android.sdklib.repository.descriptors.PkgType;
import com.android.sdklib.repository.local.LocalAddonPkgInfo;
import com.android.sdklib.repository.local.LocalPkgInfo;
import com.android.tools.idea.sdk.SdkLoadedCallback;
import com.android.tools.idea.sdk.SdkPackages;
import com.android.tools.idea.sdk.SdkState;
import com.android.tools.idea.sdk.remote.RemotePkgInfo;
import com.android.tools.idea.templates.TemplateMetadata;
import com.android.tools.idea.templates.TemplateUtils;
import com.android.tools.idea.ui.ComboBoxItemWithApiTag;
import com.android.tools.idea.wizard.dynamic.ScopedDataBinder;
import com.android.tools.idea.wizard.dynamic.ScopedStateStore;
import com.google.common.collect.Iterables;
import com.google.common.collect.Lists;
import com.google.common.collect.Sets;
import com.intellij.ide.util.PropertiesComponent;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ModalityState;
import com.intellij.openapi.diagnostic.Logger;
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
import java.util.List;

import static com.android.tools.idea.npw.FormFactorUtils.*;
import static com.android.tools.idea.wizard.WizardConstants.INSTALL_REQUESTS_KEY;
import static com.android.tools.idea.wizard.dynamic.ScopedStateStore.Key;

/**
* A labeled combo box of SDK options for a given FormFactor.
*/
public final class FormFactorApiComboBox extends JComboBox {
  private static final Logger LOG = Logger.getInstance(FormFactorApiComboBox.class);

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

  private IPkgDesc myInstallRequest;
  private Key<String> myBuildApiKey;
  private Key<Integer> myBuildApiLevelKey;
  private Key<Integer> myTargetApiLevelKey;
  private Key<String> myTargetApiStringKey;
  private Key<AndroidTargetComboBoxItem> myTargetComboBoxKey;
  private Key<Boolean> myInclusionKey;

  /**
   * Initializes this component, notably by populating the available values from local, remote, and statically-defined sources.
   *
   * @param formFactor The form factor for which we're showing available api levels
   * @param minSdkLevel The minimum sdk level we should show.
   * @param completedCallback A Runnable that will be run when we've finished looking for items (with success or failure).
   * @param foundItemsCallback A Runnable that will be run once we've determined that there are available items to show.
   * @param noItemsCallback A Runnable that will be run when we've finished looking for items without finding any.
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
    myTargetComboBoxKey = getTargetComboBoxKey(formFactor);
    myInclusionKey = getInclusionKey(formFactor);
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
    binder.register(getTargetComboBoxKey(myFormFactor), this, TARGET_COMBO_BINDING);
  }

  /**
   * Load the saved value for this ComboBox
   */
  public void loadSavedApi() {
    // Check for a saved value for the min api level
    String savedApiLevel = PropertiesComponent.getInstance().getValue(FormFactorUtils.getPropertiesComponentMinSdkKey(myFormFactor),
                                                                      Integer.toString(myFormFactor.defaultApi));
    setSelectedItem(savedApiLevel);
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
   * @param stateStore
   * @param modified
   */
  public void deriveValues(@NotNull ScopedStateStore stateStore, @NotNull Set<Key> modified) {
    if (modified.contains(myTargetComboBoxKey) || modified.contains(myInclusionKey)) {
      AndroidTargetComboBoxItem targetItem = stateStore.get(myTargetComboBoxKey);
      if (targetItem == null) {
        return;
      }
      stateStore.put(getMinApiKey(myFormFactor), targetItem.id.toString());
      stateStore.put(getMinApiLevelKey(myFormFactor), targetItem.apiLevel);
      IAndroidTarget target = targetItem.target;
      if (target != null && (target.getVersion().isPreview() || !target.isPlatform())) {
        // Make sure we set target and build to the preview version as well
        populateApiLevels(targetItem.apiLevel, target, stateStore);
      } else {
        int targetApiLevel;
        if (ourHighestInstalledApiTarget != null) {
          targetApiLevel = ourHighestInstalledApiTarget.getVersion().getFeatureLevel();
        } else {
          targetApiLevel = 0;
        }
        populateApiLevels(targetApiLevel, ourHighestInstalledApiTarget, stateStore);
      }
      AndroidVersion androidVersion = new AndroidVersion(targetItem.apiLevel, null);
      IPkgDesc platformDesc = PkgDesc.Builder.newPlatform(androidVersion, new MajorRevision(1), FullRevision.NOT_SPECIFIED).create();

      // Check to see if this is installed. If not, request that we install it
      if (myInstallRequest != null) {
        // First remove the last request, no need to install more than one platform
        stateStore.listRemove(INSTALL_REQUESTS_KEY, myInstallRequest);
        if (myInstallRequest.getType() != PkgType.PKG_PLATFORM) {
          stateStore.listRemove(INSTALL_REQUESTS_KEY, platformDesc);
        }
      }
      if (target == null) {
        // TODO: If the user has no APIs installed, we then request to install whichever version the user has targeted here.
        // Instead, we should choose to install the highest stable API possible. However, users having no SDK at all installed is pretty
        // unlikely, so this logic can wait for a followup CL.
        if (ourHighestInstalledApiTarget == null ||
            (androidVersion.getApiLevel() > ourHighestInstalledApiTarget.getVersion().getApiLevel() &&
             !ourInstalledVersions.contains(androidVersion) &&
             stateStore.get(myInclusionKey))) {

          // The user selected a stable platform minSDK that is higher than any installed SDK. Let us install it.
          stateStore.listPush(INSTALL_REQUESTS_KEY, platformDesc);
          myInstallRequest = platformDesc;

          // The selected minVersion would also be the highest sdkVersion after this install, so specify buildApi again here:
          populateApiLevels(androidVersion.getApiLevel(), null, stateStore);
        }
        if (targetItem.myAddon != null) {
          // The user selected a non stable SDK (a preview version) or a non platform SDK (e.g. for Google Glass). Let us install it:
          IPkgDesc addonDesc = targetItem.myAddon;
          stateStore.listPush(INSTALL_REQUESTS_KEY, addonDesc);
          // Overwrite request from above, since (earlier in this method) removing an addon will also remove the platform.
          myInstallRequest = addonDesc;

          AndroidSdkData sdkData = AndroidSdkUtils.tryToChooseAndroidSdk();
          LocalPkgInfo platform = sdkData.getLocalSdk().getPkgInfo(PkgType.PKG_PLATFORM, androidVersion);
          if (platform == null) {
            stateStore.listPush(INSTALL_REQUESTS_KEY, platformDesc);
          }

          // The selected minVersion should also be the buildApi:
          populateApiLevels(targetItem.apiLevel, null, stateStore);
        }
      }
      PropertiesComponent.getInstance().setValue(getPropertiesComponentMinSdkKey(myFormFactor), targetItem.id.toString());

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
      stateStore.put(getLanguageLevelKey(myFormFactor), javaVersion);
    }
  }

  public void setSelectedItem(String item) {
    ScopedDataBinder.setSelectedItem(this, item);
  }

  private void populateComboBox(@NotNull FormFactorUtils.FormFactor formFactor, int minSdk) {
    for (AndroidTargetComboBoxItem target :
      Iterables.filter(ourTargets, FormFactorUtils.getMinSdkComboBoxFilter(formFactor, minSdk))) {
      if (target.apiLevel >= minSdk || (target.target != null && target.target.getVersion().isPreview())) {
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
      if (highestInstalledTarget == null ||
          target.getVersion().getFeatureLevel() > highestInstalledTarget.getVersion().getFeatureLevel() &&
          !target.getVersion().isPreview()) {
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
    AndroidSdkData sdkData = AndroidSdkUtils.tryToChooseAndroidSdk();
    if (sdkData == null) {
      return new IAndroidTarget[0];
    }
    return getCompilationTargets(sdkData);
  }

  @NotNull
  public static IAndroidTarget[] getCompilationTargets(@NotNull AndroidSdkData sdkData) {
    IAndroidTarget[] targets = sdkData.getTargets();
    List<IAndroidTarget> list = new ArrayList<IAndroidTarget>();

    for (IAndroidTarget target : targets) {
      if (!target.isPlatform() && target.getAdditionalLibraries().isEmpty()) {
        continue;
      }
      list.add(target);
    }
    return list.toArray(new IAndroidTarget[list.size()]);
  }

  public static class AndroidTargetComboBoxItem extends ComboBoxItemWithApiTag {
    public int apiLevel = -1;
    public IAndroidTarget target = null;

    public IPkgDesc myAddon = null;

    public AndroidTargetComboBoxItem(@NotNull String label, int apiLevel) {
      super(Integer.toString(apiLevel), label, 1, 1);
      this.apiLevel = apiLevel;
    }

    public AndroidTargetComboBoxItem(@NotNull IAndroidTarget target) {
      super(getId(target), getLabel(target), 1, 1);
      this.target = target;
      apiLevel = target.getVersion().getFeatureLevel();
    }

    public AndroidTargetComboBoxItem(IPkgDesc info) {
      this(info.getListDescription(), info.getAndroidVersion().getApiLevel());
      myAddon = info;
    }

    @NotNull
    private static String getLabel(@NotNull IAndroidTarget target) {
      if (target.isPlatform()
          && target.getVersion().getApiLevel() <= SdkVersionInfo.HIGHEST_KNOWN_API) {
        if (target.getVersion().isPreview()) {
          return target.getVersion().getApiString() + ": " + target.getName();
        }
        String name = SdkVersionInfo.getAndroidName(target.getVersion().getApiLevel());
        if (name == null) {
          return "API " + Integer.toString(target.getVersion().getApiLevel());
        } else {
          return name;
        }
      } else {
        return AndroidSdkUtils.getTargetLabel(target);
      }
    }

    @NotNull
    private static String getId(@NotNull IAndroidTarget target) {
      return target.getVersion().getApiString();
    }

    @Override
    public String toString() {
      return label;
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
   * @param apiLevel the chosen build api level
   * @param apiTarget the chosen target api level
   * @param state the state in which the given variables will be set
   */
  public void populateApiLevels(int apiLevel, @Nullable IAndroidTarget apiTarget, @NotNull ScopedStateStore state) {
    if (apiLevel >= 1) {
      if (apiTarget == null) {
        state.put(myBuildApiKey, Integer.toString(apiLevel));
      } else if (!apiTarget.isPlatform()) {
        state.put(myBuildApiKey, AndroidTargetHash.getTargetHashString(apiTarget));
      } else {
        state.put(myBuildApiKey, TemplateMetadata.getBuildApiString(apiTarget.getVersion()));
      }
      state.put(myBuildApiLevelKey, apiLevel);
      if (apiLevel >= SdkVersionInfo.HIGHEST_KNOWN_API || (apiTarget != null && apiTarget.getVersion().isPreview())) {
        state.put(myTargetApiLevelKey, apiLevel);
        if (apiTarget != null) {
          state.put(myTargetApiStringKey, apiTarget.getVersion().getApiString());
        } else {
          state.put(myTargetApiStringKey, Integer.toString(apiLevel));
        }
      } else if (ourHighestInstalledApiTarget != null) {
        state.put(myTargetApiLevelKey, ourHighestInstalledApiTarget.getVersion().getApiLevel());
        state.put(myTargetApiStringKey, ourHighestInstalledApiTarget.getVersion().getApiString());
      }
    }
  }

  private void loadRemoteTargets(final int minSdkLevel, final Runnable completedCallback,
                                 final Runnable foundItemsCallback, final Runnable noItemsCallback) {
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

    SdkLoadedCallback onComplete = new SdkLoadedCallback(true) {
      @Override
      public void doRun(@NotNull SdkPackages packages) {
        List<RemotePkgInfo> packageList = Lists.newArrayList(packages.getNewPkgs());
        Collections.sort(packageList);
        Iterator<RemotePkgInfo> result =
          Iterables.filter(packageList, FormFactorUtils.getMinSdkPackageFilter(myFormFactor, minSdkLevel)).iterator();

        while (result.hasNext()) {
          RemotePkgInfo info = result.next();
          addItem(new AndroidTargetComboBoxItem(info.getPkgDesc()));
        }
        runCallbacks.run();
      }
    };

    // We need to pick up addons that don't have a target created due to the base platform not being installed.
    SdkLoadedCallback onLocalComplete = new SdkLoadedCallback(true) {
      @Override
      public void doRun(@NotNull SdkPackages packages) {
        List<LocalPkgInfo> packageList = Lists.newArrayList(packages.getLocalPkgInfos());
        Collections.sort(packageList);
        Iterable<LocalAddonPkgInfo> addons = Iterables.filter(packageList, LocalAddonPkgInfo.class);
        Iterable<LocalAddonPkgInfo> result =
          Iterables.filter(addons, FormFactorUtils.getMinSdkLocalPackageFilter(myFormFactor, minSdkLevel));

        for (LocalAddonPkgInfo info : result) {
          if (info.getAndroidTarget() == null) {
            addItem(new AndroidTargetComboBoxItem(info.getDesc()));
          }
        }
      }
    };
    Runnable onError = new Runnable() {
      @Override
      public void run() {
        ApplicationManager.getApplication().invokeLater(new Runnable() {
          @Override
          public void run() {
            runCallbacks.run();
          }
        }, ModalityState.any());
      }
    };

    SdkState.getInstance(AndroidSdkUtils.tryToChooseAndroidSdk())
      .loadAsync(SdkState.DEFAULT_EXPIRATION_PERIOD_MS, false, onLocalComplete, onComplete, onError, true);
  }

}
