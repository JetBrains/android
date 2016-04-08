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

import com.android.tools.idea.gradle.AndroidGradleModel;
import com.android.tools.idea.navigator.AndroidProjectViewPane;
import com.google.common.collect.HashMultimap;
import com.google.common.collect.Lists;
import com.google.common.collect.Sets;
import com.intellij.codeInsight.dataflow.SetUtil;
import com.intellij.ide.projectView.ViewSettings;
import com.intellij.ide.projectView.impl.nodes.ProjectViewModuleNode;
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
import java.util.Set;

/**
 * {@link com.intellij.ide.projectView.impl.nodes.PackageViewModuleNode} does not classify source types, and just assumes that all source
 * roots contain Java packages. This class overrides that behavior to provide a per source type node ({@link AndroidSourceTypeNode}) inside
 * a module.
 */
public class AndroidModuleNode extends ProjectViewModuleNode {
  private final AndroidProjectViewPane myProjectViewPane;

  public AndroidModuleNode(@NotNull Project project,
                           @NotNull Module module,
                           ViewSettings settings,
                           @NotNull AndroidProjectViewPane projectViewPane) {
    super(project, module, settings);
    myProjectViewPane = projectViewPane;
  }

  @NotNull
  private Module getModule() {
    Module module = getValue();
    assert module != null;
    return module;
  }

  @NotNull
  @Override
  public Collection<AbstractTreeNode> getChildren() {
    AndroidFacet facet = AndroidFacet.getInstance(getModule());
    if (facet == null || facet.getAndroidModel() == null) {
      return super.getChildren();
    }

    return getChildren(facet, getSettings(), myProjectViewPane, AndroidProjectViewPane.getSourceProviders(facet));
  }

  @NotNull
  public static Collection<AbstractTreeNode> getChildren(@NotNull AndroidFacet facet,
                                                         @NotNull ViewSettings settings,
                                                         @NotNull AndroidProjectViewPane pane,
                                                         @NotNull List<IdeaSourceProvider> providers) {
    Project project = facet.getModule().getProject();
    List<AbstractTreeNode> result = Lists.newArrayList();

    AndroidGradleModel androidGradleModel = AndroidGradleModel.get(facet);
    HashMultimap<AndroidSourceType,VirtualFile> sourcesByType = getSourcesBySourceType(providers, androidGradleModel);

    for (AndroidSourceType sourceType : sourcesByType.keySet()) {
      if (sourceType == AndroidSourceType.MANIFEST) {
        result.add(new AndroidManifestsGroupNode(project, facet, settings, sourcesByType.get(sourceType)));
        continue;
      }
      if (sourceType == AndroidSourceType.RES) {
        result.add(new AndroidResFolderNode(project, facet, settings, sourcesByType.get(sourceType), pane));
        continue;
      }
      if (sourceType == AndroidSourceType.SHADERS) {
        if (androidGradleModel == null || androidGradleModel.supportsShaders()) {
          continue;
        }
      }
      result.add(new AndroidSourceTypeNode(project, facet, settings, sourceType, sourcesByType.get(sourceType), pane));
    }

    return result;
  }

  @NotNull
  private static HashMultimap<AndroidSourceType,VirtualFile> getSourcesBySourceType(@NotNull List<IdeaSourceProvider> providers,
                                                                                    @Nullable AndroidGradleModel androidGradleModel) {
    HashMultimap<AndroidSourceType,VirtualFile> sourcesByType = HashMultimap.create();

    // Multiple source types can sometimes be present in the same source folder, e.g.:
    //    sourcesSets.main.java.srcDirs = sourceSets.main.aidl.srcDirs = ['src']
    // in such a case, we only want to show one of them. Source sets can be either proper or improper subsets. It is not entirely
    // obvious there is a perfect solution here, but since this is not a common occurrence, we resort to the easiest solution here:
    // If a set of sources has partially been included as part of another source type's source set, then we simply don't include it
    // as part of this source type.
    Set<VirtualFile> allSources = Sets.newHashSet();

    for (AndroidSourceType sourceType : AndroidSourceType.values()) {
      if (sourceType == AndroidSourceType.SHADERS && (androidGradleModel == null || !androidGradleModel.supportsShaders())) {
        continue;
      }
      Set<VirtualFile> sources = getSources(sourceType, providers);
      if (sources.isEmpty()) {
        continue;
      }

      if (SetUtil.intersect(allSources, sources).isEmpty()) {
        // if we haven't seen any of these source folders, then create a new source type folder
        sourcesByType.putAll(sourceType, sources);
      } else if (!allSources.containsAll(sources)) {
        // if we have a partial overlap, we put just the non overlapping sources into this source type
        sources.removeAll(allSources);
        sourcesByType.putAll(sourceType, sources);
      }

      allSources.addAll(sources);
    }

    return sourcesByType;
  }

  @NotNull
  private static Set<VirtualFile> getSources(AndroidSourceType sourceType, Iterable<IdeaSourceProvider> providers) {
    Set<VirtualFile> sources = Sets.newHashSet();

    for (IdeaSourceProvider provider : providers) {
      sources.addAll(sourceType.getSources(provider));
    }

    return sources;
  }

  @Nullable
  @Override
  public Comparable getSortKey() {
    return getModule().getName();
  }

  @Nullable
  @Override
  public Comparable getTypeSortKey() {
    return getSortKey();
  }

  @Nullable
  @Override
  public String toTestString(@Nullable Queryable.PrintInfo printInfo) {
    return String.format("%1$s (Android)", getModule().getName());
  }

  @Override
  public boolean equals(Object o) {
    if (o == null || getClass() != o.getClass()) return false;
    return super.equals(o);
  }
}
