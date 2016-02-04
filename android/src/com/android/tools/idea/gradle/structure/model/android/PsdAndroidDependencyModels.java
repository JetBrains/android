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
import com.android.tools.idea.gradle.dsl.model.dependencies.ArtifactDependencyModel;
import com.android.tools.idea.gradle.dsl.model.dependencies.ArtifactDependencySpec;
import com.android.tools.idea.gradle.structure.model.PsdParsedDependencyModels;
import com.google.common.annotations.VisibleForTesting;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Map;

import static com.android.tools.idea.gradle.structure.model.android.Artifacts.createSpec;

class PsdAndroidDependencyModels {
  @NotNull private final PsdAndroidModuleModel myParent;

  // Key:
  // - For artifact dependencies: artifact spec, "com.google.guava:guava:19.0"
  // - For module dependencies: module's Gradle path
  @NotNull private final Map<String, PsdAndroidDependencyModel> myDependencyModels = Maps.newHashMap();

  PsdAndroidDependencyModels(@NotNull PsdAndroidModuleModel parent) {
    myParent = parent;
    for (PsdVariantModel variantModel : parent.getVariantModels()) {
      addDependencies(variantModel);
    }
  }

  private void addDependencies(@NotNull PsdVariantModel variantModel) {
    Variant variant = variantModel.getGradleModel();
    if (variant != null) {
      AndroidArtifact mainArtifact = variant.getMainArtifact();
      Dependencies dependencies = mainArtifact.getDependencies();

      for (AndroidLibrary androidLibrary : dependencies.getLibraries()) {
        if (androidLibrary.getProject() != null) {
          // This is a module
          // TODO add module dependency
        }
        else {
          // This is an AAR
          addAndroidLibrary(androidLibrary, variantModel);
        }
      }
    }
  }

  @Nullable
  private PsdAndroidDependencyModel addAndroidLibrary(@NotNull AndroidLibrary androidLibrary, @NotNull PsdVariantModel variantModel) {
    PsdParsedDependencyModels parsedDependencies = myParent.getParsedDependencyModels();

    MavenCoordinates coordinates = androidLibrary.getResolvedCoordinates();
    if (coordinates != null) {
      ArtifactDependencyModel matchingParsedDependency = parsedDependencies.findMatchingParsedDependency(coordinates);
      if (matchingParsedDependency != null) {
        String parsedVersionValue = matchingParsedDependency.version();
        if (parsedVersionValue != null) {
          // The dependency has a version in the build.gradle file.
          // "tryParse" just in case the build.file has an invalid version.
          GradleVersion parsedVersion = GradleVersion.tryParse(parsedVersionValue);

          GradleVersion versionFromGradle = GradleVersion.parse(coordinates.getVersion());
          if (parsedVersion != null && compare(parsedVersion, versionFromGradle) == 0) {
            // Match.
            ArtifactDependencySpec spec = matchingParsedDependency.getSpec();
            return addAndroidLibrary(androidLibrary, spec, variantModel, matchingParsedDependency);
          }
          else {
            // TODO: handle a mismatch
          }
        }
      }
      else {
        // This dependency was not declared, it could be a transitive one.
        ArtifactDependencySpec spec = createSpec(coordinates);
        return addAndroidLibrary(androidLibrary, spec, variantModel, null);
      }
    }
    return null;
  }

  @NotNull
  private PsdAndroidDependencyModel addAndroidLibrary(@NotNull AndroidLibrary androidLibrary,
                                                      @NotNull ArtifactDependencySpec spec,
                                                      @NotNull PsdVariantModel variantModel,
                                                      @Nullable ArtifactDependencyModel parsedDependencyModel) {
    PsdAndroidDependencyModel dependencyModel = getOrCreateDependency(spec, androidLibrary, parsedDependencyModel);

    for (AndroidLibrary library : androidLibrary.getLibraryDependencies()) {
      PsdAndroidDependencyModel transitive = addAndroidLibrary(library, variantModel);
      if (transitive != null && dependencyModel instanceof PsdAndroidLibraryDependencyModel) {
        PsdAndroidLibraryDependencyModel libraryDependencyModel = (PsdAndroidLibraryDependencyModel)dependencyModel;
        libraryDependencyModel.addTransitiveDependency(transitive.getValueAsText());
      }
    }

    dependencyModel.addContainer(variantModel);
    return dependencyModel;
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
  private PsdAndroidDependencyModel getOrCreateDependency(@NotNull ArtifactDependencySpec spec,
                                                          @NotNull AndroidLibrary gradleModel,
                                                          @Nullable ArtifactDependencyModel parsedModel) {
    String key = spec.toString();
    PsdAndroidDependencyModel dependencyModel = findDependency(key);
    if (dependencyModel == null) {
      dependencyModel = new PsdAndroidLibraryDependencyModel(myParent, spec, gradleModel, parsedModel);
      myDependencyModels.put(key, dependencyModel);
    }
    return dependencyModel;
  }

  @NotNull
  List<PsdAndroidDependencyModel> getDeclaredDependencies() {
    List<PsdAndroidDependencyModel> models = Lists.newArrayList();
    for (PsdAndroidDependencyModel model : myDependencyModels.values()) {
      if (model.isEditable()) {
        models.add(model);
      }
    }
    return models;
  }

  @NotNull
  public List<PsdAndroidDependencyModel> getDependencies() {
    return Lists.newArrayList(myDependencyModels.values());
  }

  @Nullable
  public PsdAndroidDependencyModel findDependency(@NotNull String dependency) {
    return myDependencyModels.get(dependency);
  }
}
