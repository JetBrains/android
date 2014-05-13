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
package com.android.tools.idea.navigator.packageview;

import com.google.common.collect.Lists;
import com.intellij.ide.projectView.PresentationData;
import com.intellij.ide.projectView.ProjectViewNode;
import com.intellij.ide.projectView.ViewSettings;
import com.intellij.ide.util.treeView.AbstractTreeNode;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.Queryable;
import com.intellij.openapi.vfs.VirtualFile;
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

/** A virtual node in the package view that groups all the manifests together. */
public class AndroidManifestsNode extends ProjectViewNode<AndroidFacet> {
  private static final String MANIFESTS_NODE = "manifests";

  @NotNull private final Iterable<IdeaSourceProvider> mySourceProviders;

  public AndroidManifestsNode(@NotNull Project project,
                              @NotNull AndroidFacet facet,
                              @NotNull ViewSettings viewSettings,
                              @NotNull Iterable<IdeaSourceProvider> ideaSourceProviders) {
    super(project, facet, viewSettings);
    mySourceProviders = ideaSourceProviders;
  }

  @NotNull
  @Override
  public Collection<? extends AbstractTreeNode> getChildren() {
    List<AbstractTreeNode> children = Lists.newArrayList();

    assert myProject != null;
    PsiManager psiManager = PsiManager.getInstance(myProject);

    for (IdeaSourceProvider provider : mySourceProviders) {
      VirtualFile manifest = provider.getManifestFile();
      if (manifest == null) {
        continue;
      }

      PsiFile psiFile = psiManager.findFile(manifest);
      if (psiFile != null) {
        children.add(new AndroidManifestFileNode(myProject, psiFile, getSettings(), provider));
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
  }

  @Nullable
  @Override
  public String toTestString(@Nullable Queryable.PrintInfo printInfo) {
    return MANIFESTS_NODE;
  }

  @Override
  public boolean contains(@NotNull VirtualFile file) {
    for (IdeaSourceProvider provider : mySourceProviders) {
      if (file.equals(provider.getManifestFile())) {
        return true;
      }
    }

    return false;
  }
}
