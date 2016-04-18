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
import com.google.common.base.Joiner;
import com.google.common.collect.ArrayListMultimap;
import com.google.common.collect.Lists;
import com.google.common.collect.Multimap;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;
import java.util.List;
import java.util.function.Predicate;

import static com.android.SdkConstants.GRADLE_PATH_SEPARATOR;

public class PsParsedDependencies {
  // Key: module's Gradle path
  @NotNull private final Multimap<String, ModuleDependencyModel> myParsedModuleDependencies = ArrayListMultimap.create();

  // Key: artifact group ID + ":" + artifact name (e.g. "com.google.guava:guava")
  @NotNull private final Multimap<String, ArtifactDependencyModel> myParsedArtifactDependencies = ArrayListMultimap.create();

  public PsParsedDependencies(@Nullable GradleBuildModel parsedModel) {
    reset(parsedModel);
  }

  void reset(@Nullable GradleBuildModel parsedModel) {
    myParsedArtifactDependencies.clear();
    myParsedModuleDependencies.clear();
    if (parsedModel != null) {
      for (DependencyModel parsedDependency : parsedModel.dependencies().all()) {
        if (parsedDependency instanceof ArtifactDependencyModel) {
          ArtifactDependencyModel artifact = (ArtifactDependencyModel)parsedDependency;
          myParsedArtifactDependencies.put(createIdFrom(artifact), artifact);
        }
        else if (parsedDependency instanceof ModuleDependencyModel) {
          ModuleDependencyModel module = (ModuleDependencyModel)parsedDependency;
          myParsedModuleDependencies.put(module.path(), module);
        }
      }
    }
  }

  @NotNull
  private static String createIdFrom(@NotNull ArtifactDependencyModel dependency) {
    List<String> segments = Lists.newArrayList(dependency.group().value(), dependency.name().value());
    return joinAsGradlePath(segments);
  }

  @Nullable
  public ArtifactDependencyModel findLibraryDependency(@NotNull PsArtifactDependencySpec spec,
                                                       @NotNull Predicate<ArtifactDependencyModel> predicate) {
    return findLibraryDependency(createIdFrom(spec), predicate);
  }

  @NotNull
  private static String createIdFrom(@NotNull PsArtifactDependencySpec spec) {
    List<String> segments = Lists.newArrayList(spec.group, spec.name);
    return joinAsGradlePath(segments);
  }

  @Nullable
  public ArtifactDependencyModel findLibraryDependency(@NotNull MavenCoordinates coordinates,
                                                       @NotNull Predicate<ArtifactDependencyModel> predicate) {
    return findLibraryDependency(createIdFrom(coordinates), predicate);
  }

  @NotNull
  private static String createIdFrom(@NotNull MavenCoordinates coordinates) {
    List<String> segments = Lists.newArrayList(coordinates.getGroupId(), coordinates.getArtifactId());
    return joinAsGradlePath(segments);
  }

  @Nullable
  private ArtifactDependencyModel findLibraryDependency(@NotNull String id, @NotNull Predicate<ArtifactDependencyModel> predicate) {
    Collection<ArtifactDependencyModel> potentialMatches = myParsedArtifactDependencies.get(id);
    for (ArtifactDependencyModel dependency : potentialMatches) {
      if (predicate.test(dependency)) {
        return dependency;
      }
    }
    return null;
  }

  @NotNull
  private static String joinAsGradlePath(@NotNull List<String> segments) {
    return Joiner.on(GRADLE_PATH_SEPARATOR).skipNulls().join(segments);
  }

  @Nullable
  public ModuleDependencyModel findModuleDependency(@NotNull String gradlePath, @NotNull Predicate<ModuleDependencyModel> predicate) {
    Collection<ModuleDependencyModel> potentialMatches = myParsedModuleDependencies.get(gradlePath);
    for (ModuleDependencyModel dependency : potentialMatches) {
      if (predicate.test(dependency)) {
        return dependency;
      }
    }
    return null;
  }
}
