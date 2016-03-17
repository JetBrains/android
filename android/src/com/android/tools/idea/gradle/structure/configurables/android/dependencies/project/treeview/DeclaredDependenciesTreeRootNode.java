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
import com.android.tools.idea.gradle.structure.configurables.android.treeview.AbstractRootNode;
import com.android.tools.idea.gradle.structure.configurables.ui.treeview.AbstractPsdNode;
import com.android.tools.idea.gradle.structure.model.PsArtifactDependencySpec;
import com.android.tools.idea.gradle.structure.model.PsModule;
import com.android.tools.idea.gradle.structure.model.PsProject;
import com.android.tools.idea.gradle.structure.model.android.PsAndroidDependency;
import com.android.tools.idea.gradle.structure.model.android.PsAndroidModule;
import com.android.tools.idea.gradle.structure.model.android.PsLibraryDependency;
import com.android.tools.idea.gradle.structure.model.android.PsModuleDependency;
import com.google.common.base.Objects;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.intellij.util.containers.Predicate;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collections;
import java.util.List;
import java.util.Map;

class DeclaredDependenciesTreeRootNode extends AbstractRootNode<PsProject> {
  DeclaredDependenciesTreeRootNode(@NotNull PsProject project) {
    super(project);
  }

  @Override
  @NotNull
  protected List<? extends AbstractPsdNode> createChildren() {
    final DeclaredDependencyCollector collector = new DeclaredDependencyCollector();

    PsProject project = getModels().get(0);
    project.forEachModule(new Predicate<PsModule>() {
      @Override
      public boolean apply(@Nullable PsModule module) {
        if (module == null) {
          return false;
        }
        collectDeclaredDependencies(module, collector);
        return true;
      }
    });

    List<AbstractDependencyNode> children = Lists.newArrayList();
    for (Map.Entry<LibraryDependencySpecs, List<PsLibraryDependency>> entry : collector.libraryDependenciesBySpec.entrySet()) {
      LibraryDependencyNode child = new LibraryDependencyNode(this, entry.getValue());
      children.add(child);
    }

    Collections.sort(children, DependencyNodeComparator.INSTANCE);
    return children;
  }

  private static void collectDeclaredDependencies(@NotNull PsModule module, @NotNull final DeclaredDependencyCollector collector) {
    if (module instanceof PsAndroidModule) {
      PsAndroidModule androidModule = (PsAndroidModule)module;
      androidModule.forEachDeclaredDependency(new Predicate<PsAndroidDependency>() {
        @Override
        public boolean apply(@Nullable PsAndroidDependency dependency) {
          if (dependency == null) {
            return false;
          }
          collector.add(dependency);
          return true;
        }
      });
    }
  }

  private static class DeclaredDependencyCollector {
    @NotNull final Map<LibraryDependencySpecs, List<PsLibraryDependency>> libraryDependenciesBySpec = Maps.newHashMap();
    @NotNull final Map<String, List<PsModuleDependency>> moduleDependenciesByGradlePath = Maps.newHashMap();

    void add(@NotNull PsAndroidDependency dependency) {
      if (dependency instanceof PsLibraryDependency) {
        add((PsLibraryDependency)dependency);
      }
      else if (dependency instanceof PsModuleDependency) {
        add((PsModuleDependency)dependency);
      }
    }

    private void add(@NotNull PsLibraryDependency dependency) {
      LibraryDependencySpecs specs = new LibraryDependencySpecs(dependency);
      List<PsLibraryDependency> dependencies = libraryDependenciesBySpec.get(specs);
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

    LibraryDependencySpecs(@NotNull PsLibraryDependency dependency) {
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
