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
import com.android.ide.common.repository.GradleVersion;
import com.android.tools.idea.gradle.dsl.model.GradleBuildModel;
import com.android.tools.idea.gradle.dsl.model.dependencies.ArtifactDependencyModel;
import com.android.tools.idea.gradle.dsl.model.dependencies.DependencyModel;
import com.android.tools.idea.gradle.dsl.model.dependencies.ModuleDependencyModel;
import com.google.common.base.Joiner;
import com.google.common.collect.*;
import com.intellij.openapi.application.ApplicationManager;
import org.gradle.tooling.model.GradleModuleVersion;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.function.Predicate;
import java.util.stream.Collectors;

import static com.android.SdkConstants.GRADLE_PATH_SEPARATOR;
import static com.google.common.base.Strings.nullToEmpty;
import static com.intellij.openapi.util.text.StringUtil.isNotEmpty;
import static com.intellij.util.containers.ContainerUtil.getFirstItem;

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
      ApplicationManager.getApplication().runReadAction(() -> {
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
      });
    }
  }

  @NotNull
  private static String createIdFrom(@NotNull ArtifactDependencyModel dependency) {
    List<String> segments = Lists.newArrayList(dependency.group().value(), dependency.name().value());
    return joinAsGradlePath(segments);
  }

  @NotNull
  public List<ArtifactDependencyModel> findLibraryDependencies(@NotNull PsArtifactDependencySpec spec,
                                                               @Nullable Predicate<ArtifactDependencyModel> predicate) {
    String id = createIdFrom(spec);
    Collection<ArtifactDependencyModel> potentialMatches = myParsedArtifactDependencies.get(id);
    if (predicate != null) {
      return potentialMatches.stream().filter(predicate).collect(Collectors.toList());
    }
    return ImmutableList.copyOf(potentialMatches);
  }

  @NotNull
  private static String createIdFrom(@NotNull PsArtifactDependencySpec spec) {
    List<String> segments = Lists.newArrayList(spec.group, spec.name);
    return joinAsGradlePath(segments);
  }

  @Nullable
  public ArtifactDependencyModel findLibraryDependency(@NotNull MavenCoordinates coordinates,
                                                       @NotNull Predicate<ArtifactDependencyModel> predicate) {
    Collection<ArtifactDependencyModel> potentialMatches = myParsedArtifactDependencies.get(createIdFrom(coordinates));
    for (ArtifactDependencyModel dependency : potentialMatches) {
      if (predicate.test(dependency)) {
        return dependency;
      }
    }
    return null;
  }

  @NotNull
  private static String createIdFrom(@NotNull MavenCoordinates coordinates) {
    List<String> segments = Lists.newArrayList(coordinates.getGroupId(), coordinates.getArtifactId());
    return joinAsGradlePath(segments);
  }

  @Nullable
  public ArtifactDependencyModel findLibraryDependency(@NotNull GradleModuleVersion moduleVersion) {
    Collection<ArtifactDependencyModel> potentialMatches = myParsedArtifactDependencies.get(createIdFrom(moduleVersion));
    if (potentialMatches.size() == 1) {
      // Only one found. Just use it.
      return getFirstItem(potentialMatches);
    }

    String version = nullToEmpty(moduleVersion.getVersion());

    Map<GradleVersion, ArtifactDependencyModel> dependenciesByVersion = Maps.newHashMap();
    for (ArtifactDependencyModel potentialMatch : potentialMatches) {
      String potentialVersion = nullToEmpty(potentialMatch.version().value());
      if (version.equals(potentialVersion)) {
        // Perfect version match. Use it.
        return potentialMatch;
      }
      if (isNotEmpty(potentialVersion)) {
        // Collect all the "parsed" dependencies with same group and name, to make a best guess later.
        GradleVersion parsedVersion = GradleVersion.tryParse(potentialVersion);
        if (parsedVersion != null) {
          dependenciesByVersion.put(parsedVersion, potentialMatch);
        }
      }
    }

    if (isNotEmpty(version) && !dependenciesByVersion.isEmpty()) {
      GradleVersion parsedVersion = GradleVersion.tryParse(version);
      if (parsedVersion != null) {
        for (GradleVersion potentialVersion : dependenciesByVersion.keySet()) {
          if (parsedVersion.compareTo(potentialVersion) > 0) {
            return dependenciesByVersion.get(potentialVersion);
          }
        }
      }
    }

    return null;
  }

  @NotNull
  private static String createIdFrom(@NotNull GradleModuleVersion moduleVersion) {
    List<String> segments = Lists.newArrayList(moduleVersion.getGroup(), moduleVersion.getName());
    return joinAsGradlePath(segments);
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
