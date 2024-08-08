/*
 * Copyright 2016 The Bazel Authors. All rights reserved.
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
package com.google.idea.blaze.base.sync.libraries;

import static com.google.common.collect.ImmutableSet.toImmutableSet;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Lists;
import com.google.common.collect.Sets;
import com.google.idea.blaze.base.model.BlazeLibrary;
import com.google.idea.blaze.base.model.BlazeProjectData;
import com.google.idea.blaze.base.model.LibraryFilesProvider;
import com.google.idea.blaze.base.model.LibraryKey;
import com.google.idea.blaze.base.projectview.ProjectViewSet;
import com.google.idea.blaze.base.scope.BlazeContext;
import com.google.idea.blaze.base.sync.BlazeSyncPlugin;
import com.google.idea.blaze.common.PrintOutput;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.externalSystem.service.project.IdeModifiableModelsProvider;
import com.intellij.openapi.externalSystem.service.project.IdeModifiableModelsProviderImpl;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.DependencyScope;
import com.intellij.openapi.roots.ModifiableRootModel;
import com.intellij.openapi.roots.libraries.Library;
import com.intellij.openapi.roots.libraries.LibraryTable;
import com.intellij.openapi.roots.libraries.LibraryTablesRegistrar;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.function.Predicate;

/** Edits IntelliJ libraries */
public class LibraryEditor {
  private static final Logger logger = Logger.getInstance(LibraryEditor.class);

  public static void updateProjectLibraries(
      Project project,
      BlazeContext context,
      ProjectViewSet projectViewSet,
      BlazeProjectData blazeProjectData,
      Collection<BlazeLibrary> libraries) {
    Set<LibraryKey> intelliJLibraryState = Sets.newHashSet();
    IdeModifiableModelsProvider modelsProvider = new IdeModifiableModelsProviderImpl(project);
    for (Library library : modelsProvider.getAllLibraries()) {
      String name = library.getName();
      if (name != null) {
        intelliJLibraryState.add(LibraryKey.fromIntelliJLibraryName(name));
      }
    }
    context.output(PrintOutput.log(String.format("Workspace has %d libraries", libraries.size())));

    try {
      Set<String> newLibraryKeys = new HashSet<>();
      libraries.forEach(
          library -> {
            LibraryFilesProvider libraryFilesProvider =
                LibraryFilesProviderFactory.getInstance(project).get(library);
            String key = libraryFilesProvider.getName();
            if (newLibraryKeys.add(key)) {
              updateLibrary(modelsProvider, blazeProjectData, libraryFilesProvider);
            }
          });

      // Garbage collect unused libraries
      List<LibrarySource> librarySources = Lists.newArrayList();
      for (BlazeSyncPlugin syncPlugin : BlazeSyncPlugin.EP_NAME.getExtensions()) {
        LibrarySource librarySource = syncPlugin.getLibrarySource(projectViewSet, blazeProjectData);
        if (librarySource != null) {
          librarySources.add(librarySource);
        }
      }
      Predicate<Library> gcRetentionFilter =
          librarySources.stream()
              .map(LibrarySource::getGcRetentionFilter)
              .filter(Objects::nonNull)
              .reduce(Predicate::or)
              .orElse(o -> false);

      for (LibraryKey libraryKey : intelliJLibraryState) {
        String libraryIntellijName = libraryKey.getIntelliJLibraryName();
        if (!newLibraryKeys.contains(libraryIntellijName)) {
          Library library = modelsProvider.getLibraryByName(libraryIntellijName);
          if (!gcRetentionFilter.test(library)) {
            if (library != null) {
              modelsProvider.removeLibrary(library);
            }
          }
        }
      }
    } finally {
      modelsProvider.commit();
    }
  }

  /**
   * Updates the library in IntelliJ's project model.
   *
   * <p>Note: Callers of this method must invoke {@link IdeModifiableModelsProvider#commit()} on the
   * passed {@link IdeModifiableModelsProvider} for any changes to take effect. Be aware that {@code
   * commit()} should only be called once after all modifications as frequent calls can be slow.
   *
   * @param project the IntelliJ project
   * @param blazeProjectData data class contains BlazeLibrary information, decoder for artifact
   *     location. Since it has not yet been cached on disk during sync, we cannot get latest one
   *     via BlazeProjectData.getInstance. Callers need to provide it.
   * @param modelsProvider a modifier for IntelliJ's project model which supports quick application
   *     of massive modifications to the project model
   * @param blazeLibrary the library which should be updated in the project context
   */
  public static void updateLibrary(
      Project project,
      BlazeProjectData blazeProjectData,
      IdeModifiableModelsProvider modelsProvider,
      BlazeLibrary blazeLibrary) {
    updateLibrary(
        modelsProvider,
        blazeProjectData,
        LibraryFilesProviderFactory.getInstance(project).get(blazeLibrary));
  }

  private static void updateLibrary(
      IdeModifiableModelsProvider modelsProvider,
      BlazeProjectData blazeProjectData,
      LibraryFilesProvider libraryFilesProvider) {
    LibraryModifier libraryModifier = new LibraryModifier(libraryFilesProvider, modelsProvider);
    libraryModifier.updateModifiableModel(blazeProjectData);
  }

  /**
   * Configures the passed libraries as dependencies for the given root in IntelliJ's project model.
   * Libraries which don't exist in the project model will be ignored.
   *
   * <p>Note: Callers of this method must invoke {@code commit()} on the passed {@link
   * ModifiableRootModel} or on higher-level model providers for any changes to take effect. Be
   * aware that {@code commit()} should only be called once after all modifications as frequent
   * calls can be slow.
   *
   * @param modifiableRootModel a modifier for a specific root in IntelliJ's project model
   * @param libraries the libraries to add as dependencies
   */
  public static void configureDependencies(
      Project project,
      ModifiableRootModel modifiableRootModel,
      Collection<BlazeLibrary> libraries) {
    LibraryTable libraryTable =
        LibraryTablesRegistrar.getInstance().getLibraryTable(modifiableRootModel.getProject());
    ImmutableSet<String> libraryNames =
        libraries.stream()
            .map(library -> LibraryFilesProviderFactory.getInstance(project).get(library).getName())
            .collect(toImmutableSet());

    ImmutableList<Library> foundLibraries = findLibraries(libraryNames, libraryTable);
    // Add the libraries in a batch operation as adding them one after the other is not performant.
    modifiableRootModel.addLibraryEntries(
        foundLibraries, DependencyScope.COMPILE, /* exported= */ false);
  }

  private static ImmutableList<Library> findLibraries(
      Collection<String> libraryNames, LibraryTable libraryTable) {
    ImmutableList.Builder<Library> foundLibraries = ImmutableList.builder();
    ImmutableList.Builder<String> missingLibraries = ImmutableList.builder();
    for (String libraryName : libraryNames) {
      // This call is slow and causes freezes when done through IdeModifiableModelsProvider.
      Library foundLibrary = libraryTable.getLibraryByName(libraryName);
      if (foundLibrary == null) {
        missingLibraries.add(libraryName);
      } else {
        foundLibraries.add(foundLibrary);
      }
    }
    logMissingLibraries(missingLibraries.build());
    return foundLibraries.build();
  }

  private static void logMissingLibraries(Iterable<String> libraries) {
    String concatenatedLibraries = String.join(", ", libraries);
    if (!concatenatedLibraries.isEmpty()) {
      logger.error(
          "Some libraries are missing. Please resync the project to resolve. Missing libraries: %s",
          concatenatedLibraries);
    }
  }
}
