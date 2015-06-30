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

import com.android.sdklib.repository.descriptors.IPkgDesc;
import com.android.sdklib.repository.descriptors.PkgType;
import com.android.sdklib.repository.local.LocalPlatformPkgInfo;
import com.android.tools.idea.sdk.remote.UpdatablePkgInfo;
import com.android.tools.idea.sdk.remote.internal.packages.RemotePlatformPkgInfo;
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
class PlatformDetailsTreeNode extends UpdaterTreeNode {
  private NodeStateHolder myStateHolder;
  private boolean myIncludePreview;
  private final ChangeListener myChangeListener;

  public PlatformDetailsTreeNode(@NotNull NodeStateHolder state, boolean includePreview, @Nullable ChangeListener changeListener) {
    myStateHolder = state;
    myIncludePreview = includePreview;
    myStateHolder.setState(getInitialState());
    myChangeListener = changeListener;
  }

  @Override
  public NodeStateHolder.SelectedState getInitialState() {
    return myStateHolder.getPkg().hasRemote(myIncludePreview) && myStateHolder.getPkg().hasLocal()
           ? NodeStateHolder.SelectedState.MIXED
           : myStateHolder.getPkg().hasLocal() ? NodeStateHolder.SelectedState.INSTALLED : NodeStateHolder.SelectedState.NOT_INSTALLED;
  }

  @Override
  public NodeStateHolder.SelectedState getCurrentState() {
    return myStateHolder.getState();
  }

  @Override
  public int compareTo(UpdaterTreeNode o) {
    if (!(o instanceof PlatformDetailsTreeNode)) {
      return toString().compareTo(o.toString());
    }
    return myStateHolder.getPkg().compareTo(((PlatformDetailsTreeNode)o).myStateHolder.getPkg());
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
    if (!(obj instanceof PlatformDetailsTreeNode)) {
      return false;
    }
    return myStateHolder.getPkg().equals(((PlatformDetailsTreeNode)obj).myStateHolder.getPkg());
  }

  @Override
  public boolean includeInSummary() {
    return myStateHolder.getPkg().getPkgDesc(true).getType() == PkgType.PKG_SOURCE ||
           myStateHolder.getPkg().getPkgDesc(true).getType() == PkgType.PKG_PLATFORM;
  }

  @Override
  public boolean isPrimary() {
    return myStateHolder.getPkg().getPkgDesc(true).getType() == PkgType.PKG_PLATFORM;
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
    UpdatablePkgInfo p = myStateHolder.getPkg();
    String result;
    if (p.getPkgDesc(true).getType() == PkgType.PKG_PLATFORM) {
      String versionName;
      if (p.hasLocal()) {
        versionName = ((LocalPlatformPkgInfo)p.getLocalInfo()).getAndroidTarget().getVersionName();
      }
      else {
        versionName = ((RemotePlatformPkgInfo)p.getRemote(myIncludePreview)).getVersionName();
      }
      result = String.format("Android %s Platform", versionName);
      if (p.getPkgDesc(myIncludePreview).isObsolete()) {
        result += " (Obsolete)";
      }
    }
    else {
      result = p.getPkgDesc(myIncludePreview).getListDescription();
    }
    renderer.getTextRenderer().append(result, attributes);
  }

  @NotNull
  public UpdatablePkgInfo getItem() {
    return myStateHolder.getPkg();
  }

  @Override
  protected boolean canHaveMixedState() {
    return myStateHolder.getPkg().hasRemote(myIncludePreview) && myStateHolder.getPkg().hasLocal();
  }

  @Override
  public String getStatusString() {
    if (getInitialState() == NodeStateHolder.SelectedState.NOT_INSTALLED) {
      return "Not installed";
    }
    else if (getInitialState() == NodeStateHolder.SelectedState.MIXED) {
      return "Update Available: " + myStateHolder.getPkg().getRemote(myIncludePreview).getRevision();
    }
    else {
      return "Installed";
    }
  }

  public IPkgDesc getItemDesc() {
    return getItem().getPkgDesc(myIncludePreview);
  }
}
