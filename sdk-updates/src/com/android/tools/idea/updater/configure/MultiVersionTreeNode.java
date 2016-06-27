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


import com.android.repository.api.RepoPackage;
import com.android.repository.api.UpdatablePackage;
import com.intellij.openapi.util.text.StringUtil;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.util.Collection;

/**
 * TreeNode that displays summary information for packages with multiple versions available. It will show as installed if the latest version
 * is installed, updatable if only non-latest versions are installed, or not installed if no versions are installed. Selecting it to be
 * installed will install the most recent version.
 */
class MultiVersionTreeNode extends UpdaterTreeNode {
  private final DetailsTreeNode myMaxVersionNode;
  private final Collection<DetailsTreeNode> myVersionNodes;

  public MultiVersionTreeNode(Collection<DetailsTreeNode> versionNodes) {
    myVersionNodes = versionNodes;
    myMaxVersionNode = myVersionNodes.stream().max(UpdaterTreeNode::compareTo).orElse(null);
  }

  @Override
  public PackageNodeModel.SelectedState getInitialState() {
    if (myMaxVersionNode == null) {  // can be the case before remote packages are downloaded
      return PackageNodeModel.SelectedState.NOT_INSTALLED;
    }
    if (myMaxVersionNode.getInitialState() == PackageNodeModel.SelectedState.INSTALLED) {
      return PackageNodeModel.SelectedState.INSTALLED;
    }
    for (UpdaterTreeNode node : myVersionNodes) {
      if (node.getInitialState() != PackageNodeModel.SelectedState.NOT_INSTALLED) {
        return PackageNodeModel.SelectedState.MIXED;
      }
    }
    return PackageNodeModel.SelectedState.NOT_INSTALLED;
  }

  @Override
  public PackageNodeModel.SelectedState getCurrentState() {
    if (myMaxVersionNode == null) {
      return PackageNodeModel.SelectedState.NOT_INSTALLED;
    }
    if (myMaxVersionNode.getCurrentState() == PackageNodeModel.SelectedState.INSTALLED) {
      return PackageNodeModel.SelectedState.INSTALLED;
    }
    for (UpdaterTreeNode node : myVersionNodes) {
      if (node.getCurrentState() != PackageNodeModel.SelectedState.NOT_INSTALLED) {
        return PackageNodeModel.SelectedState.MIXED;
      }
    }
    return PackageNodeModel.SelectedState.NOT_INSTALLED;
  }

  @Override
  protected boolean canHaveMixedState() {
    return getInitialState() == PackageNodeModel.SelectedState.MIXED;
  }

  @Override
  public void customizeRenderer(Renderer renderer,
                                JTree tree,
                                boolean selected,
                                boolean expanded,
                                boolean leaf,
                                int row,
                                boolean hasFocus) {
    renderer.getTextRenderer().append(getDisplayName());
  }

  @NotNull
  public String getDisplayName() {
    RepoPackage maxPackage = myMaxVersionNode.getPackage();
    String maxName = maxPackage.getDisplayName();
    String maxPath = maxPackage.getPath();
    String suffix = maxPath.substring(maxPath.lastIndexOf(RepoPackage.PATH_SEPARATOR) + 1);
    maxName = StringUtil.trimEnd(maxName, suffix).trim();
    maxName = StringUtil.trimEnd(maxName, ":");
    return maxName;
  }

  @Override
  public String getStatusString() {
    if (getInitialState() == PackageNodeModel.SelectedState.INSTALLED) {
      return "Installed";
    } else if (getInitialState() == PackageNodeModel.SelectedState.NOT_INSTALLED) {
      return "Not Installed";
    } else {
      String revision;
      UpdatablePackage p = myMaxVersionNode.getItem();
      if (p.hasRemote()) {
        revision = p.getRemote().getVersion().toString();
      }
      else {
        assert false;
        revision = p.getLocal().getVersion().toString();
      }
      return "Update Available: " + revision;
    }
  }

  @Override
  protected void setState(PackageNodeModel.SelectedState state) {
    if (state == PackageNodeModel.SelectedState.NOT_INSTALLED) {
      for (UpdaterTreeNode node : myVersionNodes) {
        node.setState(PackageNodeModel.SelectedState.NOT_INSTALLED);
      }
    }
    else {
      for (UpdaterTreeNode node : myVersionNodes) {
        node.resetState();
      }
      if (state == PackageNodeModel.SelectedState.INSTALLED) {
        myMaxVersionNode.setState(PackageNodeModel.SelectedState.INSTALLED);
      }
    }
  }
}
