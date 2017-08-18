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

import com.android.tools.idea.flags.StudioFlags;
import com.android.tools.idea.gradle.project.model.AndroidModuleModel;
import com.android.tools.idea.gradle.project.model.NdkModuleModel;
import com.android.tools.idea.navigator.AndroidProjectViewPane;
import com.android.tools.idea.res.SampleDataResourceRepository;
import com.google.common.collect.HashMultimap;
import com.intellij.codeInsight.dataflow.SetUtil;
import com.intellij.ide.projectView.PresentationData;
import com.intellij.ide.projectView.ViewSettings;
import com.intellij.ide.projectView.impl.nodes.ProjectViewModuleNode;
import com.intellij.ide.projectView.impl.nodes.PsiDirectoryNode;
import com.intellij.ide.util.treeView.AbstractTreeNode;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.Queryable;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiDirectory;
import com.intellij.psi.PsiManager;
import org.jetbrains.android.facet.AndroidFacet;
import org.jetbrains.android.facet.AndroidSourceType;
import org.jetbrains.android.facet.IdeaSourceProvider;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.util.*;

import static com.android.tools.idea.gradle.util.GradleUtil.getModuleIcon;

/**
 * {@link com.intellij.ide.projectView.impl.nodes.PackageViewModuleNode} does not classify source types, and just assumes that all source
 * roots contain Java packages. This class overrides that behavior to provide a per source type node ({@link AndroidSourceTypeNode}) inside
 * a module.
 */
public class AndroidModuleNode extends ProjectViewModuleNode {
  private final AndroidProjectViewPane myProjectViewPane;

  public AndroidModuleNode(@NotNull Project project,
                           @NotNull Module module,
                           @NotNull ViewSettings settings,
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

  @Override
  @NotNull
  public Collection<AbstractTreeNode> getChildren() {
    AndroidFacet facet = AndroidFacet.getInstance(getModule());
    if (facet == null || facet.getAndroidModel() == null) {
      return super.getChildren();
    }
    return getChildren(facet, getSettings(), myProjectViewPane, AndroidProjectViewPane.getSourceProviders(facet));
  }

  @NotNull
  static Collection<AbstractTreeNode> getChildren(@NotNull AndroidFacet facet,
                                                  @NotNull ViewSettings settings,
                                                  @NotNull AndroidProjectViewPane projectViewPane,
                                                  @NotNull List<IdeaSourceProvider> providers) {
    Project project = facet.getModule().getProject();
    List<AbstractTreeNode> result = new ArrayList<>();

    AndroidModuleModel androidModuleModel = AndroidModuleModel.get(facet);
    HashMultimap<AndroidSourceType, VirtualFile> sourcesByType = getSourcesBySourceType(providers, androidModuleModel);

    NdkModuleModel ndkModuleModel = NdkModuleModel.get(facet.getModule());

    for (AndroidSourceType sourceType : sourcesByType.keySet()) {
      if (sourceType == AndroidSourceType.CPP && ndkModuleModel != null) {
        // Native sources will be added separately from NativeAndroidGradleModel.
        continue;
      }
      if (sourceType == AndroidSourceType.MANIFEST) {
        result.add(new AndroidManifestsGroupNode(project, facet, settings, sourcesByType.get(sourceType)));
        continue;
      }
      if (sourceType == AndroidSourceType.RES) {
        result.add(new AndroidResFolderNode(project, facet, settings, sourcesByType.get(sourceType), projectViewPane));
        continue;
      }
      if (sourceType == AndroidSourceType.SHADERS) {
        if (androidModuleModel == null || !androidModuleModel.getFeatures().isShadersSupported()) {
          continue;
        }
      }
      result.add(new AndroidSourceTypeNode(project, facet, settings, sourceType, sourcesByType.get(sourceType), projectViewPane));
    }

    if (ndkModuleModel != null) {
      result.add(new AndroidJniFolderNode(project, ndkModuleModel, settings));
    }

    if (StudioFlags.NELE_SAMPLE_DATA.get()) {
      try {
        VirtualFile sampleDataDirectory = SampleDataResourceRepository.getSampleDataDir(facet, false);
        PsiDirectory sampleDataPsi = sampleDataDirectory != null ? PsiManager.getInstance(project).findDirectory(sampleDataDirectory) : null;
        if (sampleDataPsi != null) {
          result.add(new PsiDirectoryNode(project, sampleDataPsi, settings));
        }
      }
      catch (IOException ignore) {
        // The folder doesn't exist so we do not add it
      }
    }

    return result;
  }

  @NotNull
  private static HashMultimap<AndroidSourceType, VirtualFile> getSourcesBySourceType(@NotNull List<IdeaSourceProvider> providers,
                                                                                     @Nullable AndroidModuleModel androidModel) {
    HashMultimap<AndroidSourceType, VirtualFile> sourcesByType = HashMultimap.create();

    // Multiple source types can sometimes be present in the same source folder, e.g.:
    //    sourcesSets.main.java.srcDirs = sourceSets.main.aidl.srcDirs = ['src']
    // in such a case, we only want to show one of them. Source sets can be either proper or improper subsets. It is not entirely
    // obvious there is a perfect solution here, but since this is not a common occurrence, we resort to the easiest solution here:
    // If a set of sources has partially been included as part of another source type's source set, then we simply don't include it
    // as part of this source type.
    Set<VirtualFile> allSources = new HashSet<>();

    for (AndroidSourceType sourceType : AndroidSourceType.values()) {
      if (sourceType == AndroidSourceType.SHADERS && (androidModel == null || !androidModel.getFeatures().isShadersSupported())) {
        continue;
      }
      Set<VirtualFile> sources = getSources(sourceType, providers);
      if (sources.isEmpty()) {
        continue;
      }

      if (SetUtil.intersect(allSources, sources).isEmpty()) {
        // if we haven't seen any of these source folders, then create a new source type folder
        sourcesByType.putAll(sourceType, sources);
      }
      else if (!allSources.containsAll(sources)) {
        // if we have a partial overlap, we put just the non overlapping sources into this source type
        sources.removeAll(allSources);
        sourcesByType.putAll(sourceType, sources);
      }

      allSources.addAll(sources);
    }

    return sourcesByType;
  }

  @NotNull
  private static Set<VirtualFile> getSources(@NotNull AndroidSourceType sourceType, @NotNull Iterable<IdeaSourceProvider> providers) {
    Set<VirtualFile> sources = new HashSet<>();

    for (IdeaSourceProvider provider : providers) {
      sources.addAll(sourceType.getSources(provider));
    }

    return sources;
  }

  @Override
  @Nullable
  public Comparable getSortKey() {
    return getModule().getName();
  }

  @Override
  @Nullable
  public Comparable getTypeSortKey() {
    return getSortKey();
  }

  @Override
  @Nullable
  public String toTestString(@Nullable Queryable.PrintInfo printInfo) {
    return String.format("%1$s (Android)", getModule().getName());
  }

  @Override
  public void update(@NotNull PresentationData presentation) {
    super.update(presentation);
    // Use Android Studio Icons
    presentation.setIcon(getModuleIcon(getModule()));
  }

  @Override
  public boolean equals(Object o) {
    if (o == null || getClass() != o.getClass()) {
      return false;
    }
    return super.equals(o);
  }
}
