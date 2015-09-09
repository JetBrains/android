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

import com.android.SdkConstants;
import com.google.common.collect.Iterables;
import com.google.common.collect.Lists;
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
import com.intellij.ui.SimpleTextAttributes;
import org.jetbrains.android.facet.AndroidFacet;
import org.jetbrains.android.facet.AndroidSourceType;
import org.jetbrains.android.facet.IdeaSourceProvider;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.util.Collection;
import java.util.List;
import java.util.Set;

public class AndroidManifestsGroupNode extends ProjectViewNode<AndroidFacet> implements DirectoryGroupNode {
  private static final String MANIFESTS_NODE = "manifests";
  @NotNull private final Set<VirtualFile> mySources;

  protected AndroidManifestsGroupNode(@NotNull Project project,
                                      @NotNull AndroidFacet facet,
                                      @NotNull ViewSettings viewSettings,
                                      @NotNull Set<VirtualFile> sources) {
    super(project, facet, viewSettings);
    mySources = sources;
  }

  @Override
  public boolean contains(@NotNull VirtualFile file) {
    return mySources.contains(file);
  }

  @NotNull
  @Override
  public Collection<? extends AbstractTreeNode> getChildren() {
    PsiManager psiManager = PsiManager.getInstance(myProject);

    List<AbstractTreeNode> children = Lists.newArrayList();
    for (VirtualFile manifest : mySources) {
      if (!manifest.isValid()) {
        continue;
      }

      PsiFile psiFile = psiManager.findFile(manifest);
      if (psiFile != null) {
        AndroidFacet facet = getValue();
        assert facet != null : "Android Facet for module cannot be null";
        children.add(new AndroidManifestFileNode(myProject, psiFile, getSettings(), facet));
      }
    }
    return children;
  }

  @Override
  protected void update(PresentationData presentation) {
    presentation.addText(MANIFESTS_NODE, SimpleTextAttributes.REGULAR_ATTRIBUTES);
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

    new OpenFileDescriptor(myProject, fileToOpen).navigate(requestFocus);
  }

  @Nullable
  private VirtualFile findFileToOpen(@NotNull Set<VirtualFile> files) {
    VirtualFile bestFile = Iterables.getFirst(files, null);

    PsiManager psiManager = PsiManager.getInstance(myProject);
    for (VirtualFile f : files) {
      PsiFile psiFile = psiManager.findFile(f);
      if (psiFile == null) {
        continue;
      }

      IdeaSourceProvider sourceProvider = AndroidManifestFileNode.getSourceProvider(getValue(), psiFile);
      if (sourceProvider != null && SdkConstants.FD_MAIN.equals(sourceProvider.getName())) {
        bestFile = f;
      }
    }

    return bestFile;
  }

  @NotNull
  @Override
  public PsiDirectory[] getDirectories() {
    return PsiDirectory.EMPTY_ARRAY;
  }

  @Nullable
  @Override
  public Comparable getSortKey() {
    return AndroidSourceType.MANIFEST;
  }

  @Nullable
  @Override
  public Comparable getTypeSortKey() {
    return AndroidSourceType.MANIFEST;
  }

  @Nullable
  @Override
  public String toTestString(@Nullable Queryable.PrintInfo printInfo) {
    return MANIFESTS_NODE;
  }
}
