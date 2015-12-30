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

import javax.swing.*;
import java.util.Set;

/**
 * TreeNode that displays summary information for build tools. It will show as installed if the latest build tools version is installed,
 * updatable if only non-latest versions are installed, or not installed if no versions are installed. Selecting it to be installed will
 * install the most recent version.
 */
class BuildToolsSummaryTreeNode extends UpdaterTreeNode {
  PlatformDetailsTreeNode myMaxVersionNode;
  Set<UpdaterTreeNode> myBuildToolsNodes;

  public BuildToolsSummaryTreeNode(Set<UpdaterTreeNode> buildToolsNodes) {
    myBuildToolsNodes = buildToolsNodes;
    for (UpdaterTreeNode node : myBuildToolsNodes) {
      if (myMaxVersionNode == null || node.compareTo(myMaxVersionNode) > 0) {
        myMaxVersionNode = (PlatformDetailsTreeNode)node;
      }
    }
  }

  @Override
  public NodeStateHolder.SelectedState getInitialState() {
    if (myMaxVersionNode == null) {  // can be the case before remote packages are downloaded
      return NodeStateHolder.SelectedState.NOT_INSTALLED;
    }
    if (myMaxVersionNode.getInitialState() == NodeStateHolder.SelectedState.INSTALLED) {
      return NodeStateHolder.SelectedState.INSTALLED;
    }
    for (UpdaterTreeNode node : myBuildToolsNodes) {
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
    for (UpdaterTreeNode node : myBuildToolsNodes) {
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
    renderer.getTextRenderer().append("Android SDK Build Tools");
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
      for (UpdaterTreeNode node : myBuildToolsNodes) {
        node.setState(NodeStateHolder.SelectedState.NOT_INSTALLED);
      }
    }
    else {
      for (UpdaterTreeNode node : myBuildToolsNodes) {
        node.resetState();
      }
      if (state == NodeStateHolder.SelectedState.INSTALLED) {
        myMaxVersionNode.setState(NodeStateHolder.SelectedState.INSTALLED);
      }
    }
  }
}
