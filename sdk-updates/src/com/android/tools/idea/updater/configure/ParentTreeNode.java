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
import com.intellij.ui.SimpleTextAttributes;

import javax.swing.*;
import java.util.Enumeration;

/**
 * A tree node used in {@link SdkUpdaterConfigurable}. Represents a summary view of several packages.
 */
class ParentTreeNode extends UpdaterTreeNode {
  private AndroidVersion myVersion;

  public ParentTreeNode(AndroidVersion version) {
    myVersion = version;
  }

  @Override
  public NodeStateHolder.SelectedState getInitialState() {
    boolean hasInstalled = false;
    boolean hasNotInstalled = false;
    for (Enumeration children = children(); children.hasMoreElements(); ) {
      UpdaterTreeNode child = (UpdaterTreeNode)children.nextElement();
      if (child.getInitialState() == NodeStateHolder.SelectedState.MIXED) {
        return NodeStateHolder.SelectedState.MIXED;
      }
      else if (child.getInitialState() == NodeStateHolder.SelectedState.INSTALLED) {
        hasInstalled = true;
      }
      else {
        hasNotInstalled = true;
      }
    }
    return hasInstalled
           ? (hasNotInstalled ? NodeStateHolder.SelectedState.MIXED : NodeStateHolder.SelectedState.INSTALLED)
           : NodeStateHolder.SelectedState.NOT_INSTALLED;
  }

  @Override
  public NodeStateHolder.SelectedState getCurrentState() {
    boolean foundInstalled = false;
    boolean foundNotInstalled = false;
    for (Enumeration children = children(); children.hasMoreElements(); ) {
      UpdaterTreeNode child = (UpdaterTreeNode)children.nextElement();
      if (child.getCurrentState() != NodeStateHolder.SelectedState.INSTALLED) {
        foundNotInstalled = true;
      }
      if (child.getCurrentState() != NodeStateHolder.SelectedState.NOT_INSTALLED) {
        foundInstalled = true;
      }
    }
    if (foundInstalled && foundNotInstalled) {
      return NodeStateHolder.SelectedState.MIXED;
    }
    else if (foundInstalled) {
      return NodeStateHolder.SelectedState.INSTALLED;
    }
    else {
      return NodeStateHolder.SelectedState.NOT_INSTALLED;
    }
  }

  @Override
  public int compareTo(UpdaterTreeNode o) {
    if (!(o instanceof ParentTreeNode)) {
      return super.compareTo(o);
    }
    return myVersion.compareTo(((ParentTreeNode)o).myVersion);
  }

  @Override
  public boolean equals(Object obj) {
    if (!(obj instanceof ParentTreeNode)) {
      return false;
    }
    if (myVersion != null) {
      return myVersion.equals(((ParentTreeNode)obj).myVersion);
    }
    return getStatusString().equals(((ParentTreeNode)obj).getStatusString());
  }

  @Override
  public void customizeRenderer(Renderer renderer,
                                JTree tree,
                                boolean selected,
                                boolean expanded,
                                boolean leaf,
                                int row,
                                boolean hasFocus) {
    renderer.getTextRenderer()
      .append(SummaryTreeNode.getDescription(myVersion), SimpleTextAttributes.REGULAR_BOLD_ATTRIBUTES);
  }

  @Override
  protected void setState(NodeStateHolder.SelectedState state) {
    for (Enumeration children = children(); children.hasMoreElements(); ) {
      UpdaterTreeNode child = (UpdaterTreeNode)children.nextElement();
      child.setState(state);
    }
  }
}
