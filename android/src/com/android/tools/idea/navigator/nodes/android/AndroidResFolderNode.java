/*
 * Copyright (C) 2017 The Android Open Source Project
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
package com.android.tools.idea.navigator.nodes.android;

import com.android.resources.ResourceFolderType;
import com.android.tools.idea.flags.StudioFlags;
import com.android.tools.idea.navigator.AndroidProjectTreeBuilder;
import com.android.tools.idea.navigator.AndroidProjectViewPane;
import com.android.tools.idea.resourceExplorer.editor.ResourceExplorerFile;
import com.google.common.collect.HashMultimap;
import com.intellij.ide.projectView.ViewSettings;
import com.intellij.ide.util.treeView.AbstractTreeNode;
import com.intellij.openapi.fileEditor.OpenFileDescriptor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiDirectory;
import org.jetbrains.android.facet.AndroidFacet;
import org.jetbrains.android.facet.AndroidSourceType;
import org.jetbrains.annotations.NotNull;

import java.util.*;

public class AndroidResFolderNode extends AndroidSourceTypeNode {
  AndroidResFolderNode(@NotNull Project project,
                       @NotNull AndroidFacet androidFacet,
                       @NotNull ViewSettings settings,
                       @NotNull Set<VirtualFile> sourceRoots,
                       @NotNull AndroidProjectViewPane projectViewPane) {
    super(project, androidFacet, settings, AndroidSourceType.RES, sourceRoots, projectViewPane);
  }

  /**
   * Returns the children of the res folder. Rather than showing the existing directory hierarchy, this merges together all the folders by
   * their {@link ResourceFolderType}.
   */
  @Override
  @NotNull
  public Collection<? extends AbstractTreeNode> getChildren() {
    // collect all res folders from all source providers
    List<PsiDirectory> resFolders = new ArrayList<>();
    for (PsiDirectory sourceFolder : getSourceFolders()) {
      resFolders.addAll(Arrays.asList(sourceFolder.getSubdirectories()));
    }

    // group all the res folders by their folder type
    HashMultimap<ResourceFolderType, PsiDirectory> foldersByResourceType = HashMultimap.create();
    for (PsiDirectory resFolder : resFolders) {
      ResourceFolderType type = ResourceFolderType.getFolderType(resFolder.getName());
      if (type == null) {
        // skip unknown folder types inside res
        continue;
      }
      foldersByResourceType.put(type, resFolder);
    }

    // create a node for each res folder type that actually has some resources
    AndroidProjectTreeBuilder treeBuilder = (AndroidProjectTreeBuilder)myProjectViewPane.getTreeBuilder();
    List<AbstractTreeNode> children = new ArrayList<>(foldersByResourceType.size());

    for (ResourceFolderType type : foldersByResourceType.keySet()) {
      Set<PsiDirectory> folders = foldersByResourceType.get(type);
      assert myProject != null;
      AndroidResFolderTypeNode node = new AndroidResFolderTypeNode(myProject, getAndroidFacet(), new ArrayList<>(folders), getSettings(),
                                                                   type);
      children.add(node);

      // Inform the tree builder of the node that this particular virtual file maps to
      for (PsiDirectory folder : folders) {
        treeBuilder.createMapping(folder.getVirtualFile(), node);
      }
    }
    return children;
  }

  @NotNull
  private AndroidFacet getAndroidFacet() {
    AndroidFacet facet = getValue();
    assert facet != null;
    return facet;
  }

  @Override
  public boolean expandOnDoubleClick() {
    return StudioFlags.RESOURCE_MANAGER_ENABLED.get() ? false : super.expandOnDoubleClick();
  }

  @Override
  public boolean canNavigate() {
    return StudioFlags.RESOURCE_MANAGER_ENABLED.get();
  }

  @Override
  public void navigate(boolean requestFocus) {
    if (myProject != null) {
      new OpenFileDescriptor(myProject, ResourceExplorerFile.getResourceEditorFile(myProject, getAndroidFacet())).navigate(requestFocus);
    }
  }
}
