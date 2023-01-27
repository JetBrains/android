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

import com.android.sdklib.AndroidVersion;
import com.android.sdklib.AndroidVersionUtils;
import com.intellij.ui.SimpleTextAttributes;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.util.Enumeration;
import java.util.function.Function;

/**
 * A tree node used in {@link SdkUpdaterConfigurable}. Represents a summary view of several packages.
 */
class ParentTreeNode extends UpdaterTreeNode {
  private final AndroidVersion myVersion;
  private final String myTitle;
  private PackageNodeModel.SelectedState myInitialState;

  public ParentTreeNode(@NotNull AndroidVersion version) {
    myVersion = version;
    myTitle = null;
  }

  public ParentTreeNode(@NotNull String title) {
    myTitle = title;
    myVersion = null;
  }

  @NotNull
  private PackageNodeModel.SelectedState getState(@NotNull Function<UpdaterTreeNode, PackageNodeModel.SelectedState> childStateGetter) {
    boolean foundInstalled = false;
    boolean foundNotInstalled = false;
    for (Enumeration children = children(); children.hasMoreElements(); ) {
      UpdaterTreeNode child = (UpdaterTreeNode)children.nextElement();
      PackageNodeModel.SelectedState childState = childStateGetter.apply(child);
      if (childState != PackageNodeModel.SelectedState.INSTALLED) {
        foundNotInstalled = true;
      }
      if (childState != PackageNodeModel.SelectedState.NOT_INSTALLED) {
        foundInstalled = true;
      }
    }
    if (foundInstalled && foundNotInstalled) {
      return PackageNodeModel.SelectedState.MIXED;
    }
    else if (foundInstalled) {
      return PackageNodeModel.SelectedState.INSTALLED;
    }
    else {
      return PackageNodeModel.SelectedState.NOT_INSTALLED;
    }
  }

  @Override
  @NotNull
  public PackageNodeModel.SelectedState getInitialState() {
    if (myInitialState == null) {
      myInitialState = getState(UpdaterTreeNode::getInitialState);
    }
    return myInitialState;
  }

  @Override
  protected boolean canHaveMixedState() {
    return getInitialState() == PackageNodeModel.SelectedState.MIXED;
  }

  @Override
  @NotNull
  public PackageNodeModel.SelectedState getCurrentState() {
    return getState(UpdaterTreeNode::getCurrentState);
  }

  @Override
  public int compareTo(@NotNull UpdaterTreeNode other) {
    if (!(other instanceof ParentTreeNode)) {
      return super.compareTo(other);
    }
    if (myVersion == null) {
      return ((ParentTreeNode)other).myVersion == null ? 0 : -1;
    }
    if (((ParentTreeNode)other).myVersion == null) {
      return 1;
    }
    return myVersion.compareTo(((ParentTreeNode)other).myVersion);
  }

  @Override
  public void customizeRenderer(Renderer renderer,
                                JTree tree,
                                boolean selected,
                                boolean expanded,
                                boolean leaf,
                                int row,
                                boolean hasFocus) {
    String title = myTitle;
    if (title == null) {
      title = AndroidVersionUtils.getFullReleaseName(myVersion, false, true);
    }
    renderer.getTextRenderer()
      .append(title, SimpleTextAttributes.REGULAR_BOLD_ATTRIBUTES);
  }

  @Override
  protected void setState(PackageNodeModel.SelectedState state) {
    for (Enumeration children = children(); children.hasMoreElements(); ) {
      UpdaterTreeNode child = (UpdaterTreeNode)children.nextElement();
      child.setState(state == PackageNodeModel.SelectedState.MIXED ? child.getInitialState() : state);
    }
  }
}
