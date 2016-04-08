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
package com.android.tools.idea.gradle.structure.model;


import com.android.builder.model.MavenCoordinates;
import com.android.tools.idea.gradle.dsl.model.GradleBuildModel;
import com.android.tools.idea.gradle.dsl.model.dependencies.ArtifactDependencyModel;
import com.android.tools.idea.gradle.dsl.model.dependencies.DependencyModel;
import com.android.tools.idea.gradle.dsl.model.dependencies.ModuleDependencyModel;
import com.android.tools.idea.gradle.structure.model.android.PsAndroidArtifact;
import com.google.common.base.Joiner;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Map;

import static com.android.SdkConstants.GRADLE_PATH_SEPARATOR;

public class PsParsedDependencies {
  // Key: module's Gradle path
  @NotNull private final Map<String, ModuleDependencyModel> myParsedModuleDependencies = Maps.newHashMap();

  // Key: artifact group ID + ":" + artifact name (e.g. "com.google.guava:guava")
  @NotNull private final Map<String, ArtifactDependencyModel> myParsedArtifactDependencies = Maps.newHashMap();

  public PsParsedDependencies(@Nullable GradleBuildModel parsedModel) {
    if (parsedModel != null) {
      for (DependencyModel parsedDependency : parsedModel.dependencies().all()) {
        if (parsedDependency instanceof ArtifactDependencyModel) {
          ArtifactDependencyModel artifact = (ArtifactDependencyModel)parsedDependency;
          myParsedArtifactDependencies.put(getIdentifier(artifact), artifact);
        }
        else if (parsedDependency instanceof ModuleDependencyModel) {
          ModuleDependencyModel module = (ModuleDependencyModel)parsedDependency;
          myParsedModuleDependencies.put(module.path(), module);
        }
      }
    }
  }

  @NotNull
  private static String getIdentifier(@NotNull ArtifactDependencyModel dependency) {
    List<String> segments = Lists.newArrayList(dependency.group().value(), dependency.name().value());
    return joinAsGradlePath(segments);
  }

  @Nullable
  public ArtifactDependencyModel findMatchingArtifactDependency(@NotNull MavenCoordinates coordinates,
                                                                @NotNull PsAndroidArtifact artifact) {
    String identifier = getIdentifier(coordinates);
    ArtifactDependencyModel parsedDependency = myParsedArtifactDependencies.get(identifier);
    if (parsedDependency != null && isDependencyInArtifact(parsedDependency, artifact)) {
      return parsedDependency;
    }
    return null;
  }

  @NotNull
  private static String getIdentifier(@NotNull MavenCoordinates coordinates) {
    List<String> segments = Lists.newArrayList(coordinates.getGroupId(), coordinates.getArtifactId());
    return joinAsGradlePath(segments);
  }

  @NotNull
  private static String joinAsGradlePath(@NotNull List<String> segments) {
    return Joiner.on(GRADLE_PATH_SEPARATOR).skipNulls().join(segments);
  }

  @Nullable
  public ModuleDependencyModel findMatchingModuleDependency(@NotNull String gradlePath, @NotNull PsAndroidArtifact artifact) {
    ModuleDependencyModel parsedDependency = myParsedModuleDependencies.get(gradlePath);
    if (parsedDependency != null && isDependencyInArtifact(parsedDependency, artifact)) {
      return parsedDependency;
    }
    return null;
  }

  public static boolean isDependencyInArtifact(@NotNull DependencyModel parsedDependency, @NotNull PsAndroidArtifact artifact) {
    String configurationName = parsedDependency.configurationName();
    return artifact.getPossibleConfigurationNames().contains(configurationName);
  }
}
