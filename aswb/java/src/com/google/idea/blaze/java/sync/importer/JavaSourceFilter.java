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
package com.google.idea.blaze.java.sync.importer;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.google.idea.blaze.base.ideinfo.ArtifactLocation;
import com.google.idea.blaze.base.ideinfo.LibraryArtifact;
import com.google.idea.blaze.base.ideinfo.TargetIdeInfo;
import com.google.idea.blaze.base.ideinfo.TargetKey;
import com.google.idea.blaze.base.ideinfo.TargetMap;
import com.google.idea.blaze.base.model.primitives.GenericBlazeRules;
import com.google.idea.blaze.base.model.primitives.WorkspaceRoot;
import com.google.idea.blaze.base.projectview.ProjectViewSet;
import com.google.idea.blaze.base.settings.BuildSystemName;
import com.google.idea.blaze.base.sync.projectview.ProjectViewTargetImportFilter;
import com.google.idea.blaze.java.JavaBlazeRules;
import com.google.idea.blaze.java.sync.source.JavaLikeLanguage;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import javax.annotation.Nullable;

/** Segments java rules into source/libraries */
public class JavaSourceFilter {
  final List<TargetIdeInfo> sourceTargets;
  final List<TargetIdeInfo> libraryTargets;
  final Map<TargetKey, Collection<ArtifactLocation>> targetToJavaSources;
  /** The set of workspace-relative paths for excluded library artifacts. */
  final Set<String> jdepsPathsForExcludedJars = new HashSet<>();

  public JavaSourceFilter(
      BuildSystemName buildSystemName,
      WorkspaceRoot workspaceRoot,
      ProjectViewSet projectViewSet,
      TargetMap targetMap) {
    ProjectViewTargetImportFilter importFilter =
        new ProjectViewTargetImportFilter(buildSystemName, workspaceRoot, projectViewSet);

    List<TargetIdeInfo> excludedTargets =
        targetMap
            .targets()
            .stream()
            .filter(importFilter::excludeTarget)
            .collect(Collectors.toList());
    excludedTargets.forEach(t -> jdepsPathsForExcludedJars.addAll(relativeArtifactPaths(t)));

    List<TargetIdeInfo> includedTargets = new ArrayList<>(targetMap.targets());
    includedTargets.removeAll(excludedTargets);

    List<TargetIdeInfo> javaTargets =
        includedTargets.stream()
            .filter(target -> target.getJavaIdeInfo() != null)
            .collect(Collectors.toList());

    targetToJavaSources = Maps.newHashMap();
    Predicate<ArtifactLocation> isSourceFile = JavaLikeLanguage.getSourceFileMatcher();
    for (TargetIdeInfo target : javaTargets) {
      List<ArtifactLocation> javaLikeSources =
          target.getSources().stream().filter(isSourceFile).collect(Collectors.toList());
      targetToJavaSources.put(target.getKey(), javaLikeSources);
    }

    sourceTargets = Lists.newArrayList();
    libraryTargets = Lists.newArrayList();
    for (TargetIdeInfo target : javaTargets) {
      if (importAsSource(importFilter, target, targetToJavaSources.get(target.getKey()))) {
        sourceTargets.add(target);
        jdepsPathsForExcludedJars.addAll(relativeArtifactPaths(target));
      } else {
        libraryTargets.add(target);
      }
    }
  }

  public Collection<TargetIdeInfo> getSourceTargets() {
    return sourceTargets;
  }

  public Iterable<TargetIdeInfo> getLibraryTargets() {
    return libraryTargets;
  }

  /** Whether the given target should be treated as a source or library target. */
  public static boolean importAsSource(
      ProjectViewTargetImportFilter importFilter, TargetIdeInfo target) {
    Collection<ArtifactLocation> javaSources =
        target.getSources().stream()
            .filter(JavaLikeLanguage.getSourceFileMatcher())
            .collect(Collectors.toList());
    return importAsSource(importFilter, target, javaSources);
  }

  private static boolean importAsSource(
      ProjectViewTargetImportFilter importFilter,
      TargetIdeInfo target,
      Collection<ArtifactLocation> javaLikeSources) {
    if (!importFilter.isSourceTarget(target)) {
      return false;
    }
    return isJavaSourceTarget(target, javaLikeSources) || isJavaProtoTarget(target);
  }

  private static boolean isJavaSourceTarget(
      TargetIdeInfo target, Collection<ArtifactLocation> javaLikeSources) {
    return JavaLikeLanguage.canImportAsSource(target) && anyNonGeneratedSources(javaLikeSources);
  }

  static boolean isJavaProtoTarget(TargetIdeInfo target) {
    return target.getJavaIdeInfo() != null
        && (JavaBlazeRules.getJavaProtoLibraryKinds().contains(target.getKind())
            || target.getKind().equals(GenericBlazeRules.RuleTypes.PROTO_LIBRARY.getKind()));
  }

  private static boolean anyNonGeneratedSources(Collection<ArtifactLocation> sources) {
    return sources.stream().anyMatch(ArtifactLocation::isSource);
  }

  private List<String> relativeArtifactPaths(TargetIdeInfo target) {
    if (target.getJavaIdeInfo() == null) {
      return ImmutableList.of();
    }
    return target.getJavaIdeInfo().getJars().stream()
        .flatMap(j -> relativeArtifactPaths(j).stream())
        .collect(Collectors.toList());
  }

  private List<String> relativeArtifactPaths(LibraryArtifact jar) {
    List<String> list = new ArrayList<>();
    addRelativePath(list, jar.getClassJar());
    addRelativePath(list, jar.getInterfaceJar());
    jar.getSourceJars().forEach(a -> addRelativePath(list, a));
    return list;
  }

  private void addRelativePath(List<String> paths, @Nullable ArtifactLocation artifact) {
    if (artifact != null) {
      paths.add(artifact.getRelativePath());
    }
  }
}
