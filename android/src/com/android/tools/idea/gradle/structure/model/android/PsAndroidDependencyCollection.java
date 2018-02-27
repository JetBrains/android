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

import com.android.builder.model.level2.Library;
import com.android.ide.common.gradle.model.IdeBaseArtifact;
import com.android.ide.common.gradle.model.level2.IdeDependencies;
import com.android.ide.common.repository.GradleCoordinate;
import com.android.ide.common.repository.GradleVersion;
import com.android.tools.idea.gradle.dsl.api.dependencies.ArtifactDependencyModel;
import com.android.tools.idea.gradle.dsl.api.dependencies.DependencyModel;
import com.android.tools.idea.gradle.dsl.api.dependencies.ModuleDependencyModel;
import com.android.tools.idea.gradle.structure.model.PsArtifactDependencySpec;
import com.android.tools.idea.gradle.structure.model.PsModelCollection;
import com.android.tools.idea.gradle.structure.model.PsModule;
import com.android.tools.idea.gradle.structure.model.PsParsedDependencies;
import com.google.common.annotations.VisibleForTesting;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.intellij.openapi.module.Module;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Consumer;

import static com.android.tools.idea.gradle.structure.model.pom.MavenPoms.findDependenciesInPomFile;
import static com.intellij.openapi.util.text.StringUtil.isEmpty;

class PsAndroidDependencyCollection implements PsModelCollection<PsAndroidDependency> {
  @NotNull private final PsAndroidModule myParent;

  @NotNull private final Map<String, PsModuleAndroidDependency> myModuleDependenciesByGradlePath = Maps.newHashMap();
  @NotNull private final Map<String, PsLibraryAndroidDependency> myLibraryDependenciesBySpec = Maps.newHashMap();

  PsAndroidDependencyCollection(@NotNull PsAndroidModule parent) {
    myParent = parent;
    parent.forEachVariant(this::addDependencies);
  }

  private void addDependencies(@NotNull PsVariant variant) {
    variant.forEachArtifact(this::collectDependencies);
  }

  private void collectDependencies(@NotNull PsAndroidArtifact artifact) {
    IdeBaseArtifact resolvedArtifact = artifact.getResolvedModel();
    if (resolvedArtifact == null) {
      return;
    }
    IdeDependencies dependencies = resolvedArtifact.getLevel2Dependencies();

    for (Library androidLibrary : dependencies.getAndroidLibraries()) {
      addLibrary(androidLibrary, artifact);
    }
    for (Library moduleLibrary : dependencies.getModuleDependencies()) {
      String gradlePath = moduleLibrary.getProjectPath();
      if (gradlePath != null) {
        addModule(gradlePath, artifact, moduleLibrary.getVariant());
      }
    }
    for (Library javaLibrary : dependencies.getJavaLibraries()) {
      addLibrary(javaLibrary, artifact);
    }
  }

  private void addModule(@NotNull String gradlePath, @NotNull PsAndroidArtifact artifact, @Nullable String projectVariant) {
    PsParsedDependencies parsedDependencies = myParent.getParsedDependencies();

    ModuleDependencyModel matchingParsedDependency = parsedDependencies.findModuleDependency(gradlePath, artifact::contains);

    Module resolvedModule = null;
    PsModule module = myParent.getParent().findModuleByGradlePath(gradlePath);
    if (module != null) {
      resolvedModule = module.getResolvedModel();
    }
    PsModuleAndroidDependency dependency = findElement(gradlePath, PsModuleAndroidDependency.class);
    if (dependency == null) {
      dependency = new PsModuleAndroidDependency(myParent, gradlePath, artifact, projectVariant, resolvedModule, matchingParsedDependency);
      myModuleDependenciesByGradlePath.put(gradlePath, dependency);
    }
    updateDependency(dependency, artifact, matchingParsedDependency);
  }

  private void addLibrary(@NotNull Library library, @NotNull PsAndroidArtifact artifact) {
    PsParsedDependencies parsedDependencies = myParent.getParsedDependencies();

    GradleCoordinate coordinates = GradleCoordinate.parseCoordinateString(library.getArtifactAddress());
    //noinspection ConstantConditions
    if (coordinates != null) {
      PsArtifactDependencySpec spec = PsArtifactDependencySpec.create(coordinates);

      ArtifactDependencyModel matchingParsedDependency = parsedDependencies.findLibraryDependency(coordinates, artifact::contains);
      if (matchingParsedDependency != null) {
        String parsedVersionValue = matchingParsedDependency.version().value();
        if (parsedVersionValue != null) {
          // The dependency has a version in the build.gradle file.
          // "tryParse" just in case the build.file has an invalid version.
          GradleVersion parsedVersion = GradleVersion.tryParse(parsedVersionValue);

          GradleVersion versionFromGradle = GradleVersion.parse(coordinates.getRevision());
          if (parsedVersion != null && match(parsedVersion, versionFromGradle)) {
            // Match.
            addLibrary(library, spec, artifact, matchingParsedDependency);
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
            PsLibraryAndroidDependency potentialDuplicate = null;
            for (PsLibraryAndroidDependency dependency : myLibraryDependenciesBySpec.values()) {
              if (dependency.getParsedModels().contains(matchingParsedDependency)) {
                potentialDuplicate = dependency;
                break;
              }
            }
            //noinspection StatementWithEmptyBody
            if (potentialDuplicate != null) {
              // TODO match ArtifactDependencyModel#configurationName with potentialDuplicate.getContainers().artifact
            }

            // Create the dependency model that will be displayed in the "Dependencies" table.
            addLibrary(library, spec, artifact, matchingParsedDependency);

            // Create a dependency model for the transitive dependency, so it can be displayed in the "Variants" tool window.
            addLibrary(library, spec, artifact, null);
          }
        }
      }
      else {
        // This dependency was not declared, it could be a transitive one.
        addLibrary(library, spec, artifact, null);
      }
    }
  }

  private void addLibrary(@NotNull Library library,
                          @NotNull PsArtifactDependencySpec resolvedSpec,
                          @NotNull PsAndroidArtifact artifact,
                          @Nullable ArtifactDependencyModel parsedModel) {
    PsAndroidDependency dependency = getOrCreateDependency(resolvedSpec, library, artifact, parsedModel);
    updateDependency(dependency, artifact, parsedModel);
  }

  @VisibleForTesting
  static Boolean match(@NotNull GradleVersion parsedVersion, @NotNull GradleVersion versionFromGradle) {
    int result = versionFromGradle.compareTo(parsedVersion);
    if (result == 0) {
      return true;
    }
    else if (result < 0) {
      // The "parsed" version might have a '+' sign.
      if (parsedVersion.getMajorSegment().acceptsGreaterValue()) {
        return true;
      }
      else if (parsedVersion.getMinorSegment() != null && parsedVersion.getMinorSegment().acceptsGreaterValue()) {
        return parsedVersion.getMajor() == versionFromGradle.getMajor();
      }
      else if (parsedVersion.getMicroSegment() != null && parsedVersion.getMicroSegment().acceptsGreaterValue()) {
        result = parsedVersion.getMajor() - versionFromGradle.getMajor();
        if (result != 0) {
          return false;
        }
        return parsedVersion.getMinor() == versionFromGradle.getMinor();
      }
    }
    return result == 0;
  }

  @NotNull
  private PsAndroidDependency getOrCreateDependency(@NotNull PsArtifactDependencySpec resolvedSpec,
                                                    @NotNull Library library,
                                                    @NotNull PsAndroidArtifact artifact,
                                                    @Nullable ArtifactDependencyModel parsedModel) {
    String compactNotation = resolvedSpec.toString();
    PsLibraryAndroidDependency dependency = myLibraryDependenciesBySpec.get(compactNotation);
    if (dependency == null) {
      dependency = new PsLibraryAndroidDependency(myParent, resolvedSpec, artifact, library, parsedModel);
      myLibraryDependenciesBySpec.put(compactNotation, dependency);

      File libraryPath = library.getArtifact();
      List<PsArtifactDependencySpec> pomDependencies = findDependenciesInPomFile(libraryPath);
      dependency.setDependenciesFromPomFile(pomDependencies);
    }
    else {
      if (parsedModel != null) {
        dependency.addParsedModel(parsedModel);
      }
    }
    return dependency;
  }

  @Nullable
  PsLibraryAndroidDependency findElement(PsArtifactDependencySpec spec) {
    PsLibraryAndroidDependency dependency = findElement(spec.toString(), PsLibraryAndroidDependency.class);
    if (dependency != null) {
      return dependency;
    }
    if (isEmpty(spec.getVersion())) {
      List<String> found = Lists.newArrayList();
      for (String specText : myLibraryDependenciesBySpec.keySet()) {
        PsArtifactDependencySpec storedSpec = PsArtifactDependencySpec.create(specText);
        if (storedSpec != null && Objects.equals(storedSpec.getGroup(), spec.getGroup()) && Objects.equals(storedSpec.getName(),
                                                                                                           spec.getName())) {
          found.add(specText);
        }
      }

      if (found.size() == 1) {
        // The spec did not have a version, we match with an existing one, only if there is one stored.
        return myLibraryDependenciesBySpec.get(found.get(0));
      }
    }

    return null;
  }

  @Override
  @Nullable
  public <S extends PsAndroidDependency> S findElement(@NotNull String name, @Nullable Class<S> type) {
    if (PsModuleAndroidDependency.class.equals(type)) {
      return type.cast(myModuleDependenciesByGradlePath.get(name));
    }
    if (PsLibraryAndroidDependency.class.equals(type)) {
      return type.cast(myLibraryDependenciesBySpec.get(name));
    }
    return null;
  }

  @Override
  public void forEach(@NotNull Consumer<PsAndroidDependency> consumer) {
    forEachDependency(myLibraryDependenciesBySpec, consumer);
    forEachDependency(myModuleDependenciesByGradlePath, consumer);
  }

  private static void forEachDependency(@NotNull Map<String, ? extends PsAndroidDependency> dependenciesBySpec,
                                        @NotNull Consumer<PsAndroidDependency> consumer) {
    dependenciesBySpec.values().forEach(consumer);
  }

  void forEachDeclaredDependency(@NotNull Consumer<PsAndroidDependency> consumer) {
    forEachDeclaredDependency(myLibraryDependenciesBySpec, consumer);
    forEachDeclaredDependency(myModuleDependenciesByGradlePath, consumer);
  }

  private static void forEachDeclaredDependency(@NotNull Map<String, ? extends PsAndroidDependency> dependenciesBySpec,
                                                @NotNull Consumer<PsAndroidDependency> consumer) {
    dependenciesBySpec.values().stream().filter(PsAndroidDependency::isDeclared).forEach(consumer);
  }

  void forEachModuleDependency(@NotNull Consumer<PsModuleAndroidDependency> consumer) {
    myModuleDependenciesByGradlePath.values().forEach(consumer);
  }

  void addLibraryDependency(@NotNull PsArtifactDependencySpec spec,
                            @NotNull PsAndroidArtifact artifact,
                            @Nullable ArtifactDependencyModel parsedModel) {
    PsLibraryAndroidDependency dependency = myLibraryDependenciesBySpec.get(spec.toString());
    if (dependency == null) {
      dependency = new PsLibraryAndroidDependency(myParent, spec, artifact, null, parsedModel);
      myLibraryDependenciesBySpec.put(spec.toString(), dependency);
    }
    else {
      updateDependency(dependency, artifact, parsedModel);
    }
  }

  void addModuleDependency(@NotNull String modulePath,
                           @NotNull PsAndroidArtifact artifact,
                           @Nullable Module resolvedModel,
                           @Nullable ModuleDependencyModel parsedModel) {
    PsModuleAndroidDependency dependency = myModuleDependenciesByGradlePath.get(modulePath);
    if (dependency == null) {
      dependency = new PsModuleAndroidDependency(myParent, modulePath, artifact, null, resolvedModel, parsedModel);
      myModuleDependenciesByGradlePath.put(modulePath, dependency);
    }
    else {
      updateDependency(dependency, artifact, parsedModel);
    }
  }

  private static void updateDependency(@NotNull PsAndroidDependency dependency,
                                       @NotNull PsAndroidArtifact artifact,
                                       @Nullable DependencyModel parsedModel) {
    if (parsedModel != null) {
      dependency.addParsedModel(parsedModel);
    }
    dependency.addContainer(artifact);
  }
}
