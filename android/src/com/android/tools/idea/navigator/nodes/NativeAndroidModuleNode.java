/*
 * Copyright (C) 2015 The Android Open Source Project
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

import com.android.builder.model.NativeAndroidProject;
import com.android.builder.model.NativeArtifact;
import com.android.tools.idea.gradle.NativeAndroidGradleModel;
import com.android.tools.idea.gradle.facet.NativeAndroidGradleFacet;
import com.google.common.collect.*;
import com.intellij.ide.projectView.ViewSettings;
import com.intellij.ide.projectView.impl.nodes.ProjectViewModuleNode;
import com.intellij.ide.util.treeView.AbstractTreeNode;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.Queryable;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;
import java.util.List;

import static com.android.tools.idea.navigator.nodes.NativeAndroidLibraryNode.getSourceDirectoryNodes;
import static com.intellij.openapi.util.text.StringUtil.trimEnd;
import static com.intellij.openapi.util.text.StringUtil.trimStart;

public class NativeAndroidModuleNode extends ProjectViewModuleNode {
  public NativeAndroidModuleNode(@NotNull Project project, @NotNull Module value, ViewSettings viewSettings) {
    super(project, value, viewSettings);
  }

  @NotNull
  public static Collection<AbstractTreeNode> getNativeSourceNodes(@NotNull Project project,
                                                                  @NotNull NativeAndroidGradleModel nativeAndroidModel,
                                                                  @NotNull ViewSettings viewSettings) {
    NativeAndroidProject nativeAndroidProject = nativeAndroidModel.getNativeAndroidProject();

    Collection<String> fileExtensions = Sets.newHashSet();
    fileExtensions.add("h"); // add header files extension explicitly as the model only return the extensions of source file
    fileExtensions.addAll(nativeAndroidProject.getFileExtensions().keySet());

    NativeAndroidGradleModel.NativeVariant variant = nativeAndroidModel.getSelectedVariant();
    Multimap<String, NativeArtifact> nativeLibraries = HashMultimap.create();
    for (NativeArtifact artifact : variant.getArtifacts()) {
      String artifactOutputFileName = artifact.getOutputFile().getName();
      nativeLibraries.put(artifactOutputFileName, artifact);
    }

    if(nativeLibraries.keySet().size() == 1) {
      return getSourceDirectoryNodes(project, nativeLibraries.values(), viewSettings, fileExtensions);
    }

    List<AbstractTreeNode> children = Lists.newArrayList();
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
      children.add(new NativeAndroidLibraryNode(project,
                                                nativeLibraryName,
                                                nativeLibraryType,
                                                nativeLibraries.get(name),
                                                viewSettings,
                                                fileExtensions));
    }
    return children;
  }

  @NotNull
  @Override
  public Collection<AbstractTreeNode> getChildren() {
    Module module = getValue();
    if (module == null) {
      return ImmutableList.of();
    }

    NativeAndroidGradleFacet facet = NativeAndroidGradleFacet.getInstance(module);
    if (facet == null || facet.getNativeAndroidGradleModel() == null) {
      return ImmutableList.of();
    }

    return getNativeSourceNodes(myProject, facet.getNativeAndroidGradleModel(), getSettings());
  }

  @Nullable
  @Override
  public Comparable getSortKey() {
    Module module = getValue();
    if (module == null) {
      return null;
    }
    return module.getName();
  }

  @Nullable
  @Override
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
  public boolean equals(Object o) {
    if (o == null || getClass() != o.getClass()) return false;
    return super.equals(o);
  }
}
