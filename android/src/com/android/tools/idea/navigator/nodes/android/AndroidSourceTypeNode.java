/*
 * Copyright (C) 2019 The Android Open Source Project
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

import static com.android.tools.idea.projectsystem.SourceProvidersKt.findSourceRoot;
import static com.intellij.openapi.vfs.VfsUtilCore.isAncestor;
import static com.intellij.ui.SimpleTextAttributes.GRAY_ATTRIBUTES;
import static com.intellij.ui.SimpleTextAttributes.REGULAR_ATTRIBUTES;

import com.android.tools.idea.navigator.AndroidProjectViewPane;
import com.android.tools.idea.navigator.nodes.FolderGroupNode;
import com.android.tools.idea.navigator.nodes.GroupNodes;
import com.android.tools.idea.projectsystem.NamedIdeaSourceProvider;
import com.intellij.ide.projectView.PresentationData;
import com.intellij.ide.projectView.ProjectViewNode;
import com.intellij.ide.projectView.ViewSettings;
import com.intellij.ide.projectView.impl.nodes.ProjectViewDirectoryHelper;
import com.intellij.ide.projectView.impl.nodes.PsiDirectoryNode;
import com.intellij.ide.projectView.impl.nodes.PsiFileNode;
import com.intellij.ide.util.treeView.AbstractTreeNode;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.Queryable;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiDirectory;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiManager;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import javax.swing.Icon;
import org.jetbrains.android.facet.AndroidFacet;
import org.jetbrains.android.facet.AndroidSourceType;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import kotlin.Pair;

/**
 * {@link AndroidSourceTypeNode} is a virtual node in the package view of an Android module under which all sources
 * corresponding to a particular {@link AndroidSourceType} are grouped together.
 */
public class AndroidSourceTypeNode extends ProjectViewNode<AndroidFacet> implements FolderGroupNode {
  @NotNull private static final String GENERATED_SUFFIX = " (generated)";

  @NotNull private final AndroidSourceType mySourceType;
  @NotNull private final Set<VirtualFile> mySourceRoots;
  @NotNull protected final AndroidProjectViewPane myProjectViewPane;

  AndroidSourceTypeNode(@NotNull Project project,
                        @NotNull AndroidFacet androidFacet,
                        @NotNull ViewSettings settings,
                        @NotNull AndroidSourceType sourceType,
                        @NotNull Set<VirtualFile> sources,
                        @NotNull AndroidProjectViewPane projectViewPane) {
    super(project, androidFacet, settings);
    mySourceType = sourceType;
    mySourceRoots = sources;
    myProjectViewPane = projectViewPane;
  }

  @Override
  @NotNull
  public Collection<? extends AbstractTreeNode<?>> getChildren() {
    List<AbstractTreeNode<?>> children = new ArrayList<>();
    ProjectViewDirectoryHelper projectViewDirectoryHelper = ProjectViewDirectoryHelper.getInstance(myProject);

    for (PsiDirectory directory : getSourceFolders()) {
      Collection<AbstractTreeNode<?>> directoryChildren = projectViewDirectoryHelper.getDirectoryChildren(directory, getSettings(), true);

      children.addAll(annotateWithSourceProvider(directoryChildren));
    }

    return children;
  }

  @NotNull
  private Collection<AbstractTreeNode<?>> annotateWithSourceProvider(@NotNull Collection<AbstractTreeNode<?>> folderChildren) {
    List<AbstractTreeNode<?>> children = new ArrayList<>(folderChildren.size());
    assert myProject != null;
    for (AbstractTreeNode<?> child : folderChildren) {
      if (child instanceof PsiDirectoryNode) {
        PsiDirectory folder = ((PsiDirectoryNode)child).getValue();
        assert folder != null;
        Pair<String, VirtualFile> providerPair = findSourceProvider(folder.getVirtualFile());
        VirtualFile file = providerPair.getSecond();
        PsiDirectory psiDir = (file == null) ? null : PsiManager.getInstance(myProject).findDirectory(file);
        children.add(new AndroidPsiDirectoryNode(myProject, folder, getSettings(), providerPair.getFirst(), psiDir));
      }
      else if (child instanceof PsiFileNode) {
        PsiFile file = ((PsiFileNode)child).getValue();
        assert file != null;
        VirtualFile virtualFile = file.getVirtualFile();
        Pair<String, VirtualFile> providerPair = findSourceProvider(virtualFile);
        children.add(new AndroidPsiFileNode(myProject, file, getSettings(), providerPair.getFirst()));
      }
      else {
        children.add(child);
      }
    }

    return children;
  }

  @NotNull
  private Pair<String, VirtualFile> findSourceProvider(@NotNull VirtualFile virtualFile) {
    AndroidFacet androidFacet = getValue();
    assert androidFacet != null;
    for (NamedIdeaSourceProvider provider : AndroidProjectViewPane.getSourceProviders(androidFacet)) {
      VirtualFile root = findSourceRoot(provider, virtualFile);
      if (root != null) {
        return new Pair<>(provider.getName(), root);
      }
    }

    return new Pair<>(null, null);
  }

  @NotNull
  protected List<PsiDirectory> getSourceFolders() {
    assert myProject != null;
    PsiManager psiManager = PsiManager.getInstance(myProject);
    List<PsiDirectory> folders = new ArrayList<>(mySourceRoots.size());

    for (VirtualFile root : mySourceRoots) {
      if (!root.isValid()) {
        continue;
      }

      PsiDirectory folder = psiManager.findDirectory(root);
      if (folder != null) {
        folders.add(folder);
      }
    }

    return folders;
  }

  @Override
  protected void update(@NotNull PresentationData presentation) {
    presentation.addText(mySourceType.getName(), REGULAR_ATTRIBUTES);
    if (mySourceType.isGenerated()) {
      presentation.addText(GENERATED_SUFFIX, GRAY_ATTRIBUTES);
    }

    Icon icon = mySourceType.getIcon();
    if (icon != null) {
      presentation.setIcon(icon);
    }

    presentation.setPresentableText(this.toTestString(null));
  }

  @Override
  @Nullable
  public String toTestString(@Nullable Queryable.PrintInfo printInfo) {
    return mySourceType.isGenerated() ? mySourceType.getName() + GENERATED_SUFFIX : mySourceType.getName();
  }

  @Override
  public boolean contains(@NotNull VirtualFile file) {
    //TODO: first check if the file is of my source type

    for (VirtualFile root : mySourceRoots) {
      if (isAncestor(root, file, false)) {
        return true;
      }
    }
    return false;
  }

  @Override
  public boolean canRepresent(Object element) {
    return GroupNodes.canRepresent(this, element);
  }

  @Override
  @Nullable
  public Comparable getSortKey() {
    return mySourceType;
  }

  @Override
  @Nullable
  public Comparable getTypeSortKey() {
    return mySourceType;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }
    if (o == null || getClass() != o.getClass()) {
      return false;
    }
    if (!super.equals(o)) {
      return false;
    }

    AndroidSourceTypeNode that = (AndroidSourceTypeNode)o;

    if (mySourceType != that.mySourceType) {
      return false;
    }
    return mySourceRoots.equals(that.mySourceRoots);
  }

  @Override
  public int hashCode() {
    return Objects.hash(super.hashCode(), mySourceType, mySourceRoots);
  }

  @Override
  @NotNull
  public List<PsiDirectory> getFolders() {
    return getSourceFolders();
  }
}
