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
package com.android.tools.idea.gradle.structure.model.android;

import com.android.builder.model.*;
import com.android.ide.common.repository.GradleVersion;
import com.android.tools.idea.gradle.AndroidGradleModel;
import com.android.tools.idea.gradle.dsl.model.dependencies.ArtifactDependencyModel;
import com.android.tools.idea.gradle.dsl.model.dependencies.ModuleDependencyModel;
import com.android.tools.idea.gradle.structure.model.PsArtifactDependencySpec;
import com.android.tools.idea.gradle.structure.model.PsModelCollection;
import com.android.tools.idea.gradle.structure.model.PsModule;
import com.android.tools.idea.gradle.structure.model.PsParsedDependencies;
import com.android.tools.idea.gradle.util.GradleUtil;
import com.google.common.annotations.VisibleForTesting;
import com.google.common.collect.Maps;
import com.intellij.openapi.module.Module;
import com.intellij.util.containers.Predicate;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import static com.android.tools.idea.gradle.structure.model.pom.MavenPoms.findDependenciesInPomFile;

class PsAndroidDependencyCollection implements PsModelCollection<PsAndroidDependency> {
  @NotNull private final PsAndroidModule myParent;

  @NotNull private final Map<String, PsModuleDependency> myModuleDependenciesByGradlePath = Maps.newHashMap();
  @NotNull private final Map<String, PsLibraryDependency> myLibraryDependenciesBySpec = Maps.newHashMap();

  PsAndroidDependencyCollection(@NotNull PsAndroidModule parent) {
    myParent = parent;
    parent.forEachVariant(new Predicate<PsVariant>() {
      @Override
      public boolean apply(@Nullable PsVariant variant) {
        if (variant == null) {
          return false;
        }
        addDependencies(variant);
        return true;
      }
    });
  }

  private void addDependencies(@NotNull PsVariant variant) {
    variant.forEachArtifact(new Predicate<PsAndroidArtifact>() {
      @Override
      public boolean apply(@Nullable PsAndroidArtifact artifact) {
        if (artifact == null) {
          return false;
        }
        collectDependencies(artifact);
        return true;
      }
    });
  }

  private void collectDependencies(@NotNull PsAndroidArtifact artifact) {
    BaseArtifact resolvedArtifact = artifact.getResolvedModel();
    if (resolvedArtifact == null) {
      return;
    }
    AndroidGradleModel gradleModel = artifact.getGradleModel();
    Dependencies dependencies = GradleUtil.getDependencies(resolvedArtifact, gradleModel.getModelVersion());

    for (AndroidLibrary androidLibrary : dependencies.getLibraries()) {
      String gradlePath = androidLibrary.getProject();
      if (gradlePath != null) {
        String projectVariant = androidLibrary.getProjectVariant();
        addModule(gradlePath, artifact, projectVariant);
      }
      else {
        // This is an AAR
        addLibrary(androidLibrary, artifact);
      }
    }

    for (JavaLibrary javaLibrary : dependencies.getJavaLibraries()) {
      addLibrary(javaLibrary, artifact);
    }
  }

  private void addModule(@NotNull String gradlePath, @NotNull PsAndroidArtifact artifact, @Nullable String projectVariant) {
    PsParsedDependencies parsedDependencies = myParent.getParsedDependencies();

    ModuleDependencyModel parsedModel = parsedDependencies.findMatchingModuleDependency(gradlePath, artifact);

    Module resolvedModule = null;
    PsModule module = myParent.getParent().findModuleByGradlePath(gradlePath);
    if (module != null) {
      resolvedModule = module.getResolvedModel();
    }
    PsModuleDependency dependency = findElement(gradlePath, PsModuleDependency.class);
    if (dependency == null) {
      dependency = new PsModuleDependency(myParent, gradlePath, projectVariant, resolvedModule, artifact, parsedModel);
      myModuleDependenciesByGradlePath.put(gradlePath, dependency);
    }
    else {
      if (dependency.getParsedModel() == null && parsedModel != null) {
        dependency.setParsedModel(parsedModel);
      }
    }
    dependency.addContainer(artifact);
  }

  @Nullable
  private PsAndroidDependency addLibrary(@NotNull Library library, @NotNull PsAndroidArtifact artifact) {
    PsParsedDependencies parsedDependencies = myParent.getParsedDependencies();

    MavenCoordinates coordinates = library.getResolvedCoordinates();
    if (coordinates != null) {
      PsArtifactDependencySpec spec = PsArtifactDependencySpec.create(coordinates);

      ArtifactDependencyModel matchingParsedDependency = parsedDependencies.findMatchingArtifactDependency(coordinates, artifact);
      if (matchingParsedDependency != null) {
        String parsedVersionValue = matchingParsedDependency.version().value();
        if (parsedVersionValue != null) {
          // The dependency has a version in the build.gradle file.
          // "tryParse" just in case the build.file has an invalid version.
          GradleVersion parsedVersion = GradleVersion.tryParse(parsedVersionValue);

          GradleVersion versionFromGradle = GradleVersion.parse(coordinates.getVersion());
          if (parsedVersion != null && compare(parsedVersion, versionFromGradle) == 0) {
            // Match.
            return addLibrary(library, spec, artifact, matchingParsedDependency);
          }
          else {
            // Version mismatch. This can happen when the project specifies an artifact version but Gradle uses a different version
            // from a transitive dependency.
            // Example:
            // 1. Module 'app' depends on module 'lib'
            // 2. Module 'app' depends on Guava 18.0
            // 3. Module 'lib' depends on Guava 19.0
            // Gradle will force module 'app' to use Guava 19.0

            // This is a case that may look as a version mismatch:
            //
            // testCompile 'junit:junit:4.11+'
            // androidTestCompile 'com.android.support.test.espresso:espresso-core:2.2.1'
            //
            // Here 'espresso' brings junit 4.12, but there is no mismatch with junit 4.11, because they are in different artifacts.
            PsLibraryDependency potentialDuplicate = null;
            for (PsLibraryDependency dependency : myLibraryDependenciesBySpec.values()) {
              if (dependency.getParsedModel() == matchingParsedDependency) {
                potentialDuplicate = dependency;
                break;
              }
            }
            if (potentialDuplicate != null) {
              // TODO match ArtifactDependencyModel#configurationName with potentialDuplicate.getContainers().artifact
            }

            // Create the dependency model that will be displayed in the "Dependencies" table.
            addLibrary(library, spec, artifact, matchingParsedDependency);

            // Create a dependency model for the transitive dependency, so it can be displayed in the "Variants" tool window.
            return addLibrary(library, spec, artifact, null);
          }
        }
      }
      else {
        // This dependency was not declared, it could be a transitive one.
        return addLibrary(library, spec, artifact, null);
      }
    }
    return null;
  }

  @Nullable
  private PsAndroidDependency addLibrary(@NotNull Library library,
                                         @NotNull PsArtifactDependencySpec resolvedSpec,
                                         @NotNull PsAndroidArtifact artifact,
                                         @Nullable ArtifactDependencyModel parsedModel) {
    if (library instanceof AndroidLibrary) {
      AndroidLibrary androidLibrary = (AndroidLibrary)library;
      return addAndroidLibrary(androidLibrary, resolvedSpec, artifact, parsedModel);
    }
    else if (library instanceof JavaLibrary) {
      JavaLibrary javaLibrary = (JavaLibrary)library;
      return addJavaLibrary(javaLibrary, resolvedSpec, artifact, parsedModel);
    }
    return null;
  }

  @NotNull
  private PsAndroidDependency addAndroidLibrary(@NotNull AndroidLibrary androidLibrary,
                                                @NotNull PsArtifactDependencySpec resolvedSpec,
                                                @NotNull PsAndroidArtifact artifact,
                                                @Nullable ArtifactDependencyModel parsedModel) {
    PsAndroidDependency dependency = getOrCreateDependency(resolvedSpec, androidLibrary, artifact, parsedModel);

    for (Library library : androidLibrary.getLibraryDependencies()) {
      addTransitive(library, artifact, dependency);
    }

    AndroidGradleModel gradleModel = artifact.getGradleModel();
    if (gradleModel.supportsDependencyGraph()) {
      for (Library library : androidLibrary.getJavaDependencies()) {
        addTransitive(library, artifact, dependency);
      }
    }

    dependency.addContainer(artifact);
    return dependency;
  }

  @NotNull
  private PsAndroidDependency addJavaLibrary(@NotNull JavaLibrary javaLibrary,
                                             @NotNull PsArtifactDependencySpec resolvedSpec,
                                             @NotNull PsAndroidArtifact artifact,
                                             @Nullable ArtifactDependencyModel parsedModel) {
    PsAndroidDependency dependency = getOrCreateDependency(resolvedSpec, javaLibrary, artifact, parsedModel);

    for (Library library : javaLibrary.getDependencies()) {
      addTransitive(library, artifact, dependency);
    }

    dependency.addContainer(artifact);
    return dependency;
  }

  private void addTransitive(@NotNull Library transitiveDependency,
                             @NotNull PsAndroidArtifact artifact,
                             @NotNull PsAndroidDependency dependency) {
    PsAndroidDependency transitive = addLibrary(transitiveDependency, artifact);
    if (transitive != null && dependency instanceof PsLibraryDependency) {
      PsLibraryDependency libraryDependency = (PsLibraryDependency)dependency;
      libraryDependency.addTransitiveDependency(transitive.getValueAsText());
    }
  }

  @VisibleForTesting
  static int compare(@NotNull GradleVersion parsedVersion, @NotNull GradleVersion versionFromGradle) {
    int result = versionFromGradle.compareTo(parsedVersion);
    if (result == 0) {
      return result;
    }
    else if (result < 0) {
      // The "parsed" version might have a '+' sign.
      if (parsedVersion.getMajorSegment().acceptsGreaterValue()) {
        return 0;
      }
      else if (parsedVersion.getMinorSegment() != null && parsedVersion.getMinorSegment().acceptsGreaterValue()) {
        return parsedVersion.getMajor() - versionFromGradle.getMajor();
      }
      else if (parsedVersion.getMicroSegment() != null && parsedVersion.getMicroSegment().acceptsGreaterValue()) {
        result = parsedVersion.getMajor() - versionFromGradle.getMajor();
        if (result != 0) {
          return result;
        }
        return parsedVersion.getMinor() - versionFromGradle.getMinor();
      }
    }
    return result;
  }

  @NotNull
  private PsAndroidDependency getOrCreateDependency(@NotNull PsArtifactDependencySpec resolvedSpec,
                                                    @NotNull Library library,
                                                    @NotNull PsAndroidArtifact artifact,
                                                    @Nullable ArtifactDependencyModel parsedModel) {
    String compactNotation = resolvedSpec.toString();
    PsLibraryDependency dependency = myLibraryDependenciesBySpec.get(compactNotation);
    if (dependency == null) {
      dependency = new PsLibraryDependency(myParent, resolvedSpec, library, artifact, parsedModel);
      myLibraryDependenciesBySpec.put(compactNotation, dependency);

      File libraryPath = null;
      if (library instanceof AndroidLibrary) {
        libraryPath = ((AndroidLibrary)library).getBundle();
      }
      else if (library instanceof JavaLibrary) {
        libraryPath = ((JavaLibrary)library).getJarFile();
      }
      if (!artifact.getGradleModel().supportsDependencyGraph()) {
        // If the Android model supports the full dependency graph (v. 2.2.0+), we don't need to look for transitive dependencies in POM
        // files.
        List<PsArtifactDependencySpec> pomDependencies = Collections.emptyList();
        if (libraryPath != null) {
          pomDependencies = findDependenciesInPomFile(libraryPath);
        }
        dependency.setPomDependencies(pomDependencies);
      }
    }
    else {
      if (dependency.getParsedModel() == null && parsedModel != null) {
        dependency.setParsedModel(parsedModel);
      }
    }
    return dependency;
  }

  @Override
  @Nullable
  public <S extends PsAndroidDependency> S findElement(@NotNull String name, @Nullable Class<S> type) {
    if (PsModuleDependency.class.equals(type)) {
      return type.cast(myModuleDependenciesByGradlePath.get(name));
    }
    if (PsLibraryDependency.class.equals(type)) {
      return type.cast(myLibraryDependenciesBySpec.get(name));
    }
    return null;
  }

  @Override
  public void forEach(@NotNull Predicate<PsAndroidDependency> function) {
    for (PsLibraryDependency dependency : myLibraryDependenciesBySpec.values()) {
      function.apply(dependency);
    }
    for (PsModuleDependency dependency : myModuleDependenciesByGradlePath.values()) {
      function.apply(dependency);
    }
  }

  public void forEachDeclaredDependency(@NotNull Predicate<PsAndroidDependency> function) {
    for (PsLibraryDependency dependency : myLibraryDependenciesBySpec.values()) {
      if (dependency.isDeclared()) {
        function.apply(dependency);
      }
    }
    for (PsModuleDependency dependency : myModuleDependenciesByGradlePath.values()) {
      if (dependency.isDeclared()) {
        function.apply(dependency);
      }
    }
  }

  public void forEachModuleDependency(@NotNull Predicate<PsModuleDependency> function) {
    for (PsModuleDependency dependency : myModuleDependenciesByGradlePath.values()) {
      function.apply(dependency);
    }
  }
}
