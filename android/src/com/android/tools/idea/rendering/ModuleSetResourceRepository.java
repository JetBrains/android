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

import com.android.annotations.VisibleForTesting;
import com.android.builder.model.AndroidLibrary;
import com.android.tools.idea.gradle.IdeaAndroidProject;
import com.google.common.collect.Lists;
import com.google.common.collect.Sets;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleManager;
import com.intellij.openapi.project.Project;
import org.jetbrains.android.facet.AndroidFacet;
import org.jetbrains.android.util.AndroidUtils;
import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.util.Collections;
import java.util.List;
import java.util.Set;

import static com.android.SdkConstants.DOT_AAR;

/** Resource repository for a module along with all its library dependencies */
public final class ModuleSetResourceRepository extends MultiResourceRepository {
  private final AndroidFacet myFacet;

  private ModuleSetResourceRepository(@NotNull AndroidFacet facet, @NotNull List<? extends ProjectResources> delegates) {
    super(facet.getModule().getName(), delegates);
    myFacet = facet;
  }

  @NotNull
  public static ProjectResources create(@NotNull final AndroidFacet facet) {
    boolean refresh = facet.getIdeaAndroidProject() == null;

    List<ProjectResources> resources = computeRepositories(facet);
    final ModuleSetResourceRepository repository = new ModuleSetResourceRepository(facet, resources);

    // If the model is not yet ready, we may get an incomplete set of resource
    // directories, so in that case update the repository when the model is available.
    if (refresh) {
      facet.addListener(new AndroidFacet.GradleProjectAvailableListener() {
        @Override
        public void gradleProjectAvailable(@NotNull IdeaAndroidProject project) {
          facet.removeListener(this);
          repository.updateRoots();
        }
      });
    }

    return repository;
  }

  private static List<ProjectResources> computeRepositories(@NotNull final AndroidFacet facet) {
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
      return Collections.singletonList(main);
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
        // We should only add .aar dependencies if they aren't already provided as modules.
        // For now, the way we associate them with each other is via the library name;
        // in the future the model will provide this for us

        String libraryName = null;
        String projectName = library.getProject();
        if (projectName != null && !projectName.isEmpty()) {
          libraryName = projectName.substring(projectName.lastIndexOf(':') + 1);
        } else {
          // Pre 0.5 support: remove soon
          File folder = library.getFolder();
          String name = folder.getName();
          if (name.endsWith(DOT_AAR)) {
            libraryName = name.substring(0, name.length() - DOT_AAR.length());
          }
        }
        if (libraryName != null && !moduleNames.contains(libraryName)) {
          File resFolder = library.getResFolder();
          if (resFolder.exists()) {
            files.add(resFolder);

            // Don't add it again!
            moduleNames.add(libraryName);
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

    return resources;
  }

  private static void addAndroidLibraries(List<AndroidLibrary> list, AndroidFacet facet) {
    IdeaAndroidProject gradleProject = facet.getIdeaAndroidProject();
    if (gradleProject != null) {
      list.addAll(gradleProject.getSelectedVariant().getMainArtifactInfo().getDependencies().getLibraries());
    }
  }

  void updateRoots() {
    List<ProjectResources> repositories = computeRepositories(myFacet);
    updateRoots(repositories);
  }

  @VisibleForTesting
  void updateRoots(List<ProjectResources> resourceDirectories) {
    if (resourceDirectories.equals(myChildren)) {
      // Nothing changed (including order); nothing to do
      return;
    }

    setChildren(resourceDirectories);
  }

  /**
   * Called when module roots have changed in the given project. Locates all
   * the {@linkplain ModuleSetResourceRepository} instances (but only those that
   * have already been initialized) and updates the roots, if necessary.
   *
   * @param project the project whose module roots changed.
   */
  public static void moduleRootsChanged(@NotNull Project project) {
    ModuleManager moduleManager = ModuleManager.getInstance(project);
    for (Module module : moduleManager.getModules()) {
      moduleRootsChanged(module);
    }
  }

  /**
   * Called when module roots have changed in the given module. Locates the
   * {@linkplain ModuleSetResourceRepository} instance (but only if it has
   * already been initialized) and updates its roots, if necessary.
   * <p>
   * TODO: Currently, this method is only called during a Gradle project import.
   * We should call it for non-Gradle projects after modules are changed in the
   * project structure dialog etc. with
   *   project.getMessageBus().connect().subscribe(ProjectTopics.PROJECT_ROOTS, new ModuleRootListener() { ... }
   *
   *
   * @param module the module whose roots changed
   */
  public static void moduleRootsChanged(@NotNull Module module) {
    AndroidFacet facet = AndroidFacet.getInstance(module);
    if (facet != null) {
      if (facet.isGradleProject() && facet.getIdeaAndroidProject() == null) {
        // Project not yet fully initialized; no need to do a sync now because our
        // GradleProjectAvailableListener will be called as soon as it is and do a proper sync
        return;
      }
      ProjectResources resources = facet.getProjectResources(true, false);
      if (resources instanceof ModuleSetResourceRepository) {
        ModuleSetResourceRepository moduleSetRepository = (ModuleSetResourceRepository)resources;
        moduleSetRepository.updateRoots();
      }
    }
  }

  @VisibleForTesting
  @NotNull
  static ProjectResources create(AndroidFacet facet, List<ProjectResources> modules) {
    return new ModuleSetResourceRepository(facet, modules);
  }
}
