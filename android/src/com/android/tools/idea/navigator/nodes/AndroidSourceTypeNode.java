/*
 * Copyright (C) 2014 The Android Open Source Project
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
package com.android.tools.idea.navigator.nodes;

import com.android.resources.ResourceFolderType;
import com.android.tools.idea.navigator.AndroidProjectTreeBuilder;
import com.android.tools.idea.navigator.AndroidProjectViewPane;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.google.common.collect.Sets;
import com.intellij.ide.projectView.PresentationData;
import com.intellij.ide.projectView.ProjectViewNode;
import com.intellij.ide.projectView.ViewSettings;
import com.intellij.ide.projectView.impl.nodes.ProjectViewDirectoryHelper;
import com.intellij.ide.util.treeView.AbstractTreeNode;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.Queryable;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.vfs.VfsUtilCore;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiDirectory;
import com.intellij.psi.PsiManager;
import com.intellij.ui.SimpleTextAttributes;
import org.jetbrains.android.facet.AndroidFacet;
import org.jetbrains.android.facet.AndroidSourceType;
import org.jetbrains.android.facet.IdeaSourceProvider;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.util.*;

/**
 * {@link AndroidSourceTypeNode} is a virtual node in the package view of an Android module under which all sources
 * corresponding to a particular {@link org.jetbrains.android.facet.AndroidSourceType} are grouped together.
 */
public class AndroidSourceTypeNode extends ProjectViewNode<AndroidFacet> implements AndroidProjectViewNode {
  public static final Key<IdeaSourceProvider> SOURCE_PROVIDER = Key.create("Android.SourceProvider");

  @NotNull private final AndroidSourceType mySourceType;
  @NotNull private final List<IdeaSourceProvider> mySourceProviders;
  @NotNull private final AndroidProjectViewPane myProjectViewPane;

  public AndroidSourceTypeNode(@NotNull Project project,
                               @NotNull AndroidFacet facet,
                               @NotNull ViewSettings viewSettings,
                               @NotNull AndroidSourceType sourceType,
                               @NotNull List<IdeaSourceProvider> sourceProviders,
                               @NotNull AndroidProjectViewPane projectViewPane) {
    super(project, facet, viewSettings);
    mySourceType = sourceType;
    mySourceProviders = sourceProviders;
    myProjectViewPane = projectViewPane;
  }

  @NotNull
  @Override
  public Collection<? extends AbstractTreeNode> getChildren() {
    assert myProject != null;

    switch (mySourceType) {
      case RES:
        return getMergedRes();
      case MANIFEST:
        // Manifest nodes are handled under the AndroidManifestsGroupNode.
        throw new IllegalStateException();
      default:
        return getProjectViewChildren();
    }
  }

  @NotNull
  public Collection<? extends AbstractTreeNode> getProjectViewChildren() {
    List<AbstractTreeNode> children = Lists.newArrayList();
    ProjectViewDirectoryHelper projectViewDirectoryHelper = ProjectViewDirectoryHelper.getInstance(myProject);
    AndroidProjectTreeBuilder treeBuilder = (AndroidProjectTreeBuilder)myProjectViewPane.getTreeBuilder();

    for (PsiDirectory directory : getSourceDirectories(myProject, mySourceProviders, mySourceType)) {
      Collection<AbstractTreeNode> directoryChildren = projectViewDirectoryHelper.getDirectoryChildren(directory, getSettings(), true);
      children.addAll(directoryChildren);

      // Inform the tree builder of the node that this particular virtual file maps to
      treeBuilder.createMapping(directory.getVirtualFile(), this);
    }

    return children;
  }

  /**
   * Returns the children of the res folder. Rather than showing the existing directory hierarchy, this merges together
   * all the folders by their {@link com.android.resources.ResourceFolderType}.
   */
  public Collection<? extends AbstractTreeNode> getMergedRes() {
    // collect all res folders from all source providers
    List<PsiDirectory> resFolders = Lists.newArrayList();
    for (PsiDirectory directory : getSourceDirectories(myProject, mySourceProviders, mySourceType)) {
      resFolders.addAll(Lists.newArrayList(directory.getSubdirectories()));
    }

    // group all the res folders by their folder type
    EnumMap<ResourceFolderType, Set<PsiDirectory>> foldersByResourceType = Maps.newEnumMap(ResourceFolderType.class);
    for (PsiDirectory resFolder : resFolders) {
      ResourceFolderType type = ResourceFolderType.getFolderType(resFolder.getName());
      Set<PsiDirectory> folders = foldersByResourceType.get(type);
      if (folders == null) {
        folders = Sets.newHashSet();
        foldersByResourceType.put(type, folders);
      }
      folders.add(resFolder);
    }

    // create a node for each res folder type that actually has some resources
    AndroidProjectTreeBuilder treeBuilder = (AndroidProjectTreeBuilder)myProjectViewPane.getTreeBuilder();
    List<AbstractTreeNode> children = Lists.newArrayListWithExpectedSize(foldersByResourceType.size());
    for (ResourceFolderType type : foldersByResourceType.keySet()) {
      Set<PsiDirectory> folders = foldersByResourceType.get(type);
      final AndroidResFolderTypeNode androidResFolderTypeNode =
        new AndroidResFolderTypeNode(myProject, getValue(), Lists.newArrayList(folders), getSettings(), type, myProjectViewPane);
      children.add(androidResFolderTypeNode);

      // Inform the tree builder of the node that this particular virtual file maps to
      for (PsiDirectory folder : folders) {
        treeBuilder.createMapping(folder.getVirtualFile(), androidResFolderTypeNode);
      }
    }
    return children;
  }

  private static List<PsiDirectory> getSourceDirectories(@NotNull Project project,
                                                         @NotNull List<IdeaSourceProvider> sourceProviders,
                                                         @NotNull AndroidSourceType sourceType) {
    PsiManager psiManager = PsiManager.getInstance(project);
    List<PsiDirectory> psiDirectories = Lists.newArrayList();

    for (IdeaSourceProvider sourceProvider : sourceProviders) {
      for (VirtualFile file : sourceType.getSources(sourceProvider)) {
        final PsiDirectory directory = psiManager.findDirectory(file);
        if (directory != null) {
          psiDirectories.add(directory);
          directory.putUserData(SOURCE_PROVIDER, sourceProvider);
        }
      }
    }

    return psiDirectories;
  }

  @Override
  protected void update(PresentationData presentation) {
    presentation.addText(mySourceType.getName(), SimpleTextAttributes.REGULAR_ATTRIBUTES);

    Icon icon = mySourceType.getIcon();
    if (icon != null) {
      presentation.setIcon(icon);
    }
    presentation.setPresentableText(mySourceType.getName());
  }

  @Nullable
  @Override
  public String toTestString(@Nullable Queryable.PrintInfo printInfo) {
    return mySourceType.getName();
  }

  @Override
  public boolean contains(@NotNull VirtualFile file) {
    //TODO: first check if the file is of my source type

    for (IdeaSourceProvider sourceProvider : mySourceProviders) {
      for (VirtualFile folder : mySourceType.getSources(sourceProvider)) {
        if (VfsUtilCore.isAncestor(folder, file, false)) {
          return true;
        }
      }
    }

    return false;
  }

  @Nullable
  @Override
  public Comparable getSortKey() {
    return mySourceType;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;
    if (!super.equals(o)) return false;

    AndroidSourceTypeNode that = (AndroidSourceTypeNode)o;

    if (mySourceType != that.mySourceType) return false;
    return mySourceProviders.equals(that.mySourceProviders);
  }

  @Override
  public int hashCode() {
    int result = super.hashCode();
    result = 31 * result + mySourceType.hashCode();
    for (IdeaSourceProvider provider : mySourceProviders) {
      result = 31 * result + provider.hashCode();
    }
    return result;
  }

  @NotNull
  @Override
  public AndroidFacet getAndroidFacet() {
    return getValue();
  }

  @NotNull
  @Override
  public PsiDirectory[] getDirectories() {
    PsiManager psiManager = PsiManager.getInstance(myProject);
    Set<PsiDirectory> folders = Sets.newHashSet();

    for (IdeaSourceProvider provider : mySourceProviders) {
      for (VirtualFile vf : mySourceType.getSources(provider)) {
        PsiDirectory folder = psiManager.findDirectory(vf);
        if (folder != null) {
          folders.add(folder);
        }
      }
    }

    return folders.toArray(new PsiDirectory[folders.size()]);
  }
}
