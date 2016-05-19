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

import com.android.repository.impl.meta.TypeDetails;
import com.android.sdklib.AndroidVersion;
import com.android.sdklib.SdkVersionInfo;
import com.android.sdklib.repository.meta.DetailsTypes;
import com.google.common.collect.Sets;

import javax.swing.*;
import java.util.Set;

/**
 * Tree node representing all packages corresponding to a specified AndroidVersion. The checked state and the effect of
 * checking/unchecking this node is taken from and applies to children where {@code includeInSummary()} is true. The revision
 * number will be taken from a child where {@code isPrimary()} is true.
 */
class SummaryTreeNode extends UpdaterTreeNode {
  private AndroidVersion myVersion;
  private Set<UpdaterTreeNode> myAllChildren;
  private Set<UpdaterTreeNode> myIncludedChildren = Sets.newHashSet();
  private UpdaterTreeNode myPrimaryChild;

  /**
   * Factory method to create SummaryTreeNodes.
   *
   * @param version The AndroidVersion of this node
   * @param children The nodes represented by this summary node.
   * @param versionName The version name to be shown in the UI.
   * @return A new SummaryTreeNode, or null if none of the children are actually included.
   */
  public static SummaryTreeNode createNode(AndroidVersion version, Set<UpdaterTreeNode> children) {
    Set<UpdaterTreeNode> includedChildren = Sets.newHashSet();
    UpdaterTreeNode primaryChild = null;
    for (UpdaterTreeNode child : children) {
      if (child.includeInSummary()) {
        includedChildren.add(child);
      }
      if (child.isPrimary()) {
        primaryChild = child;
      }
    }

    if (!includedChildren.isEmpty()) {
      return new SummaryTreeNode(version, children, includedChildren, primaryChild);
    }
    return null;
  }

  protected SummaryTreeNode(AndroidVersion version, Set<UpdaterTreeNode> children, Set<UpdaterTreeNode> includedChildren,
                            UpdaterTreeNode primaryChild) {
    myVersion = version;
    myAllChildren = children;
    myIncludedChildren = includedChildren;
    myPrimaryChild = primaryChild;
  }

  @Override
  public NodeStateHolder.SelectedState getInitialState() {
    boolean hasNeedsUpdate = false;
    for (UpdaterTreeNode summaryNode : myIncludedChildren) {
      if (summaryNode.getInitialState() == NodeStateHolder.SelectedState.NOT_INSTALLED) {
        return NodeStateHolder.SelectedState.NOT_INSTALLED;
      }
      if (summaryNode.getInitialState() == NodeStateHolder.SelectedState.MIXED) {
        hasNeedsUpdate = true;
      }
    }
    return hasNeedsUpdate ? NodeStateHolder.SelectedState.MIXED : NodeStateHolder.SelectedState.INSTALLED;
  }

  @Override
  public NodeStateHolder.SelectedState getCurrentState() {
    boolean hasNeedsUpdate = false;
    for (UpdaterTreeNode summaryNode : myIncludedChildren) {
      if (summaryNode.getCurrentState() == NodeStateHolder.SelectedState.NOT_INSTALLED) {
        return NodeStateHolder.SelectedState.NOT_INSTALLED;
      }
      if (summaryNode.getCurrentState() == NodeStateHolder.SelectedState.MIXED) {
        hasNeedsUpdate = true;
      }
    }
    return hasNeedsUpdate ? NodeStateHolder.SelectedState.MIXED : NodeStateHolder.SelectedState.INSTALLED;
  }

  @Override
  public int compareTo(UpdaterTreeNode o) {
    if (!(o instanceof SummaryTreeNode)) {
      return super.compareTo(o);
    }
    return myVersion.compareTo(((SummaryTreeNode)o).myVersion);
  }

  @Override
  public boolean equals(Object obj) {
    if (!(obj instanceof SummaryTreeNode)) {
      return false;
    }
    return myVersion.equals(((SummaryTreeNode)obj).myVersion);
  }

  @Override
  public void customizeRenderer(Renderer renderer, JTree tree, boolean selected, boolean expanded, boolean leaf, int row, boolean hasFocus) {
    renderer.getTextRenderer().append(getDescription(myVersion));
  }

  public AndroidVersion getVersion() {
    return myVersion;
  }

  @Override
  protected void setState(NodeStateHolder.SelectedState state) {
    boolean hasOrigNotInstalled = false;
    for (UpdaterTreeNode summaryTreeNode : myIncludedChildren) {
      if (summaryTreeNode.getInitialState() == NodeStateHolder.SelectedState.NOT_INSTALLED) {
        hasOrigNotInstalled = true;
      }
    }

    // In most cases setting the summary resets all packages to their initial state.
    // In the mixed case, we know this is all we need to do.
    for (UpdaterTreeNode child : myAllChildren) {
      child.resetState();
    }

    if (state == NodeStateHolder.SelectedState.NOT_INSTALLED) {
      if (!hasOrigNotInstalled) {
        // We originally were completely installed, so remove all packages to uninstall.
        for (UpdaterTreeNode child : myAllChildren) {
          child.setState(NodeStateHolder.SelectedState.NOT_INSTALLED);
        }
      }
    }
    if (state == NodeStateHolder.SelectedState.INSTALLED) {
      // install included packages
      for (UpdaterTreeNode child : myIncludedChildren) {
        child.setState(NodeStateHolder.SelectedState.INSTALLED);
      }
    }
  }

  @Override
  protected boolean canHaveMixedState() {
    for (UpdaterTreeNode child : myIncludedChildren) {
      if (child.canHaveMixedState()) {
        return true;
      }
    }
    return false;
  }

  @Override
  public String getStatusString() {
    boolean foundSources = false;
    boolean foundPlatform = false;
    boolean foundUpdate = false;
    for (UpdaterTreeNode child : myAllChildren) {
      if (child.getInitialState() != NodeStateHolder.SelectedState.NOT_INSTALLED) {
        TypeDetails details = ((DetailsTreeNode)child).getPackage().getTypeDetails();
        if (details instanceof DetailsTypes.SourceDetailsType) {
          foundSources = true;
        } else if (details instanceof DetailsTypes.PlatformDetailsType) {
          foundPlatform = true;
        }
        if (child.getInitialState() == NodeStateHolder.SelectedState.MIXED) {
          foundUpdate = true;
        }
      }
    }
    if (foundUpdate) {
      return "Update available";
    }
    if (foundPlatform && (foundSources || myVersion.getApiLevel() < 14)) {
      // APIs before 14 don't have separate sources
      return "Installed";
    }
    if (foundPlatform || foundSources) {
      return "Partially installed";
    }
    return "Not installed";
  }

  public UpdaterTreeNode getPrimaryChild() {
    return myPrimaryChild;
  }

  public static String getDescription(AndroidVersion version) {
    StringBuilder result = new StringBuilder();
    result.append("Android ");
    if (version.isPreview()) {
      result.append(version.getCodename());
      result.append(" Preview");
    }
    else {
      result.append(SdkVersionInfo.getVersionString(version.getFeatureLevel()));
      String codeName = version.getCodename();
      if (codeName == null) {
        codeName = SdkVersionInfo.getCodeName(version.getFeatureLevel());
      }
      if (codeName != null) {
        result.append(" (");
        result.append(codeName);
        result.append(")");
      }
    }
    return result.toString();
  }
}
