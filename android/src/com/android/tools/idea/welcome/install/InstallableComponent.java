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

import com.android.repository.api.ProgressIndicator;
import com.android.repository.api.RepoManager;
import com.android.repository.api.UpdatablePackage;
import com.android.repository.impl.meta.Archive;
import com.android.repository.impl.meta.RepositoryPackages;
import com.android.repository.io.FileOp;
import com.android.sdklib.repository.AndroidSdkHandler;
import com.android.tools.idea.sdk.progress.StudioLoggerProgressIndicator;
import com.android.tools.idea.welcome.SdkLocationUtils;
import com.android.tools.idea.welcome.wizard.WelcomeUIUtils;
import com.android.tools.idea.wizard.dynamic.DynamicWizardStep;
import com.android.tools.idea.wizard.dynamic.ScopedStateStore;
import com.google.common.collect.Lists;
import org.jetbrains.annotations.NotNull;

import java.util.*;

/**
 * Base class for leaf components (the ones that are immediately installed).
 */
public abstract class InstallableComponent extends ComponentTreeNode {
  protected final ScopedStateStore.Key<Boolean> myKey;
  protected final ScopedStateStore myStateStore;
  @NotNull private final String myName;
  private Boolean myUserSelection; // null means default component enablement is used
  private boolean myIsOptional = true;
  private boolean myIsInstalled = false;
  private static final ProgressIndicator PROGRESS_LOGGER = new StudioLoggerProgressIndicator(InstallableComponent.class);

  protected final FileOp myFileOp;
  protected final boolean myInstallUpdates;
  protected AndroidSdkHandler mySdkHandler;
  protected RepositoryPackages myRepositoryPackages;

  public InstallableComponent(@NotNull ScopedStateStore stateStore,
                              @NotNull String name,
                              @NotNull String description,
                              boolean installUpdates,
                              @NotNull FileOp fop) {
    super(description);
    myInstallUpdates = installUpdates;
    myStateStore = stateStore;
    myName = name;
    myKey = stateStore.createKey("component.enabled." + System.identityHashCode(this), Boolean.class);
    myFileOp = fop;
  }

  @Override
  public String getLabel() {
    String sizeLabel = isInstalled() ? "installed" : WelcomeUIUtils.getSizeLabel(getDownloadSize());
    return String.format("%s – (%s)", myName, sizeLabel);
  }

  private boolean isInstalled() {
    return myIsInstalled;
  }

  /**
   * Gets the packages that this component would actually install (the required packages that aren't already installed
   * or have an update available, if we're installing updates).
   */
  @NotNull
  public Collection<UpdatablePackage> getPackagesToInstall() {
    List<UpdatablePackage> result = Lists.newArrayList();
    Map<String, UpdatablePackage> consolidatedPackages = myRepositoryPackages.getConsolidatedPkgs();
    for (String path : getRequiredSdkPackages()) {
      UpdatablePackage p = consolidatedPackages.get(path);
      if (p != null && p.hasRemote() && (!p.hasLocal() || (myInstallUpdates && p.isUpdate()))) {
        result.add(p);
      }
    }
    return result;
  }

  /**
   * Gets the unfiltered collection of all packages required by this component.
   */
  @NotNull
  protected abstract Collection<String> getRequiredSdkPackages();

  public abstract void configure(@NotNull InstallContext installContext, @NotNull AndroidSdkHandler sdkHandler);

  protected boolean isSelectedByDefault() {
    return true;
  }

  // TODO Rename this to isEnabled
  @Override
  public boolean isOptional() {
    return myIsOptional;
  }

  protected boolean isOptionalForSdkLocation() {
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
    mySdkHandler = sdkHandler;
    if (myRepositoryPackages == null) {
      RepoManager sdkManager = mySdkHandler.getSdkManager(PROGRESS_LOGGER);
      myRepositoryPackages = sdkManager.getPackages();
    }
    boolean nothingToInstall = !SdkLocationUtils.isWritable(myFileOp, sdkHandler.getLocation()) || getPackagesToInstall().isEmpty();
    myIsOptional = !nothingToInstall && isOptionalForSdkLocation();

    boolean isSelected;

    if (!myIsOptional) {
      isSelected = !nothingToInstall;
    }
    else if (myUserSelection != null) {
      isSelected = myUserSelection;
    }
    else {
      isSelected = isSelectedByDefault();
    }
    myStateStore.put(myKey, isSelected);
    myIsInstalled = checkInstalledPackages();
  }

  private boolean checkInstalledPackages() {
    if (mySdkHandler != null) {
      return getPackagesToInstall().isEmpty();
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

  public long getDownloadSize() {
    long size = 0;
    for (UpdatablePackage updatable : getPackagesToInstall()) {
      // TODO: support patches if this is an update
      Archive archive = updatable.getRemote().getArchive();
      if (archive != null) {
        size += archive.getComplete().getSize();
      }
    }
    return size;
  }

  @Override
  public boolean componentStateChanged(@NotNull Set<ScopedStateStore.Key> modified) {
    return modified.contains(myKey);
  }
}
