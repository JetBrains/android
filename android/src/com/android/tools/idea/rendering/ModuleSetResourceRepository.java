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
import com.intellij.openapi.roots.LibraryOrSdkOrderEntry;
import org.jetbrains.android.facet.AndroidFacet;
import org.jetbrains.android.util.AndroidUtils;
import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;

import static com.android.SdkConstants.DOT_AAR;
import static org.jetbrains.android.facet.ResourceFolderManager.addAarsFromModuleLibraries;

/** Resource repository for a module along with all its library dependencies */
public final class ModuleSetResourceRepository extends MultiResourceRepository {
  private final AndroidFacet myFacet;

  private ModuleSetResourceRepository(@NotNull AndroidFacet facet, @NotNull List<? extends ProjectResources> delegates) {
    super(facet.getModule().getName() + " with libraries", delegates);
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
    ProjectResources main = get(facet.getModule(), false /*includeLibraries */);

    // List of module facets the given module depends on
    List<AndroidFacet> dependentFacets = AndroidUtils.getAllAndroidDependencies(facet.getModule(), true);
    List<File> aarDirs = findAarLibraries(facet, dependentFacets);
    if (dependentFacets.isEmpty() && aarDirs.isEmpty()) {
      return Collections.singletonList(main);
    }

    List<ProjectResources> resources = Lists.newArrayListWithExpectedSize(dependentFacets.size() + aarDirs.size());

    for (File root : aarDirs) {
      resources.add(FileProjectResourceRepository.get(root));
    }

    for (AndroidFacet f : dependentFacets) {
      ProjectResources r = get(f.getModule(), false /*includeLibraries */);
      resources.add(r);
    }

    resources.add(main);

    return resources;
  }

  @NotNull
  private static List<File> findAarLibraries(AndroidFacet facet, List<AndroidFacet> dependentFacets) {
    if (facet.isGradleProject()) {
      // Use the gradle model if available, but if not, fall back to using plain IntelliJ library dependencies
      // which have been persisted since the most recent sync
      if (facet.getIdeaAndroidProject() != null) {
        List<AndroidLibrary> libraries = Lists.newArrayList();
        addGradleLibraries(libraries, facet);
        for (AndroidFacet f : dependentFacets) {
          addGradleLibraries(libraries, f);
        }
        return findAarLibrariesFromGradle(dependentFacets, libraries);
      } else {
        return findAarLibrariesFromIntelliJ(facet, dependentFacets);
      }
    }

    return Collections.emptyList();
  }

  /**
   *  Reads IntelliJ library definitions ({@link LibraryOrSdkOrderEntry}) and if possible, finds a corresponding
   * {@code .aar} resource library to include. This works before the Gradle project has been initialized.
   */
  private static List<File> findAarLibrariesFromIntelliJ(AndroidFacet facet, List<AndroidFacet> dependentFacets) {
    // Find .aar libraries from old IntelliJ library definitions
    Set<File> dirs = Sets.newHashSet();
    addAarsFromModuleLibraries(facet, dirs);
    for (AndroidFacet f : dependentFacets) {
      addAarsFromModuleLibraries(f, dirs);
    }
    List<File> sorted = new ArrayList<File>(dirs);
    // Sort to ensure consistent results between pre-model sync order of resources and
    // the post-sync order. (Also see sort comment in the method below.)
    Collections.sort(sorted);
    return sorted;
  }

  /**
   * Looks up the library dependencies from the Gradle tools model and returns the corresponding {@code .aar}
   * resource directories.
   */
  @NotNull
  private static List<File> findAarLibrariesFromGradle(List<AndroidFacet> dependentFacets, List<AndroidLibrary> libraries) {
    // Pull out the unique directories, in case multiple modules point to the same .aar folder
    Set<File> files = Sets.newHashSetWithExpectedSize(dependentFacets.size());

    Set<String> moduleNames = Sets.newHashSet();
    for (AndroidFacet f : dependentFacets) {
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
        // Since this library has project!=null, it exists in module form; don't
        // add it here.
        moduleNames.add(libraryName);
        continue;
      } else {
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

    List<File> dirs = Lists.newArrayList();
    for (File resFolder : files) {
      dirs.add(resFolder);
    }

    // Sort alphabetically to ensure that we keep a consistent order of these libraries;
    // otherwise when we jump from libraries initialized from IntelliJ library binary paths
    // to gradle project state, the order difference will cause the merged project resource
    // maps to have to be recomputed
    Collections.sort(dirs);
    return dirs;
  }

  private static void addGradleLibraries(List<AndroidLibrary> list, AndroidFacet facet) {
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
