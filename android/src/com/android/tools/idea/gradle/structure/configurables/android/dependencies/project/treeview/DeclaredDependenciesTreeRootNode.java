/*
 * Copyright (C) 2016 The Android Open Source Project
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
package com.android.tools.idea.gradle.structure.configurables.android.dependencies.project.treeview;

import com.android.tools.idea.gradle.structure.configurables.android.dependencies.treeview.AbstractDependencyNode;
import com.android.tools.idea.gradle.structure.configurables.android.dependencies.treeview.DependencyNodeComparator;
import com.android.tools.idea.gradle.structure.configurables.android.dependencies.treeview.LibraryDependencyNode;
import com.android.tools.idea.gradle.structure.configurables.android.dependencies.treeview.ModuleDependencyNode;
import com.android.tools.idea.gradle.structure.configurables.ui.treeview.AbstractPsResettableNode;
import com.android.tools.idea.gradle.structure.configurables.ui.treeview.AbstractPsModelNode;
import com.android.tools.idea.gradle.structure.model.PsArtifactDependencySpec;
import com.android.tools.idea.gradle.structure.model.PsModule;
import com.android.tools.idea.gradle.structure.model.PsProject;
import com.android.tools.idea.gradle.structure.model.android.PsAndroidDependency;
import com.android.tools.idea.gradle.structure.model.android.PsAndroidModule;
import com.android.tools.idea.gradle.structure.model.android.PsAndroidLibraryDependency;
import com.android.tools.idea.gradle.structure.model.android.PsModuleDependency;
import com.google.common.base.Objects;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import org.jetbrains.annotations.NotNull;

import java.util.Collections;
import java.util.List;
import java.util.Map;

class DeclaredDependenciesTreeRootNode extends AbstractPsResettableNode<PsProject> {
  DeclaredDependenciesTreeRootNode(@NotNull PsProject project) {
    super(project);
  }

  @Override
  @NotNull
  protected List<? extends AbstractPsModelNode> createChildren() {
    DeclaredDependencyCollector collector = new DeclaredDependencyCollector();

    PsProject project = getModels().get(0);
    project.forEachModule(module -> collectDeclaredDependencies(module, collector));

    List<AbstractDependencyNode> children = Lists.newArrayList();
    for (Map.Entry<LibraryDependencySpecs, List<PsAndroidLibraryDependency>> entry : collector.libraryDependenciesBySpec.entrySet()) {
      LibraryDependencyNode child = new LibraryDependencyNode(this, entry.getValue());
      children.add(child);
    }

    for (Map.Entry<String, List<PsModuleDependency>> entry : collector.moduleDependenciesByGradlePath.entrySet()) {
      ModuleDependencyNode child = new ModuleDependencyNode(this, entry.getValue());
      children.add(child);
    }

    Collections.sort(children, DependencyNodeComparator.INSTANCE);
    return children;
  }

  private static void collectDeclaredDependencies(@NotNull PsModule module, @NotNull DeclaredDependencyCollector collector) {
    if (module instanceof PsAndroidModule) {
      PsAndroidModule androidModule = (PsAndroidModule)module;
      androidModule.forEachDeclaredDependency(collector::add);
    }
  }

  private static class DeclaredDependencyCollector {
    @NotNull final Map<LibraryDependencySpecs, List<PsAndroidLibraryDependency>> libraryDependenciesBySpec = Maps.newHashMap();
    @NotNull final Map<String, List<PsModuleDependency>> moduleDependenciesByGradlePath = Maps.newHashMap();

    void add(@NotNull PsAndroidDependency dependency) {
      if (dependency instanceof PsAndroidLibraryDependency) {
        add((PsAndroidLibraryDependency)dependency);
      }
      else if (dependency instanceof PsModuleDependency) {
        add((PsModuleDependency)dependency);
      }
    }

    private void add(@NotNull PsAndroidLibraryDependency dependency) {
      LibraryDependencySpecs specs = new LibraryDependencySpecs(dependency);
      List<PsAndroidLibraryDependency> dependencies = libraryDependenciesBySpec.get(specs);
      if (dependencies == null) {
        dependencies = Lists.newArrayList();
        libraryDependenciesBySpec.put(specs, dependencies);
      }
      dependencies.add(dependency);
    }

    private void add(@NotNull PsModuleDependency dependency) {
      String key = dependency.getGradlePath();
      List<PsModuleDependency> dependencies = moduleDependenciesByGradlePath.get(key);
      if (dependencies == null) {
        dependencies = Lists.newArrayList();
        moduleDependenciesByGradlePath.put(key, dependencies);
      }
      dependencies.add(dependency);
    }
  }

  private static class LibraryDependencySpecs {
    @NotNull final PsArtifactDependencySpec declaredSpec;
    @NotNull final PsArtifactDependencySpec resolvedSpec;

    LibraryDependencySpecs(@NotNull PsAndroidLibraryDependency dependency) {
      PsArtifactDependencySpec declaredSpec = dependency.getDeclaredSpec();
      assert declaredSpec != null;
      this.declaredSpec = declaredSpec;
      resolvedSpec = dependency.getResolvedSpec();
    }

    @Override
    public boolean equals(Object o) {
      if (this == o) {
        return true;
      }
      if (o == null || getClass() != o.getClass()) {
        return false;
      }
      LibraryDependencySpecs that = (LibraryDependencySpecs)o;
      return Objects.equal(declaredSpec, that.declaredSpec) &&
             Objects.equal(resolvedSpec, that.resolvedSpec);
    }

    @Override
    public int hashCode() {
      return Objects.hashCode(declaredSpec, resolvedSpec);
    }
  }
}
