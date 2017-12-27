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
package com.android.tools.idea.gradle.project.sync.setup.module.dependency;

import com.android.builder.model.level2.Library;
import com.android.ide.common.repository.GradleCoordinate;
import com.android.ide.common.repository.GradleVersion;
import com.android.tools.idea.gradle.project.model.ide.android.IdeAndroidArtifact;
import com.android.tools.idea.gradle.project.model.ide.android.IdeBaseArtifact;
import com.android.tools.idea.gradle.project.model.ide.android.IdeVariant;
import com.android.tools.idea.gradle.project.model.ide.android.level2.IdeDependencies;
import com.intellij.openapi.components.ServiceManager;
import com.intellij.openapi.roots.DependencyScope;
import com.intellij.openapi.util.text.StringUtil;
import org.jetbrains.annotations.NotNull;

import static com.android.tools.idea.gradle.project.sync.setup.module.dependency.LibraryDependency.PathType.BINARY;
import static com.intellij.openapi.roots.DependencyScope.COMPILE;
import static com.intellij.openapi.roots.DependencyScope.TEST;
import static com.intellij.openapi.util.text.StringUtil.trimLeading;

/**
 * Creates {@link DependencySet} from variant or artifact.
 */
public class DependenciesExtractor {
  @NotNull
  public static DependenciesExtractor getInstance() {
    return ServiceManager.getService(DependenciesExtractor.class);
  }

  /**
   * Get a {@link DependencySet} contains merged dependencies from main artifact and test artifacts.
   *
   * @param variant the variant to extract dependencies from.
   * @return Instance of {@link DependencySet} retrieved from given variant.
   */
  @NotNull
  public DependencySet extractFrom(@NotNull IdeVariant variant) {
    DependencySet dependencies = new DependencySet();

    for (IdeBaseArtifact testArtifact : variant.getTestArtifacts()) {
      populate(dependencies, testArtifact, TEST);
    }

    IdeAndroidArtifact mainArtifact = variant.getMainArtifact();
    populate(dependencies, mainArtifact, COMPILE);

    return dependencies;
  }

  /**
   * @param artifact the artifact to extract dependencies from.
   * @param scope    Scope of the dependencies, e.g. "compile" or "test".
   * @return Instance of {@link DependencySet} retrieved from given artifact.
   */
  @NotNull
  public DependencySet extractFrom(@NotNull IdeBaseArtifact artifact, @NotNull DependencyScope scope) {
    DependencySet dependencies = new DependencySet();
    populate(dependencies, artifact, scope);
    return dependencies;
  }

  private static void populate(@NotNull DependencySet dependencies,
                               @NotNull IdeBaseArtifact artifact,
                               @NotNull DependencyScope scope) {
    IdeDependencies artifactDependencies = artifact.getLevel2Dependencies();

    for (Library library : artifactDependencies.getJavaLibraries()) {
      LibraryDependency libraryDependency = new LibraryDependency(library.getArtifact(), library.getArtifactAddress(), scope);
      libraryDependency.addPath(LibraryDependency.PathType.BINARY, library.getArtifact());
      dependencies.add(libraryDependency);
    }

    for (Library library : artifactDependencies.getAndroidLibraries()) {
      dependencies.add(createLibraryDependencyFromAndroidLibrary(library, scope));
    }

    for (Library library : artifactDependencies.getModuleDependencies()) {
      String gradleProjectPath = library.getProjectPath();
      if (gradleProjectPath != null && !gradleProjectPath.isEmpty()) {
        ModuleDependency dependency = new ModuleDependency(gradleProjectPath, scope);
        dependencies.add(dependency);
      }
    }
  }

  @NotNull
  private static LibraryDependency createLibraryDependencyFromAndroidLibrary(@NotNull Library library,
                                                                             @NotNull DependencyScope scope) {
    LibraryDependency dependency =
      new LibraryDependency(library.getArtifact(), getDependencyName(library, "-"), scope);
    dependency.addPath(BINARY, library.getJarFile());
    dependency.addPath(BINARY, library.getResFolder());

    for (String localJar : library.getLocalJars()) {
      dependency.addPath(BINARY, localJar);
    }
    return dependency;
  }

  /**
   * Get user friendly name of a level2 library dependency.
   *
   * @param library   Level 2 library instance.
   * @param separator String to connect artifact id and version, for example, "-" or ":".
   * @return User friendly name of the dependency.
   * For example, artifactId[:/-]version for external library dependency, and moduleName::variant for module dependency.
   */
  @NotNull
  public static String getDependencyName(@NotNull Library library, @NotNull String separator) {
    String artifactAddress = library.getArtifactAddress();
    GradleCoordinate coordinates = GradleCoordinate.parseCoordinateString(artifactAddress);
    // Artifact address for external libraries are in the format of groupId:artifactId:version@packing, thus can be converted to GradleCoordinate.
    // But artifact address for module dependency is in the format of :moduleName::variant, trim the leading : for module dependency.
    if (coordinates != null) {
      String name = coordinates.getArtifactId();

      GradleVersion version = coordinates.getVersion();
      if (version != null && !"unspecified".equals(version.toString())) {
        name += separator + version;
      }
      assert name != null;
      if (StringUtil.isNotEmpty(coordinates.getGroupId())) {
        return coordinates.getGroupId() + ":" + name;
      }
      return name;
    }
    return trimLeading(artifactAddress, ':');
  }

  /**
   * Like {@link #getDependencyName(Library, String)} but intended for display purposes; names may not be unique
   * (and separator is always ":"). It will only show the artifact id, if that id contains slashes, otherwise
   * it will include the last component of the group id (unless identical to the artifact id).
   *
   * E.g.
   * com.android.support.test.espresso:espresso-core:3.0.1@aar -> espresso-core:3.0.1
   *         android.arch.lifecycle:extensions:1.0.0-beta1@aar -> lifecycle:extensions:1.0.0-beta1
   *                         com.google.guava:guava:11.0.2@jar -> guava:11.0.2
   */
  @NotNull
  public static String getDependencyDisplayName(@NotNull Library library) {
    String artifactAddress = library.getArtifactAddress();
    GradleCoordinate coordinates = GradleCoordinate.parseCoordinateString(artifactAddress);
    if (coordinates != null) {
      String name = coordinates.getArtifactId();
      if (name == null) {
        name = "?";
      }

      // For something like android.arch.lifecycle:runtime, instead of just showing "runtime",
      // we show "lifecycle:runtime"
      if (!name.contains("-")) {
        String groupId = coordinates.getGroupId();
        if (groupId != null) {
          int index = groupId.lastIndexOf('.'); // okay if it doesn't exist
          String groupSuffix = groupId.substring(index + 1);
          if (!groupSuffix.equals(name)) { // e.g. for com.google.guava:guava we'd end up with "guava:guava"
            name = groupSuffix + ":" + name;
          }
        }
      }

      GradleVersion version = coordinates.getVersion();
      if (version != null && !"unspecified".equals(version.toString())) {
        name += ":" + version;
      }
      return name;
    }
    return trimLeading(artifactAddress, ':');
  }
}
