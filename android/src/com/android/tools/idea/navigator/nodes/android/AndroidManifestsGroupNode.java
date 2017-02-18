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

import com.android.tools.idea.navigator.nodes.FolderGroupNode;
import com.google.common.collect.Iterables;
import com.intellij.ide.projectView.PresentationData;
import com.intellij.ide.projectView.ProjectViewNode;
import com.intellij.ide.projectView.ViewSettings;
import com.intellij.ide.util.treeView.AbstractTreeNode;
import com.intellij.openapi.fileEditor.OpenFileDescriptor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.Queryable;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiDirectory;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiManager;
import org.jetbrains.android.facet.AndroidFacet;
import org.jetbrains.android.facet.AndroidSourceType;
import org.jetbrains.android.facet.IdeaSourceProvider;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Set;

import static com.android.SdkConstants.FD_MAIN;
import static com.intellij.psi.PsiDirectory.EMPTY_ARRAY;
import static com.intellij.ui.SimpleTextAttributes.REGULAR_ATTRIBUTES;

public class AndroidManifestsGroupNode extends ProjectViewNode<AndroidFacet> implements FolderGroupNode {
  private static final String MANIFESTS_NODE = "manifests";

  @NotNull private final Set<VirtualFile> mySources;

  public AndroidManifestsGroupNode(@NotNull Project project,
                                   @NotNull AndroidFacet androidFacet,
                                   @NotNull ViewSettings settings,
                                   @NotNull Set<VirtualFile> sources) {
    super(project, androidFacet, settings);
    mySources = sources;
  }

  @Override
  public boolean contains(@NotNull VirtualFile file) {
    return mySources.contains(file);
  }

  @Override
  @NotNull
  public Collection<? extends AbstractTreeNode> getChildren() {
    Project project = getNotNullProject();
    PsiManager psiManager = PsiManager.getInstance(project);

    List<AbstractTreeNode> children = new ArrayList<>();
    for (VirtualFile manifest : mySources) {
      if (!manifest.isValid()) {
        continue;
      }

      PsiFile psiFile = psiManager.findFile(manifest);
      if (psiFile != null) {
        AndroidFacet facet = getAndroidFacet();
        children.add(new AndroidManifestFileNode(project, psiFile, getSettings(), facet));
      }
    }
    return children;
  }

  @Override
  protected void update(@NotNull PresentationData presentation) {
    presentation.addText(MANIFESTS_NODE, REGULAR_ATTRIBUTES);
    Icon icon = AndroidSourceType.MANIFEST.getIcon();
    if (icon != null) {
      presentation.setIcon(icon);
    }
    presentation.setPresentableText(MANIFESTS_NODE);
  }

  @Override
  public boolean expandOnDoubleClick() {
    return false;
  }

  @Override
  public boolean canNavigate() {
    return !mySources.isEmpty();
  }

  @Override
  public void navigate(boolean requestFocus) {
    VirtualFile fileToOpen = findFileToOpen(mySources);
    if (fileToOpen == null) {
      return;
    }
    new OpenFileDescriptor(getNotNullProject(), fileToOpen).navigate(requestFocus);
  }

  @Nullable
  private VirtualFile findFileToOpen(@NotNull Set<VirtualFile> files) {
    VirtualFile bestFile = Iterables.getFirst(files, null);

    PsiManager psiManager = PsiManager.getInstance(getNotNullProject());
    for (VirtualFile file : files) {
      PsiFile psiFile = psiManager.findFile(file);
      if (psiFile == null) {
        continue;
      }

      AndroidFacet facet = getAndroidFacet();
      IdeaSourceProvider sourceProvider = AndroidManifestFileNode.getSourceProvider(facet, psiFile);
      if (sourceProvider != null && FD_MAIN.equals(sourceProvider.getName())) {
        bestFile = file;
      }
    }

    return bestFile;
  }

  @NotNull
  private AndroidFacet getAndroidFacet() {
    AndroidFacet facet = getValue();
    assert facet != null : "Android Facet for module cannot be null";
    return facet;
  }

  @NotNull
  private Project getNotNullProject() {
    assert myProject != null;
    return myProject;
  }

  @Override
  @NotNull
  public PsiDirectory[] getFolders() {
    return EMPTY_ARRAY;
  }

  @Override
  @Nullable
  public Comparable getSortKey() {
    return AndroidSourceType.MANIFEST;
  }

  @Override
  @Nullable
  public Comparable getTypeSortKey() {
    return AndroidSourceType.MANIFEST;
  }

  @Override
  @Nullable
  public String toTestString(@Nullable Queryable.PrintInfo printInfo) {
    return MANIFESTS_NODE;
  }

  @Override
  public boolean isAlwaysShowPlus() {
    return true;
  }
}
