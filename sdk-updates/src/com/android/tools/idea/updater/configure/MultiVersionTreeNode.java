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


import com.android.repository.api.UpdatablePackage;
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
  public NodeStateHolder.SelectedState getInitialState() {
    if (myMaxVersionNode == null) {  // can be the case before remote packages are downloaded
      return NodeStateHolder.SelectedState.NOT_INSTALLED;
    }
    if (myMaxVersionNode.getInitialState() == NodeStateHolder.SelectedState.INSTALLED) {
      return NodeStateHolder.SelectedState.INSTALLED;
    }
    for (UpdaterTreeNode node : myVersionNodes) {
      if (node.getInitialState() != NodeStateHolder.SelectedState.NOT_INSTALLED) {
        return NodeStateHolder.SelectedState.MIXED;
      }
    }
    return NodeStateHolder.SelectedState.NOT_INSTALLED;
  }

  @Override
  public NodeStateHolder.SelectedState getCurrentState() {
    if (myMaxVersionNode == null) {
      return NodeStateHolder.SelectedState.NOT_INSTALLED;
    }
    if (myMaxVersionNode.getCurrentState() == NodeStateHolder.SelectedState.INSTALLED) {
      return NodeStateHolder.SelectedState.INSTALLED;
    }
    for (UpdaterTreeNode node : myVersionNodes) {
      if (node.getCurrentState() != NodeStateHolder.SelectedState.NOT_INSTALLED) {
        return NodeStateHolder.SelectedState.MIXED;
      }
    }
    return NodeStateHolder.SelectedState.NOT_INSTALLED;
  }

  @Override
  protected boolean canHaveMixedState() {
    return getInitialState() == NodeStateHolder.SelectedState.MIXED;
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
    String maxName = myMaxVersionNode.getPackage().getDisplayName();
    int lastSpaceIndex = maxName.lastIndexOf(' ');
    if (lastSpaceIndex > 0 && lastSpaceIndex < maxName.length() && Character.isDigit(maxName.charAt(lastSpaceIndex + 1))) {
      // strip off the version number
      return maxName.substring(0, lastSpaceIndex);
    }
    else {
      return maxName;
    }
  }

  @Override
  public String getStatusString() {
    if (getInitialState() == NodeStateHolder.SelectedState.INSTALLED) {
      return "Installed";
    } else if (getInitialState() == NodeStateHolder.SelectedState.NOT_INSTALLED) {
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
  protected void setState(NodeStateHolder.SelectedState state) {
    if (state == NodeStateHolder.SelectedState.NOT_INSTALLED) {
      for (UpdaterTreeNode node : myVersionNodes) {
        node.setState(NodeStateHolder.SelectedState.NOT_INSTALLED);
      }
    }
    else {
      for (UpdaterTreeNode node : myVersionNodes) {
        node.resetState();
      }
      if (state == NodeStateHolder.SelectedState.INSTALLED) {
        myMaxVersionNode.setState(NodeStateHolder.SelectedState.INSTALLED);
      }
    }
  }
}
