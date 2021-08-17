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

import static com.android.tools.idea.gradle.util.GradleUtil.getModuleIcon;
import static com.android.tools.idea.navigator.nodes.ndk.NdkModuleNodeKt.containedByNativeNodes;
import static com.android.tools.idea.util.FileExtensions.toVirtualFile;
import static com.intellij.openapi.vfs.VfsUtil.findFileByIoFile;
import static org.jetbrains.android.facet.AndroidSourceType.GENERATED_JAVA;
import static org.jetbrains.android.facet.AndroidSourceType.GENERATED_RES;

import com.android.tools.idea.gradle.model.IdeAndroidArtifact;
import com.android.tools.idea.gradle.model.IdeJavaArtifact;
import com.android.ide.common.util.PathString;
import com.android.tools.idea.gradle.project.model.AndroidModuleModel;
import com.android.tools.idea.gradle.project.model.NdkModuleModel;
import com.android.tools.idea.gradle.util.GradleUtil;
import com.android.tools.idea.model.AndroidModel;
import com.android.tools.idea.navigator.AndroidProjectViewPane;
import com.android.tools.idea.navigator.nodes.AndroidViewModuleNode;
import com.android.tools.idea.projectsystem.AndroidModuleSystem;
import com.android.tools.idea.projectsystem.NamedIdeaSourceProvider;
import com.android.tools.idea.projectsystem.ProjectSystemUtil;
import com.google.common.collect.HashMultimap;
import com.intellij.codeInsight.dataflow.SetUtil;
import com.intellij.ide.projectView.PresentationData;
import com.intellij.ide.projectView.ViewSettings;
import com.intellij.ide.projectView.impl.nodes.PsiDirectoryNode;
import com.intellij.ide.util.treeView.AbstractTreeNode;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.Queryable;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiDirectory;
import com.intellij.psi.PsiManager;
import java.io.File;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.jetbrains.android.facet.AndroidFacet;
import org.jetbrains.android.facet.AndroidSourceType;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * {@link com.intellij.ide.projectView.impl.nodes.PackageViewModuleNode} does not classify source types, and just assumes that all source
 * roots contain Java packages. This class overrides that behavior to provide a per source type node ({@link AndroidSourceTypeNode}) inside
 * a module.
 */
public class AndroidModuleNode extends AndroidViewModuleNode {

  public AndroidModuleNode(@NotNull Project project,
                           @NotNull Module module,
                           @NotNull AndroidProjectViewPane projectViewPane,
                           @NotNull ViewSettings settings) {
    super(project, module, projectViewPane, settings);
  }

  @Override
  @NotNull
  protected Collection<AbstractTreeNode<?>> getModuleChildren() {
    Module module = getValue();
    if (module == null || module.isDisposed()) {
      return Collections.emptyList();
    }
    AndroidFacet facet = AndroidFacet.getInstance(module);
    if (facet == null || AndroidModel.get(facet) == null) {
      return platformGetChildren();
    }
    return getChildren(facet, getSettings(), myProjectViewPane, AndroidProjectViewPane.getSourceProviders(facet));
  }

  @NotNull
  static Collection<AbstractTreeNode<?>> getChildren(@NotNull AndroidFacet facet,
                                                     @NotNull ViewSettings settings,
                                                     @NotNull AndroidProjectViewPane projectViewPane,
                                                     @NotNull Iterable<NamedIdeaSourceProvider> providers) {
    List<AbstractTreeNode<?>> result = new ArrayList<>();
    Project project = facet.getModule().getProject();
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
      if (sourceType == AndroidSourceType.RES || sourceType == GENERATED_RES) {
        result.add(new AndroidResFolderNode(project, facet, sourceType, settings, sourcesByType.get(sourceType), projectViewPane));
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

    AndroidModuleSystem moduleSystem = ProjectSystemUtil.getModuleSystem(facet.getModule());
    PsiDirectory sampleDataPsi = getPsiDirectory(project, moduleSystem.getSampleDataDirectory());
    if (sampleDataPsi != null) {
      result.add(new PsiDirectoryNode(project, sampleDataPsi, settings));
    }

    return result;
  }

  @Nullable
  private static PsiDirectory getPsiDirectory(@NotNull Project project, @Nullable PathString path) {
    VirtualFile virtualFile = toVirtualFile(path);
    return virtualFile != null ? PsiManager.getInstance(project).findDirectory(virtualFile) : null;
  }

  @NotNull
  private static HashMultimap<AndroidSourceType, VirtualFile> getSourcesBySourceType(@NotNull Iterable<NamedIdeaSourceProvider> providers,
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
      Set<VirtualFile> sources;
      if (sourceType == GENERATED_JAVA) {
        sources = getGeneratedSources(androidModel);
      }
      else if (sourceType == GENERATED_RES) {
        sources = getGeneratedResFolders(androidModel);
      }
      else {
        sources = getSources(sourceType, providers);
      }
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
  private static Set<VirtualFile> getSources(@NotNull AndroidSourceType sourceType, @NotNull Iterable<NamedIdeaSourceProvider> providers) {
    Set<VirtualFile> sources = new HashSet<>();

    for (NamedIdeaSourceProvider provider : providers) {
      sources.addAll(sourceType.getSources(provider));
    }

    return sources;
  }

  /**
   * Collect generated java source folders from main artifact and test artifacts.
   */
  @NotNull
  private static Set<VirtualFile> getGeneratedSources(@Nullable AndroidModuleModel androidModuleModel) {
    Set<VirtualFile> sources = new HashSet<>();
    if (androidModuleModel != null) {
      List<File> files = new ArrayList<>(GradleUtil.getGeneratedSourceFoldersToUse(androidModuleModel.getMainArtifact(),
                                                                                   androidModuleModel));
      IdeAndroidArtifact androidTestArtifact = androidModuleModel.getArtifactForAndroidTest();
      if (androidTestArtifact != null) {
        files.addAll(GradleUtil.getGeneratedSourceFoldersToUse(androidTestArtifact, androidModuleModel));
      }
      IdeJavaArtifact unitTestArtifact = androidModuleModel.getSelectedVariant().getUnitTestArtifact();
      if (unitTestArtifact != null) {
        files.addAll(unitTestArtifact.getGeneratedSourceFolders());
      }
      for (File file : files) {
        VirtualFile vFile = findFileByIoFile(file, false /* Don't refresh. */);
        if (vFile != null) {
          sources.add(vFile);
        }
      }
    }
    return sources;
  }

  /**
   * Collect generated res folders from main artifact and test artifacts.
   */
  @NotNull
  private static Set<VirtualFile> getGeneratedResFolders(@Nullable AndroidModuleModel androidModuleModel) {
    Set<VirtualFile> sources = new HashSet<>();
    if (androidModuleModel != null) {
      List<File> files = new ArrayList<>(androidModuleModel.getMainArtifact().getGeneratedResourceFolders());
      IdeAndroidArtifact androidTest = androidModuleModel.getArtifactForAndroidTest();
      if (androidTest != null) {
        files.addAll(androidTest.getGeneratedResourceFolders());
      }

      for (File file : files) {
        VirtualFile vFile = findFileByIoFile(file, false /* Don't refresh. */);
        if (vFile != null) {
          sources.add(vFile);
        }
      }
    }
    return sources;
  }

  @Override
  public boolean contains(@NotNull VirtualFile file) {
    if (super.contains(file)) {
      return true;
    }

    // If there is a native-containing module then check it for externally referenced header files
    Module module = getValue();
    if (module == null || module.isDisposed()) {
      return false;
    }
    AndroidFacet facet = AndroidFacet.getInstance(module);
    if (facet == null || AndroidModel.get(facet) == null) {
      return false;
    }
    NdkModuleModel ndkModuleModel = NdkModuleModel.get(facet.getModule());
    if (ndkModuleModel != null) {
      return containedByNativeNodes(myProject, ndkModuleModel, file);
    }
    return false;
  }

  @Override
  @Nullable
  public Comparable getSortKey() {
    Module module = getValue();
    if (module == null || module.isDisposed()) {
      return null;
    }
    return module.getName();
  }

  @Override
  @Nullable
  public Comparable getTypeSortKey() {
    return getSortKey();
  }

  @Override
  @Nullable
  public String toTestString(@Nullable Queryable.PrintInfo printInfo) {
    Module module = getValue();
    if (module == null || module.isDisposed()) {
      return module == null ? "(null)" : "(Disposed)";
    }
    return String.format("%1$s (Android)", super.toTestString(printInfo));
  }

  @Override
  public void update(@NotNull PresentationData presentation) {
    super.update(presentation);
    Module module = getValue();
    if (module == null || module.isDisposed()) {
      return;
    }
    // Use Android Studio Icons if module is available. If module was disposed, super.update will set the value of this node to null.
    // This can happen when a module was just deleted, see b/67838273.
    presentation.setIcon(getModuleIcon(module));
  }
}
