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
package com.android.tools.idea.gradle.project.model.ide.android.level2;

import com.android.annotations.VisibleForTesting;
import com.android.builder.model.AndroidLibrary;
import com.android.builder.model.BaseArtifact;
import com.android.builder.model.Dependencies;
import com.android.builder.model.JavaLibrary;
import com.android.builder.model.level2.DependencyGraphs;
import com.android.builder.model.level2.GlobalLibraryMap;
import com.android.builder.model.level2.GraphItem;
import com.android.builder.model.level2.Library;
import com.android.ide.common.repository.GradleVersion;
import com.android.tools.idea.gradle.project.model.ide.android.ModelCache;
import com.android.tools.idea.gradle.project.sync.ng.NewGradleSync;
import com.google.common.collect.ImmutableList;
import org.gradle.tooling.model.GradleProject;
import org.gradle.tooling.model.UnsupportedMethodException;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;
import java.util.stream.Collectors;

import static com.android.builder.model.level2.Library.*;
import static com.android.tools.idea.gradle.project.model.ide.android.IdeLibraries.computeAddress;

/**
 * Creates {@link IdeDependencies} from {@link BaseArtifact}.
 */
public class IdeDependenciesFactory {
  // Map from unique artifact address to level2 library instance. The library instances are supposed to be shared by all artifacts.
  // When creating IdeLevel2Dependencies, check if current library is available in this map,
  // if it's available, don't create new one, simple add reference to it.
  // If it's not available, create new instance and save to this map, so it can be reused the next time when the same library is added.
  @NotNull private final Map<String, Library> myLibrariesById = new HashMap<>();

  @NotNull private final IdeLibraryFactory myLibraryFactory = new IdeLibraryFactory();
  @NotNull private final BuildFolderPaths myBuildFolderPaths = new BuildFolderPaths();

  /**
   * Finds and stores the path of the module's "build" folder.
   *
   * @param gradleProject contains the path of the module's "build" folder.
   */
  public void findAndAddBuildFolderPath(@NotNull GradleProject gradleProject) {
    myBuildFolderPaths.add(gradleProject);
  }

  /**
   * Create {@link IdeDependencies} from {@link BaseArtifact}.
   *
   * @param artifact     Instance of {@link BaseArtifact} returned from Android plugin.
   * @param modelVersion Version of Android plugin.
   * @return New instance of {@link IdeDependencies}.
   */
  public IdeDependencies create(@NotNull BaseArtifact artifact, @Nullable GradleVersion modelVersion) {
    // Create a fresh model cache for this class, since current instance is based on dependencyGraphs or dependencies, which
    // have been copied in the constructor of IdeBaseArtifact.
    ModelCache modelCache = new ModelCache();
    if (modelVersion != null && modelVersion.getMajor() >= 3 && NewGradleSync.isLevel4Model()) {
      return createFromDependencyGraphs(artifact.getDependencyGraphs());
    }
    return createFromDependencies(artifact.getDependencies(), modelCache);
  }

  /**
   * Call this method on 3.0+ models.
   */
  @VisibleForTesting
  @NotNull
  IdeDependencies createFromDependencyGraphs(@NotNull DependencyGraphs graphs) {
    return createInstance(graphs.getCompileDependencies().stream().map(GraphItem::getArtifactAddress).collect(Collectors.toList()));
  }

  /**
   * Call this method on pre-3.0 models.
   */
  @NotNull
  private IdeDependencies createFromDependencies(@NotNull Dependencies dependencies, @NotNull ModelCache modelCache) {
    Set<String> visited = new HashSet<>();
    populateAndroidLibraries(dependencies.getLibraries(), visited, modelCache);
    populateJavaLibraries(dependencies.getJavaLibraries(), visited, modelCache);
    for (String projectPath : dependencies.getProjects()) {
      if (!visited.contains(projectPath)) {
        visited.add(projectPath);
        myLibrariesById.computeIfAbsent(projectPath, id -> IdeLibraryFactory.create(projectPath, modelCache));
      }
    }
    return createInstance(visited);
  }

  private void populateAndroidLibraries(@NotNull Collection<? extends AndroidLibrary> androidLibraries,
                                        @NotNull Set<String> visited,
                                        @NotNull ModelCache modelCache) {
    for (AndroidLibrary androidLibrary : androidLibraries) {
      String address = computeAddress(androidLibrary);
      if (!visited.contains(address)) {
        visited.add(address);
        myLibrariesById.computeIfAbsent(address, id -> myLibraryFactory.create(androidLibrary, myBuildFolderPaths, modelCache));
        populateAndroidLibraries(androidLibrary.getLibraryDependencies(), visited, modelCache);
        populateJavaLibraries(getJavaDependencies(androidLibrary), visited, modelCache);
      }
    }
  }

  @NotNull
  private static Collection<? extends JavaLibrary> getJavaDependencies(AndroidLibrary androidLibrary) {
    try {
      return androidLibrary.getJavaDependencies();
    }
    catch (UnsupportedMethodException e) {
      return Collections.emptyList();
    }
  }

  private void populateJavaLibraries(@NotNull Collection<? extends JavaLibrary> javaLibraries,
                                     @NotNull Set<String> visited,
                                     @NotNull ModelCache modelCache) {
    for (JavaLibrary javaLibrary : javaLibraries) {
      String address = computeAddress(javaLibrary);
      if (!visited.contains(address)) {
        visited.add(address);
        myLibrariesById.computeIfAbsent(address, k -> myLibraryFactory.create(javaLibrary, modelCache));
        populateJavaLibraries(javaLibrary.getDependencies(), visited, modelCache);
      }
    }
  }

  @NotNull
  private IdeDependencies createInstance(@NotNull Collection<String> artifactAddresses) {
    ImmutableList.Builder<Library> androidLibraries = ImmutableList.builder();
    ImmutableList.Builder<Library> javaLibraries = ImmutableList.builder();
    ImmutableList.Builder<Library> moduleDependencies = ImmutableList.builder();

    for (String address : artifactAddresses) {
      Library library = myLibrariesById.get(address);
      assert library != null;
      switch (library.getType()) {
        case LIBRARY_ANDROID:
          androidLibraries.add(library);
          break;
        case LIBRARY_JAVA:
          javaLibraries.add(library);
          break;
        case LIBRARY_MODULE:
          moduleDependencies.add(library);
          break;
        default:
          throw new UnsupportedOperationException("Unknown library type " + library.getType());
      }
    }
    return new IdeDependenciesImpl(androidLibraries.build(), javaLibraries.build(), moduleDependencies.build());
  }

  /**
   * Populate global library map from {@link GlobalLibraryMap} by making a deep copy.
   *
   * @param globalLibraryMap GlobalLibraryMap model returned from Android Plugin.
   */
  public void setupGlobalLibraryMap(@NotNull GlobalLibraryMap globalLibraryMap) {
    ModelCache modelCache = new ModelCache();
    for (Library library : globalLibraryMap.getLibraries().values()) {
      myLibrariesById.computeIfAbsent(library.getArtifactAddress(), k -> myLibraryFactory.create(library, modelCache));
    }
  }
}
