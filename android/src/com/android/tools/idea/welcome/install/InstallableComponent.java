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

import com.android.repository.api.LocalPackage;
import com.android.repository.api.ProgressIndicator;
import com.android.repository.api.RemotePackage;
import com.android.repository.impl.meta.RepositoryPackages;
import com.android.repository.io.FileOp;
import com.android.sdklib.repositoryv2.AndroidSdkHandler;
import com.android.tools.idea.sdkv2.StudioLoggerProgressIndicator;
import com.android.tools.idea.welcome.SdkLocationUtils;
import com.android.tools.idea.welcome.wizard.WelcomeUIUtils;
import com.android.tools.idea.wizard.dynamic.DynamicWizardStep;
import com.android.tools.idea.wizard.dynamic.ScopedStateStore;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;
import java.util.Collections;
import java.util.Map;
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
  protected final FileOp myFileOp;

  public InstallableComponent(@NotNull ScopedStateStore stateStore,
                              @NotNull String name,
                              long size,
                              @NotNull String description,
                              @NotNull FileOp fop) {
    super(description);
    myStateStore = stateStore;
    myName = name;
    mySize = size;
    myKey = stateStore.createKey("component.enabled." + System.identityHashCode(this), Boolean.class);
    myFileOp = fop;
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
  public abstract Collection<String> getRequiredSdkPackages(@Nullable Map<String, RemotePackage> remotePackages);

  public abstract void configure(@NotNull InstallContext installContext, @NotNull AndroidSdkHandler sdkHandler);

  protected boolean isSelectedByDefault(@Nullable @SuppressWarnings("UnusedParameters") AndroidSdkHandler sdkHandler) {
    return true;
  }

  // TODO Rename this to isEnabled
  @Override
  public boolean isOptional() {
    return myIsOptional;
  }

  protected boolean isOptionalForSdkLocation(@Nullable @SuppressWarnings("UnusedParameters") AndroidSdkHandler sdkHandler) {
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
  public void updateState(@NotNull AndroidSdkHandler sdkHandler) {
    // If we don't have anything to install, show as unchecked and not editable.
    boolean nothingToInstall = !SdkLocationUtils.isWritable(myFileOp, sdkHandler.getLocation()) || getRequiredSdkPackages(null).isEmpty();
    myIsOptional = !nothingToInstall && isOptionalForSdkLocation(sdkHandler);

    boolean isSelected;

    if (!myIsOptional) {
      isSelected = !nothingToInstall;
    }
    else if (myUserSelection != null) {
      isSelected = myUserSelection;
    }
    else {
      isSelected = isSelectedByDefault(sdkHandler);
    }
    myStateStore.put(myKey, isSelected);
    myIsInstalled = checkInstalledPackages(sdkHandler);
  }

  private boolean checkInstalledPackages(@Nullable AndroidSdkHandler sdkHandler) {
    if (sdkHandler != null) {
      ProgressIndicator progress = new StudioLoggerProgressIndicator(InstallableComponent.class);
      RepositoryPackages packages = sdkHandler.getSdkManager(progress).getPackages();
      Map<String, ? extends LocalPackage> localPackages = packages.getLocalPackages();
      Collection<String> requiredSdkPackages = getRequiredSdkPackages(null);
      return localPackages.keySet().containsAll(requiredSdkPackages);
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
