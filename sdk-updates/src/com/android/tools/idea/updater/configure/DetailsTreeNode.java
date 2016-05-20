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
  private NodeStateHolder myStateHolder;
  private final ChangeListener myChangeListener;
  private SdkUpdaterConfigurable myConfigurable;

  public DetailsTreeNode(@NotNull NodeStateHolder state,
                         @Nullable ChangeListener changeListener,
                         @NotNull SdkUpdaterConfigurable configurable) {
    myStateHolder = state;
    myStateHolder.setState(getInitialState());
    myChangeListener = changeListener;
    myConfigurable = configurable;
  }

  @Override
  public NodeStateHolder.SelectedState getInitialState() {
    return myStateHolder.getPkg().isUpdate()
           ? NodeStateHolder.SelectedState.MIXED
           : myStateHolder.getPkg().hasLocal() ? NodeStateHolder.SelectedState.INSTALLED : NodeStateHolder.SelectedState.NOT_INSTALLED;
  }

  @Override
  public NodeStateHolder.SelectedState getCurrentState() {
    return myStateHolder.getState();
  }

  @Override
  public int compareTo(UpdaterTreeNode o) {
    if (!(o instanceof DetailsTreeNode)) {
      return toString().compareTo(o.toString());
    }
    return myStateHolder.getPkg().compareTo(((DetailsTreeNode)o).myStateHolder.getPkg());
  }

  @Override
  protected void setState(NodeStateHolder.SelectedState state) {
    myStateHolder.setState(state);
    if (myChangeListener != null) {
      myChangeListener.stateChanged(new ChangeEvent(this));
    }
  }

  @Override
  public boolean equals(Object obj) {
    if (!(obj instanceof DetailsTreeNode)) {
      return false;
    }
    return myStateHolder.getPkg().equals(((DetailsTreeNode)obj).myStateHolder.getPkg());
  }

  @Override
  public boolean includeInSummary() {
    return myStateHolder.getPkg().getRepresentative().getTypeDetails() instanceof DetailsTypes.SourceDetailsType ||
           myStateHolder.getPkg().getRepresentative().getTypeDetails() instanceof DetailsTypes.PlatformDetailsType;
  }

  @Override
  public boolean isPrimary() {
    return myStateHolder.getPkg().getRepresentative().getTypeDetails() instanceof DetailsTypes.PlatformDetailsType;
  }

  @Override
  public void customizeRenderer(Renderer renderer,
                                JTree tree,
                                boolean selected,
                                boolean expanded,
                                boolean leaf,
                                int row,
                                boolean hasFocus) {
    SimpleTextAttributes attributes = SimpleTextAttributes.REGULAR_ATTRIBUTES;
    String result;
    RepoPackage p = myStateHolder.getPkg().getRepresentative();
    result = p.getDisplayName();
    if (p.obsolete()) {
      result += " (Obsolete)";
    }

    renderer.getTextRenderer().append(result, attributes);
  }

  @NotNull
  public UpdatablePackage getItem() {
    return myStateHolder.getPkg();
  }

  @Override
  protected boolean canHaveMixedState() {
    return myStateHolder.getPkg().isUpdate();
  }

  @Override
  public String getStatusString() {
    if (getInitialState() == NodeStateHolder.SelectedState.INSTALLED) {
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

      if (getInitialState() == NodeStateHolder.SelectedState.NOT_INSTALLED) {
        return "Not installed";
      }
      else {
        // The initial state being mixed ensures we have a remote we care about
        //noinspection ConstantConditions
        return "Update Available: " + myStateHolder.getPkg().getRemote().getVersion();
      }
    }
  }

  public RepoPackage getPackage() {
    return getItem().getRepresentative();
  }
}
