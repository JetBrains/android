/*
 * Copyright 2022 The Bazel Authors. All rights reserved.
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
package com.google.idea.blaze.qsync.project;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Predicate;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.ImmutableSetMultimap;
import com.google.idea.blaze.common.Context;
import com.google.idea.blaze.common.Label;
import com.google.idea.blaze.common.TargetTree;
import com.google.idea.blaze.qsync.query.PackageSet;
import java.nio.file.Path;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import javax.annotation.Nullable;

public interface BuildGraphData {
  @VisibleForTesting BuildGraphData EMPTY = BuildGraphDataImpl.builder().projectDeps(ImmutableSet.of()).build();

  /**
   * A map from target to file on disk for all source files
   */
  ImmutableSet<Label> sourceFileLabels();

  /** A set of all the BUILD files */
  PackageSet packages();

  /**
   * Returns a {@link Label} representing the given path in the workspace with the current build packages. The file does not need to exist.
   */
  @VisibleForTesting
  Optional<Label> pathToLabel(Path file);

  /**
   * If the given path represents a currently known source file returns a {@link Label} representing the given path in the workspace with
   * the current build packages.
   */
  Optional<Label> sourceFileToLabel(Path sourceFile);

  /**
   * All dependencies external to this project.
   *
   * <p>This includes in-project targets that we must build, due to generated sources or being of a
   * {@link com.google.idea.blaze.qsync.BlazeQueryParser#ALWAYS_BUILD_RULE_KINDS always build kind}.
   */
  ImmutableSet<Label> projectDeps();

  /**
   * All supported targets.
   *
   * <p>This is a subset of the keys of {@link #targetMap()} containing only rules that we support
   * enabling analysis for, i.e. that are for a language we support.
   */
  TargetTree allTargets();

  /**
   * Mapping of in-project targets to {@link ProjectTarget}s. This includes all build targets,
   * regardless of if they are supported by querysync.
   */
  ImmutableMap<Label, ProjectTarget> targetMap();

  DepsGraph<Label> depsGraph();

  /**
   * Calculates the set of direct reverse dependencies for a set of targets (including the targets
   * themselves).
   */
  ImmutableSet<Label> getSameLanguageTargetsDependingOn(Set<Label> targets);

  /**
   * Calculates the first targets of a given set of rule types along any given dependency path for a
   * given source.
   */
  Collection<ProjectTarget> getFirstReverseDepsOfType(Path sourcePath, Set<String> ruleKinds);

  /**
   * Returns all in project targets that depend on the source file at {@code sourcePath} via an
   * in-project dependency chain. Used to determine possible test targets for a given file.
   *
   * <p>If project target A depends on external target B, and external target B depends on project
   * target C, target A is *not* included in {@code getReverseDeps} for a source file in target C.
   */
  Collection<ProjectTarget> getReverseDepsForSource(Path sourcePath);

  /**
   * Checks whether a given dependency path contains any of a specified set of rule kinds.
   *
   * <p>All dependency paths are considered starting at any target containing {@param sourcePath}
   * and going to any target containing {@param consumingSourcePath}. If any rule on one of these
   * paths is of a kind contained in {@param ruleKinds}, the method will return true.
   */
  boolean doesDependencyPathContainRules(Path sourcePath, Path consumingSourcePath, Set<String> ruleKinds);

  // TODO: b/397649793 - Remove this method when fixed.
  boolean dependsOnAnyOf_DO_NOT_USE_BROKEN(Label projectTarget, ImmutableSet<Label> deps);

  ImmutableSet<Path> getTargetSources(Label target, ProjectTarget.SourceType... types);

  ImmutableSetMultimap<Label, Label> sourceOwners();

  ImmutableSet<Label> getSourceFileOwners(Path path);

  ImmutableSet<Label> getSourceFileOwners(Label label);

  /**
   * @deprecated Choosing a target based on the number of deps it has is not a good strategy, as we
   *     could end up selecting one that doesn't build in the current config. Allow the user to
   *     choose, or require the projects source -> target mapping to be unambiguous instead.
   */
  @Deprecated
  @Nullable
  Label selectLabelWithLeastDeps(Collection<Label> candidates);

  /** A set of all the targets that show up in java rules 'src' attributes */
  ImmutableSet<Label> javaSources();

  List<Path> getJavaSourceFiles();

  /**
   * Returns a list of all the proto source files of the project, relative to the workspace root.
   */
  List<Path> getProtoSourceFiles();

  /** Returns a list of all the cc source files of the project, relative to the workspace root. */
  List<Path> getCcSourceFiles();

  List<Path> getSourceFilesByRuleKindAndType(
    Predicate<String> ruleKindPredicate, ProjectTarget.SourceType... sourceTypes);

  /**
   * Returns a list of regular (java/kt) source files owned by an Android target, relative to the
   * workspace root.
   */
  List<Path> getAndroidSourceFiles();

  List<Path> getAndroidResourceFiles();

  /** Returns a list of custom_package fields that used by current project. */
  ImmutableSet<String> getAllCustomPackages();

  /**
   * Returns the list of project targets related to the given workspace file.
   *
   * @param context Context
   * @param workspaceRelativePath Workspace relative file path to find targets for. This may be a
   *     source file, directory or BUILD file.
   * @return Corresponding project targets. For a source file, this is the targets that build that
   *     file. For a BUILD file, it's the set or targets defined in that file. For a directory, it's
   *     the set of all targets defined in all build packages within the directory (recursively).
   */
  TargetsToBuild getProjectTargets(Context<?> context, Path workspaceRelativePath);

  /**
   * Returns the set of {@link ProjectTarget#languages() target languages} for a set of project
   * targets.
   */
  ImmutableSet<QuerySyncLanguage> getTargetLanguages(ImmutableSet<Label> targets);

  /**
   * Traverses the dependency graph starting from {@code projectTargets} and returns the first level of dependencies which are either not in
   * the project scope or must be built as they are not directly supported by the IDE.
   */
  Set<Label> getExternalDependencies(Collection<Label> projectTargets);

  /**
   * Calculates the {@link RequestedTargets} for a project target.
   */
  RequestedTargets computeRequestedTargets(Collection<Label> projectTargets);
}
