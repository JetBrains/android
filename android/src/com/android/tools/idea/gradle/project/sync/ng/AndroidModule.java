/*
 * Copyright (C) 2018 The Android Open Source Project
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
package com.android.tools.idea.gradle.project.sync.ng;

import com.android.builder.model.*;
import com.android.builder.model.level2.DependencyGraphs;
import com.android.builder.model.level2.GraphItem;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static com.android.tools.idea.gradle.project.sync.Modules.createUniqueModuleId;
import static com.google.common.base.Strings.nullToEmpty;

public class AndroidModule {
  @NotNull private final AndroidProject myAndroidProject;
  @NotNull private final SyncModuleModels myModuleModels;
  @NotNull private final List<ModuleDependency> myModuleDependencies = new ArrayList<>();
  @NotNull private final Map<String, Variant> myVariantsByName = new HashMap<>();
  // Format of ArtifactAddress for module library, BuildId@@GradlePath::Variant, the "::Variant" part is optional if variant is null.
  @NotNull public static final Pattern MODULE_ARTIFACT_ADDRESS_PATTERN = Pattern.compile("([^@]*)@@(.[^:]*)(::(.*))?");

  AndroidModule(@NotNull AndroidProject androidProject, @NotNull SyncModuleModels moduleModels) {
    myAndroidProject = androidProject;
    myModuleModels = moduleModels;
  }

  void addSelectedVariant(@NotNull Variant selectedVariant) {
    myVariantsByName.put(selectedVariant.getName(), selectedVariant);
    AndroidArtifact artifact = selectedVariant.getMainArtifact();
    Dependencies dependencies = artifact.getDependencies();

    if (!dependencies.getLibraries().isEmpty()) {
      // Level1 Dependencies model.
      populateDependencies(dependencies);
    }
    else {
      // Level4 DependencyGraphs model.
      // DependencyGraph was added in AGP 3.0. If the code gets here, means current AGP is 3.2+, no try/catch needed.
      populateDependencies(artifact.getDependencyGraphs());
    }
  }

  private void populateDependencies(@NotNull Dependencies dependencies) {
    for (AndroidLibrary library : dependencies.getLibraries()) {
      String project = library.getProject();
      if (project != null) {
        String id = createUniqueModuleId(nullToEmpty(library.getBuildId()), project);
        String variant = library.getProjectVariant();
        addModuleDependency(id, variant);
      }
    }
  }

  private void populateDependencies(@NotNull DependencyGraphs dependencyGraphs) {
    for (GraphItem item : dependencyGraphs.getCompileDependencies()) {
      String address = item.getArtifactAddress();
      Matcher matcher = MODULE_ARTIFACT_ADDRESS_PATTERN.matcher(address);
      if (matcher.matches()) {
        String buildId = matcher.group(1);
        String project = matcher.group(2);
        if (buildId != null && project != null) {
          String id = createUniqueModuleId(nullToEmpty(buildId), project);
          String variant = matcher.group(4);
          addModuleDependency(id, variant);
        }
      }
    }
  }

  private void addModuleDependency(@NotNull String id, @Nullable String variant) {
    ModuleDependency dependency = new ModuleDependency(id, variant);
    myModuleDependencies.add(dependency);
  }

  @NotNull
  AndroidProject getAndroidProject() {
    return myAndroidProject;
  }

  @NotNull
  List<ModuleDependency> getModuleDependencies() {
    return myModuleDependencies;
  }

  @NotNull
  SyncModuleModels getModuleModels() {
    return myModuleModels;
  }

  boolean containsVariant(@NotNull String variantName) {
    return myVariantsByName.containsKey(variantName);
  }

  static class ModuleDependency {
    @NotNull final String id;
    @Nullable final String variant;

    ModuleDependency(@NotNull String id, @Nullable String variant) {
      this.id = id;
      this.variant = variant;
    }
  }
}
