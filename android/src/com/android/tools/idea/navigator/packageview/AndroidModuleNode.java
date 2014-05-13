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
import com.intellij.ide.projectView.ViewSettings;
import com.intellij.ide.projectView.impl.nodes.PackageViewLibrariesNode;
import com.intellij.ide.projectView.impl.nodes.PackageViewModuleNode;
import com.intellij.ide.util.treeView.AbstractTreeNode;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.Queryable;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.android.facet.AndroidFacet;
import org.jetbrains.android.facet.AndroidSourceType;
import org.jetbrains.android.facet.IdeaSourceProvider;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;
import java.util.List;

/**
 * {@link com.intellij.ide.projectView.impl.nodes.PackageViewModuleNode} does not classify source types, and just assumes that all source
 * roots contain Java packages. This class overrides that behavior to provide a per source type node ({@link AndroidSourceTypeNode}) inside
 * a module.
 */
public class AndroidModuleNode extends PackageViewModuleNode {
  public AndroidModuleNode(@NotNull Project project, @NotNull Module module, ViewSettings settings) {
    super(project, module, settings);
  }

  @NotNull
  @Override
  public Collection<AbstractTreeNode> getChildren() {
    Module module = getValue();
    AndroidFacet facet = AndroidFacet.getInstance(module);
    if (facet == null || facet.getIdeaAndroidProject() == null || myProject == null) {
      return super.getChildren();
    }

    // When the list of active providers changes (due to a variant change by the user, or after a gradle sync),
    // the action causes a change to the set of source roots, which itself causes the project/package view to refresh,
    // so there is no need to explicitly listen for those events.
    @SuppressWarnings("ConstantConditions") Iterable<IdeaSourceProvider> providers = AndroidPackageViewSettings.SHOW_CURRENT_VARIANT_ONLY
                                                                                     ? IdeaSourceProvider.getCurrentSourceProviders(facet)
                                                                                     : IdeaSourceProvider.getAllIdeaSourceProviders(facet);
    List<AbstractTreeNode> result = Lists.newArrayList();

    // create a separate node for each source type (java, jni, res, resources, ...), excluding manifests
    for (AndroidSourceType sourceType : AndroidSourceType.values()) {
      if (sourceType == AndroidSourceType.MANIFEST) {
        continue;
      }

      List<VirtualFile> sources = getSources(sourceType, providers);
      if (!sources.isEmpty()) {
        result.add(new AndroidSourceTypeNode(myProject, facet, getSettings(), sourceType, providers));
      }
    }

    // collapse all manifests into a single node
    result.add(new AndroidManifestsNode(myProject, facet, getSettings(), providers));

    if (getSettings().isShowLibraryContents()) {
      result.add(new PackageViewLibrariesNode(myProject, getValue(), getSettings()));
    }

    return result;
  }

  @NotNull
  private static List<VirtualFile> getSources(AndroidSourceType sourceType, Iterable<IdeaSourceProvider> providers) {
    List<VirtualFile> sources = Lists.newArrayList();

    for (IdeaSourceProvider provider : providers) {
      sources.addAll(sourceType.getSources(provider));
    }

    return sources;
  }

  @Nullable
  @Override
  public String toTestString(@Nullable Queryable.PrintInfo printInfo) {
    Module module = getValue();
    return String.format("%1$s (Android)", module.getName());
  }
}
