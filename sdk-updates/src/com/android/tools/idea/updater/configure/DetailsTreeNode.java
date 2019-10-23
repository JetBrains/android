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
package com.android.tools.idea.updater.configure;

import com.android.repository.api.*;
import com.android.sdklib.repository.meta.DetailsTypes;
import com.intellij.ui.SimpleTextAttributes;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.event.ChangeEvent;
import javax.swing.event.ChangeListener;

/**
 * Represents a row in a table in {@link SdkUpdaterConfigurable} associated with a single package.
 * Can have three state: not installed, installed but with update available, and installed.
 */
class DetailsTreeNode extends UpdaterTreeNode {
  private PackageNodeModel myModel;
  private final ChangeListener myChangeListener;
  private SdkUpdaterConfigurable myConfigurable;

  public DetailsTreeNode(@NotNull PackageNodeModel state,
                         @Nullable ChangeListener changeListener,
                         @NotNull SdkUpdaterConfigurable configurable) {
    myModel = state;
    myModel.setState(getInitialState());
    myChangeListener = changeListener;
    myConfigurable = configurable;
  }

  @Override
  @NotNull
  public PackageNodeModel.SelectedState getInitialState() {
    return myModel.getPkg().isUpdate()
           ? PackageNodeModel.SelectedState.MIXED
           : myModel.getPkg().hasLocal() ? PackageNodeModel.SelectedState.INSTALLED : PackageNodeModel.SelectedState.NOT_INSTALLED;
  }

  @Override
  @Nullable   // Will be null if the state isn't yet initialized
  public PackageNodeModel.SelectedState getCurrentState() {
    return myModel.getState();
  }

  @Override
  public int compareTo(@NotNull UpdaterTreeNode o) {
    if (!(o instanceof DetailsTreeNode)) {
      return toString().compareTo(o.toString());
    }
    return myModel.getPkg().compareTo(((DetailsTreeNode)o).myModel.getPkg());
  }

  @Override
  protected void setState(@NotNull PackageNodeModel.SelectedState state) {
    myModel.setState(state);
    if (myChangeListener != null) {
      myChangeListener.stateChanged(new ChangeEvent(this));
    }
  }

  @Override
  public boolean equals(@NotNull Object obj) {
    if (!(obj instanceof DetailsTreeNode)) {
      return false;
    }
    return myModel.getPkg().equals(((DetailsTreeNode)obj).myModel.getPkg());
  }

  @Override
  public boolean includeInSummary() {
    return myModel.getPkg().getRepresentative().getTypeDetails() instanceof DetailsTypes.SourceDetailsType ||
           myModel.getPkg().getRepresentative().getTypeDetails() instanceof DetailsTypes.PlatformDetailsType;
  }

  @Nullable
  private String getDisplayName() {
    return myModel.getTitle();
  }

  @Override
  public boolean isPrimary() {
    return myModel.getPkg().getRepresentative().getTypeDetails() instanceof DetailsTypes.PlatformDetailsType;
  }

  @Override
  public void customizeRenderer(@NotNull Renderer renderer,
                                @Nullable JTree tree,
                                boolean selected,
                                boolean expanded,
                                boolean leaf,
                                int row,
                                boolean hasFocus) {
    SimpleTextAttributes attributes = SimpleTextAttributes.REGULAR_ATTRIBUTES;
    renderer.getTextRenderer().append(getDisplayName(), attributes);
  }

  @NotNull
  public UpdatablePackage getItem() {
    return myModel.getPkg();
  }

  @Override
  protected boolean canHaveMixedState() {
    return myModel.getPkg().isUpdate();
  }

  @Override
  @NotNull
  public String getStatusString() {
    if (getInitialState() == PackageNodeModel.SelectedState.INSTALLED) {
      return "Installed";
    }
    else {
      RepoManager mgr = myConfigurable.getRepoManager();
      RemotePackage remote = getItem().getRemote();
      // We know it has a remote since it's not installed.
      assert remote != null;
      PackageOperation installer = mgr.getInProgressInstallOperation(remote);
      if (installer != null) {
        PackageOperation.InstallStatus status = installer.getInstallStatus();
        if (status == PackageOperation.InstallStatus.PREPARING) {
          return "Preparing install...";
        }
        if (status == PackageOperation.InstallStatus.PREPARED) {
          return "Install ready";
        }
      }

      if (getInitialState() == PackageNodeModel.SelectedState.NOT_INSTALLED) {
        return "Not installed";
      }
      else {
        // The initial state being mixed ensures we have a remote we care about
        //noinspection ConstantConditions
        return "Update Available: " + myModel.getPkg().getRemote().getVersion();
      }
    }
  }

  @NotNull
  public RepoPackage getPackage() {
    return getItem().getRepresentative();
  }
}