/*
 * Copyright (C) 2013 The Android Open Source Project
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
package com.android.tools.idea.rendering;

import com.android.SdkConstants;
import com.android.annotations.VisibleForTesting;
import com.android.builder.model.AndroidLibrary;
import com.android.tools.idea.gradle.IdeaAndroidProject;
import com.google.common.collect.Lists;
import com.google.common.collect.Sets;
import org.jetbrains.android.facet.AndroidFacet;
import org.jetbrains.android.util.AndroidUtils;
import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.util.List;
import java.util.Set;

import static com.android.SdkConstants.DOT_AAR;

/** Resource repository for a module along with all its library dependencies */
final class ModuleSetResourceRepository extends MultiResourceRepository {
  private ModuleSetResourceRepository(@NotNull List<? extends ProjectResources> delegates) {
    super(delegates);
    assert delegates.size() >= 2; // factory should delegate to a plain ModuleResourceRepository if not
  }

  @NotNull
  public static ProjectResources create(@NotNull AndroidFacet facet) {
    // List of module facets the given module depends on
    List<AndroidFacet> facets = AndroidUtils.getAllAndroidDependencies(facet.getModule(), true);

    // Android libraries (.aar libraries) the module, or any of the modules it depends on
    List<AndroidLibrary> libraries = Lists.newArrayList();
    addAndroidLibraries(libraries, facet);
    for (AndroidFacet f : facets) {
      addAndroidLibraries(libraries, f);
    }

    boolean includeLibraries = false;
    ProjectResources main = get(facet.getModule(), includeLibraries);

    if (facets.isEmpty() && libraries.isEmpty()) {
      return main;
    }

    List<ProjectResources> resources = Lists.newArrayListWithExpectedSize(facets.size());

    if (libraries != null) {
      // Pull out the unique directories, in case multiple modules point to the same .aar folder
      Set<File> files = Sets.newHashSetWithExpectedSize(facets.size());

      Set<String> moduleNames = Sets.newHashSet();
      for (AndroidFacet f : facets) {
        moduleNames.add(f.getModule().getName());
      }
      for (AndroidLibrary library : libraries) {
        File folder = library.getFolder();
        String name = folder.getName();
        // We should only add .aar dependencies if they aren't already provided as modules.
        // For now, the way we associate them with each other is via the library name;
        // in the future the model will provide this for us
        if (name.endsWith(DOT_AAR)) {
          String libraryName = name.substring(0, name.length() - DOT_AAR.length());
          if (!moduleNames.contains(libraryName)) {
            File resFolder = library.getResFolder();
            if (resFolder.exists()) {
              files.add(resFolder);

              // Don't add it again!
              moduleNames.add(libraryName);
            }
          }
        }
      }

      for (File resFolder : files) {
        resources.add(FileProjectResourceRepository.get(resFolder));
      }
    }

    for (AndroidFacet f : facets) {
      ProjectResources r = get(f.getModule(), includeLibraries);
      resources.add(r);
    }

    resources.add(main);

    // TODO: How do we update the module set if the module roots (dependencies) have changed?
    // See ModuleResourceRepository#updateRoots for similar logic we should apply here (but for dependencies, not resource dirs obviously)

    return new ModuleSetResourceRepository(resources);
  }

  private static void addAndroidLibraries(List<AndroidLibrary> list, AndroidFacet facet) {
    IdeaAndroidProject gradleProject = facet.getIdeaAndroidProject();
    if (gradleProject != null) {
      list.addAll(gradleProject.getDelegate().getDefaultConfig().getDependencies().getLibraries());
    }
  }

  @VisibleForTesting
  @NotNull
  static ProjectResources create(List<ProjectResources> modules) {
    return new ModuleSetResourceRepository(modules);
  }
}
