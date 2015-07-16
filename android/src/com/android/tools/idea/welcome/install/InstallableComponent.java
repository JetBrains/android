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
package com.android.tools.idea.welcome.install;

import com.android.sdklib.SdkManager;
import com.android.sdklib.repository.descriptors.IPkgDesc;
import com.android.sdklib.repository.descriptors.PkgType;
import com.android.sdklib.repository.local.LocalPkgInfo;
import com.android.sdklib.repository.local.LocalSdk;
import com.android.tools.idea.sdk.remote.RemotePkgInfo;
import com.android.tools.idea.welcome.wizard.WelcomeUIUtils;
import com.android.tools.idea.wizard.dynamic.DynamicWizardStep;
import com.android.tools.idea.wizard.dynamic.ScopedStateStore;
import com.google.common.collect.Multimap;
import com.google.common.collect.Sets;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.util.Collection;
import java.util.Collections;
import java.util.EnumSet;
import java.util.Set;

/**
 * Base class for leaf components (the ones that are immediately installed).
 */
public abstract class InstallableComponent extends ComponentTreeNode {
  protected final ScopedStateStore.Key<Boolean> myKey;
  protected final ScopedStateStore myStateStore;
  @NotNull private final String myName;
  private final long mySize;
  private Boolean myUserSelection; // null means default component enablement is used
  private boolean myIsOptional = true;
  private boolean myIsInstalled = false;

  public InstallableComponent(@NotNull ScopedStateStore stateStore, @NotNull String name, long size, @NotNull String description) {
    super(description);
    myStateStore = stateStore;
    myName = name;
    mySize = size;
    myKey = stateStore.createKey("component.enabled." + System.identityHashCode(this), Boolean.class);
  }

  @Override
  public String getLabel() {
    String sizeLabel = isInstalled() ? "installed" : WelcomeUIUtils.getSizeLabel(mySize);
    return String.format("%s – (%s)", myName, sizeLabel);
  }

  private boolean isInstalled() {
    return myIsInstalled;
  }

  /**
   * @param remotePackages an up-to-date list of the packages in the Android SDK repositories, if one can be obtained.
   */
  @NotNull
  public abstract Collection<IPkgDesc> getRequiredSdkPackages(@Nullable Multimap<PkgType, RemotePkgInfo> remotePackages);

  public abstract void configure(@NotNull InstallContext installContext, @NotNull File sdk);

  protected boolean isSelectedByDefault(@Nullable SdkManager sdkManager) {
    return true;
  }

  @Override
  public boolean isOptional() {
    return myIsOptional;
  }

  protected boolean isOptionalForSdkLocation(@Nullable SdkManager manager) {
    return true;
  }

  @Override
  public Collection<InstallableComponent> getChildrenToInstall() {
    if (!myStateStore.getNotNull(myKey, true)) {
      return Collections.emptySet();
    }
    return Collections.singleton(this);
  }

  @NotNull
  @Override
  public Collection<DynamicWizardStep> createSteps() {
    return Collections.emptySet();
  }

  @Override
  public void updateState(@Nullable SdkManager manager) {
    boolean isSelected;
    // If we don't have anything to install, show as unchecked and not editable.
    boolean nothingToInstall = getRequiredSdkPackages(null).isEmpty();
    myIsOptional = !nothingToInstall && isOptionalForSdkLocation(manager);

    if (!myIsOptional) {
      isSelected = !nothingToInstall;
    }
    else if (myUserSelection != null) {
      isSelected = myUserSelection;
    }
    else {
      isSelected = isSelectedByDefault(manager);
    }
    myStateStore.put(myKey, isSelected);
    myIsInstalled = checkInstalledPackages(manager);
  }

  private boolean checkInstalledPackages(@Nullable SdkManager manager) {
    if (manager != null) {
      LocalSdk localSdk = manager.getLocalSdk();
      LocalPkgInfo[] pkgsInfos = localSdk.getPkgsInfos(EnumSet.allOf(PkgType.class));
      Set<String> descs = Sets.newHashSetWithExpectedSize(pkgsInfos.length);
      for (LocalPkgInfo pkgsInfo : pkgsInfos) {
        IPkgDesc desc = pkgsInfo.getDesc();
        descs.add(desc.getPath());
      }
      Collection<IPkgDesc> requiredSdkPackages = getRequiredSdkPackages(null);
      if (requiredSdkPackages.isEmpty()) {
        return false;
      }
      for (IPkgDesc desc : requiredSdkPackages) {
        if (!descs.contains(desc.getPath())) {
          return false;
        }
      }
      return true;
    }
    else {
      return false;
    }
  }

  @Override
  public void toggle(boolean isSelected) {
    if (myIsOptional) {
      myUserSelection = isSelected;
      myStateStore.put(myKey, isSelected);
    }
  }

  @Override
  public Collection<ComponentTreeNode> getImmediateChildren() {
    return Collections.emptySet();
  }

  @Override
  public boolean isChecked() {
    return myStateStore.getNotNull(myKey, true);
  }

  public long getInstalledSize() {
    return myIsInstalled ? 0 : mySize;
  }

  @Override
  public boolean componentStateChanged(@NotNull Set<ScopedStateStore.Key> modified) {
    return modified.contains(myKey);
  }
}
