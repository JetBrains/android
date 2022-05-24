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

import com.android.ide.common.repository.GradleVersion;
import com.android.repository.api.RepoPackage;
import com.android.repository.api.UpdatablePackage;
import com.android.sdklib.repository.AndroidSdkHandler;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.util.containers.ContainerUtil;
import java.util.Collection;
import java.util.Comparator;
import java.util.function.Function;
import javax.swing.JTree;
import org.jetbrains.annotations.NotNull;

/**
 * TreeNode that displays summary information for packages with multiple versions available. It will show as installed if the latest version
 * is installed, updatable if only non-latest versions are installed, or not installed if no versions are installed. Selecting it to be
 * installed will install the most recent version.
 */
class MultiVersionTreeNode extends UpdaterTreeNode {
  private final DetailsTreeNode myMaxVersionNode;
  private final Collection<DetailsTreeNode> myVersionNodes;

  public MultiVersionTreeNode(@NotNull Collection<DetailsTreeNode> versionNodes) {
    myVersionNodes = versionNodes;
    DetailsTreeNode max = myVersionNodes.stream().filter(node -> node.getPackage().getPath().endsWith("latest")).findFirst().orElse(null);
    if (max == null) {
      RepoPackage greatestPackage = AndroidSdkHandler.getLatestPackageFromPrefixCollection(
        ContainerUtil.map(myVersionNodes, DetailsTreeNode::getPackage),
        null,
        true,
        GradleVersion::tryParse,
        Comparator.nullsFirst(Comparator.naturalOrder()));
      max = myVersionNodes.stream().filter(node -> node.getPackage() == greatestPackage).findFirst().orElse(null);
    }
    myMaxVersionNode = max;
  }

  @NotNull
  private PackageNodeModel.SelectedState getState(@NotNull Function<UpdaterTreeNode, PackageNodeModel.SelectedState> childStateGetter) {
    if (myMaxVersionNode == null) {  // can be the case before remote packages are downloaded
      return PackageNodeModel.SelectedState.NOT_INSTALLED;
    }
    if (childStateGetter.apply(myMaxVersionNode) == PackageNodeModel.SelectedState.INSTALLED) {
      return PackageNodeModel.SelectedState.INSTALLED;
    }
    for (UpdaterTreeNode node : myVersionNodes) {
      if (childStateGetter.apply(node) != PackageNodeModel.SelectedState.NOT_INSTALLED) {
        return PackageNodeModel.SelectedState.MIXED;
      }
    }
    return PackageNodeModel.SelectedState.NOT_INSTALLED;
  }

  @Override
  @NotNull
  public PackageNodeModel.SelectedState getInitialState() {
    return getState(UpdaterTreeNode::getInitialState);
  }

  @Override
  @NotNull
  public PackageNodeModel.SelectedState getCurrentState() {
    return getState(UpdaterTreeNode::getCurrentState);
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
  @NotNull
  public String getStatusString() {
    if (getInitialState() == PackageNodeModel.SelectedState.INSTALLED) {
      return "Installed";
    } else if (getInitialState() == PackageNodeModel.SelectedState.NOT_INSTALLED) {
      return "Not Installed";
    } else {
      String revision;
      UpdatablePackage p = myMaxVersionNode.getItem();
      if (p.hasRemote()) {
        //noinspection ConstantConditions
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
