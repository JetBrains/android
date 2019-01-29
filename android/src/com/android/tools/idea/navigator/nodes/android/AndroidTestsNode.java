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

import com.android.tools.idea.navigator.AndroidProjectViewPane;
import com.android.tools.idea.navigator.nodes.FolderGroupNode;
import com.intellij.ide.projectView.PresentationData;
import com.intellij.ide.projectView.ProjectViewNode;
import com.intellij.ide.projectView.ViewSettings;
import com.intellij.ide.util.treeView.AbstractTreeNode;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleType;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.Queryable;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiDirectory;
import org.jetbrains.android.facet.AndroidFacet;
import org.jetbrains.android.facet.IdeaSourceProvider;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;
import java.util.Collections;

import static com.intellij.ui.SimpleTextAttributes.REGULAR_ATTRIBUTES;

/**
 * AndroidTestsNode groups all the sources for instrumentation tests.
 * Currently this is not instantiated as separating this out as it stands now seems to lead to some
 * issues in the UI when it comes to identifying the tree node from source. In the future, this will
 * be made an optional setting.
 */
public class AndroidTestsNode extends ProjectViewNode<AndroidFacet> implements FolderGroupNode {
  private static final String ANDROID_TESTS = "androidTests";
  private final AndroidProjectViewPane myProjectViewPane;

  AndroidTestsNode(@NotNull Project project,
                   @NotNull AndroidFacet androidFacet,
                   @NotNull ViewSettings settings,
                   @NotNull AndroidProjectViewPane projectViewPane) {
    super(project, androidFacet, settings);
    myProjectViewPane = projectViewPane;
  }


  @Override
  @NotNull
  public Collection<AbstractTreeNode> getChildren() {
    Module module = getModule();
    AndroidFacet facet = AndroidFacet.getInstance(module);
    if (facet == null || facet.getConfiguration().getModel() == null) {
      return Collections.emptyList();
    }

    return AndroidModuleNode.getChildren(facet, getSettings(), myProjectViewPane, IdeaSourceProvider.getCurrentTestSourceProviders(facet));
  }

  @Override
  public boolean contains(@NotNull VirtualFile file) {
    Module module = getModule();
    AndroidFacet facet = AndroidFacet.getInstance(module);
    if (facet == null || facet.getConfiguration().getModel() == null) {
      return false;
    }

    for (IdeaSourceProvider provider : IdeaSourceProvider.getCurrentTestSourceProviders(facet)) {
      if (provider.containsFile(file)) {
        return true;
      }
    }

    return false;
  }

  @Override
  public void update(@NotNull PresentationData presentation) {
    presentation.setPresentableText(ANDROID_TESTS);
    presentation.addText(ANDROID_TESTS, REGULAR_ATTRIBUTES);
    presentation.setIcon(ModuleType.get(getModule()).getIcon());
  }

  @NotNull
  private Module getModule() {
    return getAndroidFacet().getModule();
  }

  @NotNull
  private AndroidFacet getAndroidFacet() {
    AndroidFacet facet = getValue();
    assert facet != null;
    return facet;
  }

  @Override
  @Nullable
  public String toTestString(@Nullable Queryable.PrintInfo printInfo) {
    return ANDROID_TESTS;
  }

  @Override
  public boolean equals(Object object) {
    return object instanceof AndroidTestsNode && super.equals(object);
  }

  @Override
  @NotNull
  public PsiDirectory[] getFolders() {
    return PsiDirectory.EMPTY_ARRAY;
  }
}
