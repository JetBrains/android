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

import com.android.annotations.NonNull;
import com.android.repository.impl.meta.TypeDetails;
import com.android.sdklib.AndroidVersion;
import com.android.sdklib.AndroidVersionUtils;
import com.android.sdklib.repository.meta.DetailsTypes;
import com.google.common.collect.Sets;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.util.Set;
import java.util.function.Function;

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
   * @return A new SummaryTreeNode, or null if none of the children are actually included.
   */
  public static SummaryTreeNode createNode(@NotNull AndroidVersion version, @NotNull Set<UpdaterTreeNode> children) {
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

  protected SummaryTreeNode(@NotNull AndroidVersion version, @NotNull Set<UpdaterTreeNode> children,
                            @NotNull Set<UpdaterTreeNode> includedChildren, @Nullable UpdaterTreeNode primaryChild) {
    myVersion = version;
    myAllChildren = children;
    myIncludedChildren = includedChildren;
    myPrimaryChild = primaryChild;
  }

  @NonNull
  private PackageNodeModel.SelectedState getState(@NotNull Function<UpdaterTreeNode, PackageNodeModel.SelectedState> childStateGetter) {
    boolean hasNeedsUpdate = false;
    for (UpdaterTreeNode summaryNode : myIncludedChildren) {
      if (childStateGetter.apply(summaryNode) == PackageNodeModel.SelectedState.NOT_INSTALLED) {
        return PackageNodeModel.SelectedState.NOT_INSTALLED;
      }
      if (childStateGetter.apply(summaryNode) == PackageNodeModel.SelectedState.MIXED) {
        hasNeedsUpdate = true;
      }
    }
    return hasNeedsUpdate ? PackageNodeModel.SelectedState.MIXED : PackageNodeModel.SelectedState.INSTALLED;
  }

  @Override
  @NotNull
  public PackageNodeModel.SelectedState getInitialState() {
    return getState(UpdaterTreeNode::getInitialState);
  }

  @Override
  @NonNull
  public PackageNodeModel.SelectedState getCurrentState() {
    return getState(UpdaterTreeNode::getCurrentState);
  }

  @Override
  public int compareTo(@NotNull UpdaterTreeNode o) {
    if (!(o instanceof SummaryTreeNode)) {
      return super.compareTo(o);
    }
    return myVersion.compareTo(((SummaryTreeNode)o).myVersion);
  }

  @Override
  public void customizeRenderer(Renderer renderer, JTree tree, boolean selected, boolean expanded, boolean leaf, int row, boolean hasFocus) {
    renderer.getTextRenderer().append(AndroidVersionUtils.getFullReleaseName(myVersion, false, true));
  }

  public AndroidVersion getVersion() {
    return myVersion;
  }

  @Override
  protected void setState(PackageNodeModel.SelectedState state) {
    boolean hasOrigNotInstalled = false;
    for (UpdaterTreeNode summaryTreeNode : myIncludedChildren) {
      if (summaryTreeNode.getInitialState() == PackageNodeModel.SelectedState.NOT_INSTALLED) {
        hasOrigNotInstalled = true;
      }
    }

    // In most cases setting the summary resets all packages to their initial state.
    // In the mixed case, we know this is all we need to do.
    for (UpdaterTreeNode child : myAllChildren) {
      child.resetState();
    }

    if (state == PackageNodeModel.SelectedState.NOT_INSTALLED) {
      if (!hasOrigNotInstalled) {
        // We originally were completely installed, so remove all packages to uninstall.
        for (UpdaterTreeNode child : myAllChildren) {
          child.setState(PackageNodeModel.SelectedState.NOT_INSTALLED);
        }
      }
    }
    if (state == PackageNodeModel.SelectedState.INSTALLED) {
      // install included packages
      for (UpdaterTreeNode child : myIncludedChildren) {
        child.setState(PackageNodeModel.SelectedState.INSTALLED);
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
  @NotNull
  public String getStatusString() {
    boolean foundSources = false;
    boolean foundPlatform = false;
    boolean foundUpdate = false;
    boolean sourcesAvailable = false;
    for (UpdaterTreeNode child : myAllChildren) {
      TypeDetails details = ((DetailsTreeNode)child).getPackage().getTypeDetails();
      if (child.getInitialState() != PackageNodeModel.SelectedState.NOT_INSTALLED) {
        if (details instanceof DetailsTypes.SourceDetailsType) {
          foundSources = true;
        } else if (details instanceof DetailsTypes.PlatformDetailsType) {
          foundPlatform = true;
        }
        if (child.getInitialState() == PackageNodeModel.SelectedState.MIXED) {
          foundUpdate = true;
        }
      }
      if (details instanceof DetailsTypes.SourceDetailsType) {
        sourcesAvailable = true;
      }
    }
    if (foundUpdate) {
      return "Update available";
    }
    if (foundPlatform && (foundSources || !sourcesAvailable)) {
      // Sometimes sources might be not published for the given platform at all (e.g., if API < 14, or
      // for a platform being currently in preview stage). In that case, do not take sources into account
      // when determining platform installation status.
      return "Installed";
    }
    if (foundPlatform || foundSources) {
      return "Partially installed";
    }
    return "Not installed";
  }

  @Nullable
  public UpdaterTreeNode getPrimaryChild() {
    return myPrimaryChild;
  }
}
