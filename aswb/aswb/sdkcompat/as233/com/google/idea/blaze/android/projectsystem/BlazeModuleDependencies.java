/*
 * Copyright 2024 The Bazel Authors. All rights reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.google.idea.blaze.android.projectsystem;

import static com.google.common.collect.ImmutableList.toImmutableList;

import com.android.ide.common.repository.GoogleMavenArtifactId;
import com.android.projectmodel.ExternalAndroidLibrary;
import com.android.tools.idea.projectsystem.DependencyScopeType;
import com.android.tools.idea.rendering.PsiClassViewClass;
import com.android.tools.module.ModuleDependencies;
import com.android.tools.module.ViewClass;
import com.google.common.collect.ImmutableList;
import com.google.idea.blaze.android.resources.BlazeLightResourceClassService;
import com.intellij.openapi.module.Module;
import com.intellij.psi.JavaPsiFacade;
import java.util.List;
import java.util.Objects;
import java.util.function.Predicate;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/** ASwB specific implementation of [ModuleDependencies]. */
public class BlazeModuleDependencies implements ModuleDependencies {

  Module module;

  BlazeModuleDependencies(Module module) {
    this.module = module;
  }

  @Override
  public boolean dependsOn(@NotNull GoogleMavenArtifactId googleMavenArtifactId) {
    return false;
  }

  /**
   * Fetches the resource packages within the project and from external dependencies. Input
   * parameter includeExternalLibraries is ignored as ASwB/Blaze uses a single workspace module to
   * map both types of resources.
   */
  @NotNull
  @Override
  public List<String> getResourcePackageNames(boolean includeExternalLibraries) {
    // TODO(b/304821496): Add an integration test to test it similar to BuildDependenciesTest
    // TODO(b/307604153): Update AndroidExternalLibraryManager to read package name from multiple
    // locations
    return ImmutableList.<String>builder()
        .addAll(
            BlazeModuleSystem.getInstance(module)
                .getAndroidLibraryDependencies(DependencyScopeType.MAIN)
                .stream()
                .map(ExternalAndroidLibrary::getPackageName)
                .filter(Objects::nonNull)
                .filter(Predicate.not(String::isEmpty))
                .collect(toImmutableList()))
        .addAll(
            BlazeLightResourceClassService.getInstance(module.getProject())
                .getWorkspaceResourcePackages())
        .build();
  }

  @Nullable
  @Override
  public ViewClass findViewClass(@NotNull String fqcn) {
    JavaPsiFacade facade = JavaPsiFacade.getInstance(module.getProject());
    return new PsiClassViewClass(
        facade.findClass(fqcn, module.getModuleWithDependenciesAndLibrariesScope(false)));
  }
}
