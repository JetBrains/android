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
package com.android.tools.idea.navigator.nodes.ndk;

import com.android.builder.model.NativeAndroidProject;
import com.android.builder.model.NativeArtifact;
import com.android.tools.idea.gradle.project.facet.ndk.NdkFacet;
import com.android.tools.idea.gradle.project.model.NdkModuleModel;
import com.android.tools.idea.navigator.nodes.AndroidViewModuleNode;
import com.google.common.collect.HashMultimap;
import com.google.common.collect.Multimap;
import com.intellij.ide.projectView.PresentationData;
import com.intellij.ide.projectView.ViewSettings;
import com.intellij.ide.util.treeView.AbstractTreeNode;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.Queryable;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

import static com.android.tools.idea.gradle.util.GradleUtil.getModuleIcon;
import static com.intellij.openapi.util.text.StringUtil.trimEnd;
import static com.intellij.openapi.util.text.StringUtil.trimStart;

public class NdkModuleNode extends AndroidViewModuleNode {
  public NdkModuleNode(@NotNull Project project, @NotNull Module value, @NotNull ViewSettings settings) {
    super(project, value, settings);
  }

  @Override
  @NotNull
  public Collection<AbstractTreeNode> getChildren() {
    Module module = getValue();
    if (module == null) {
      return Collections.emptyList();
    }

    NdkFacet facet = NdkFacet.getInstance(module);
    if (facet == null || facet.getNdkModuleModel() == null) {
      return Collections.emptyList();
    }

    assert myProject != null;
    return getNativeSourceNodes(myProject, facet.getNdkModuleModel(), getSettings());
  }

  @NotNull
  public static Collection<AbstractTreeNode> getNativeSourceNodes(@NotNull Project project,
                                                                  @NotNull NdkModuleModel ndkModel,
                                                                  @NotNull ViewSettings settings) {
    NativeAndroidProject nativeAndroidProject = ndkModel.getAndroidProject();
    Collection<String> sourceFileExtensions = nativeAndroidProject.getFileExtensions().keySet();

    NdkModuleModel.NdkVariant variant = ndkModel.getSelectedVariant();
    Multimap<String, NativeArtifact> nativeLibraries = HashMultimap.create();
    for (NativeArtifact artifact : variant.getArtifacts()) {
      String artifactOutputFileName = artifact.getOutputFile().getName();
      nativeLibraries.put(artifactOutputFileName, artifact);
    }

    if (nativeLibraries.keySet().size() == 1) {
      return NdkLibraryNode.getSourceFolderNodes(project, nativeLibraries.values(), settings, sourceFileExtensions);
    }

    List<AbstractTreeNode> children = new ArrayList<>();
    for (String name : nativeLibraries.keySet()) {
      String nativeLibraryType = "";
      String nativeLibraryName = trimEnd(name, ".so");
      if (nativeLibraryName.length() < name.length()) {
        nativeLibraryType = "Shared Library";
      }
      else {
        nativeLibraryName = trimEnd(name, ".a");
        if (nativeLibraryName.length() < name.length()) {
          nativeLibraryType = "Static Library";
        }
      }
      nativeLibraryName = trimStart(nativeLibraryName, "lib");
      NdkLibraryNode node = new NdkLibraryNode(project, nativeLibraryName, nativeLibraryType, nativeLibraries.get(name), settings,
                                               sourceFileExtensions);
      children.add(node);
    }
    return children;
  }

  @Override
  @Nullable
  public Comparable getSortKey() {
    Module module = getValue();
    if (module == null) {
      return null;
    }
    return module.getName();
  }

  @Override
  @Nullable
  public Comparable getTypeSortKey() {
    return getSortKey();
  }

  @Nullable
  @Override
  public String toTestString(@Nullable Queryable.PrintInfo printInfo) {
    Module module = getValue();
    if (module == null) {
      return null;
    }
    return String.format("%1$s (Native-Android-Gradle)", getValue().getName());
  }

  @Override
  public void update(@NotNull PresentationData presentation) {
    super.update(presentation);
    Module module = getValue();
    if (module != null)
      presentation.setIcon(getModuleIcon(module));
  }
}
