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

import static com.google.common.collect.ImmutableList.toImmutableList;
import static com.google.common.collect.ImmutableSet.toImmutableSet;
import static com.google.common.collect.ImmutableSetMultimap.toImmutableSetMultimap;
import static java.util.Arrays.stream;

import com.google.auto.value.AutoValue;
import com.google.auto.value.extension.memoized.Memoized;
import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Preconditions;
import com.google.common.base.Predicate;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.ImmutableSetMultimap;
import com.google.common.collect.Queues;
import com.google.common.collect.Sets;
import com.google.common.collect.Streams;
import com.google.common.graph.Graph;
import com.google.common.graph.Graphs;
import com.google.common.graph.Traverser;
import com.google.idea.blaze.common.Context;
import com.google.idea.blaze.common.Label;
import com.google.idea.blaze.common.PrintOutput;
import com.google.idea.blaze.common.RuleKinds;
import com.google.idea.blaze.common.TargetTree;
import com.google.idea.blaze.qsync.project.ProjectTarget.SourceType;
import com.google.idea.blaze.qsync.query.PackageSet;
import java.nio.file.Path;
import java.util.AbstractMap.SimpleEntry;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Queue;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import javax.annotation.Nullable;

/**
 * The build graph of all the rules that make up the project.
 *
 * <p>This class is immutable. A new instance of it will be created every time there is any change
 * to the project structure.
 */
@AutoValue
public abstract class BuildGraphData {
  /** A map from target to file on disk for all source files */
  public abstract ImmutableSet<Label> sourceFileLabels();

  /** A set of all the BUILD files */
  @Memoized
  public PackageSet packages() {
    PackageSet.Builder packages = new PackageSet.Builder();
    for (Label sourceFile : sourceFileLabels()) {
      if (sourceFile.getName().equals(Path.of("BUILD")) || sourceFile.getName().equals(Path.of("BUILD.bazel"))) {
        // TODO: b/334110669 - support Bazel workspaces.
        packages.add(sourceFile.getPackage());
      }
    }
    return packages.build();
  }

  /**
   * Returns a {@link Label} representing the given path in the workspace with the current build packages. The file does not need to exist.
   */
  @VisibleForTesting
  public Optional<Label> pathToLabel(Path file) {
    var path = file;
    do {
      path = path.getParent();
      final var probe = path != null ? path : Path.of("");
      final var probeNameCount = path != null ? path.getNameCount() : 0;
      if (packages().contains(probe)) {
        return Optional.of(Label.of("//" + probe.toString() + ":" + file.subpath(probeNameCount, file.getNameCount()).toString()));
      }
    } while (path != null);
    return Optional.empty();
  }

  /**
   * If the given path represents a currently known source file returns a {@link Label} representing the given path in the workspace with
   * the current build packages.
   */
  public Optional<Label> sourceFileToLabel(Path sourceFile) {
    final var sourceFileLabel = pathToLabel(sourceFile);
    if (sourceFileLabel.isEmpty()) {
      return Optional.empty();
    }
    if (sourceFileLabels().contains(sourceFileLabel.get())) {
      return sourceFileLabel;
    }
    return Optional.empty();
  }

  /**
   * All dependencies external to this project.
   *
   * <p>This includes in-project targets that we must build, due to generated sources or being of a
   * {@link com.google.idea.blaze.qsync.BlazeQueryParser#ALWAYS_BUILD_RULE_KINDS always build kind}.
   */
  public abstract ImmutableSet<Label> projectDeps();

  /**
   * All supported targets.
   *
   * <p>This is a subset of the keys of {@link #targetMap()} containing only rules that we support
   * enabling analysis for, i.e. that are for a language we support.
   */
  public abstract TargetTree allTargets();

  /**
   * Mapping of in-project targets to {@link ProjectTarget}s. This includes all build targets,
   * regardless of if they are supported by querysync.
   */
  public abstract ImmutableMap<Label, ProjectTarget> targetMap();

  /**
   * An immutable directed graph of all project dependencies.
   *
   * <p>This graph include both in-project targets, and direct out-of-project dependencies.
   *
   * <p>To find the reverse dependencies of a target, you can use {@link Graph#predecessors} or
   * {@link Graphs#transpose(Graph)} with this method.
   */
  @Memoized
  public DepsGraph<Label> depsGraph() {
    DepsGraph.Builder<Label> builder = new DepsGraph.Builder<>();
    targetMap().values().stream().forEach(target -> builder.add(target.label(), target.deps()));
    return builder.build();
  }

  @Memoized
  public ExternalTransitiveClosure<Label> transitiveExternalDeps() {
    return new ExternalTransitiveClosure(depsGraph(), projectDeps());
  }

  /**
   * Calculates the set of direct reverse dependencies for a set of targets (including the targets
   * themselves).
   */
  public ImmutableSet<Label> getSameLanguageTargetsDependingOn(Set<Label> targets) {
    ImmutableSet.Builder<Label> directRdeps = ImmutableSet.builder();
    directRdeps.addAll(targets);
    for (Label target : targets) {
      ImmutableSet<QuerySyncLanguage> targetLanguages = targetMap().get(target).languages();
      // filter the rdeps based on the languages, removing those that don't have a common
      // language. This ensures we don't follow reverse deps of (e.g.) a java target depending on
      // a cc target.
      depsGraph().rdeps(target).stream()
          .filter(d -> !Collections.disjoint(targetMap().get(d).languages(), targetLanguages))
          .forEach(directRdeps::add);
    }
    return directRdeps.build();
  }

  /**
   * Calculates the first targets of a given set of rule types along any given dependency path for a
   * given source.
   */
  public Collection<ProjectTarget> getFirstReverseDepsOfType(
      Path sourcePath, Set<String> ruleKinds) {
    ImmutableSet<Label> targetOwners = getSourceFileOwners(sourcePath);

    if (targetOwners == null || targetOwners.isEmpty()) {
      return ImmutableList.of();
    }

    List<ProjectTarget> result = new ArrayList<>();

    Queue<Label> toVisit = Queues.newArrayDeque(targetOwners);
    Set<Label> visited = Sets.newHashSet();

    while (!toVisit.isEmpty()) {
      Label next = toVisit.remove();
      if (visited.add(next)) {
        ProjectTarget target = targetMap().get(next);
        if (target != null && ruleKinds.contains(target.kind())) {
          result.add(target);
        } else {
          toVisit.addAll(depsGraph().rdeps(next));
        }
      }
    }

    return result;
  }

  /**
   * Returns all in project targets that depend on the source file at {@code sourcePath} via an
   * in-project dependency chain. Used to determine possible test targets for a given file.
   *
   * <p>If project target A depends on external target B, and external target B depends on project
   * target C, target A is *not* included in {@code getReverseDeps} for a source file in target C.
   */
  public Collection<ProjectTarget> getReverseDepsForSource(Path sourcePath) {

    ImmutableSet<Label> targetOwners = getSourceFileOwners(sourcePath);

    if (targetOwners == null || targetOwners.isEmpty()) {
      return ImmutableList.of();
    }

    return Streams.stream(Traverser.forGraph(depsGraph()::rdeps).breadthFirst(targetOwners))
        .map(label -> targetMap().get(label))
        .filter(Objects::nonNull)
        .collect(toImmutableSet());
  }

  /**
   * Checks whether a given dependency path contains any of a specified set of rule kinds.
   *
   * <p>All dependency paths are considered starting at any target containing {@param sourcePath}
   * and going to any target containing {@param consumingSourcePath}. If any rule on one of these
   * paths is of a kind contained in {@param ruleKinds}, the method will return true.
   */
  public boolean doesDependencyPathContainRules(
      Path sourcePath, Path consumingSourcePath, Set<String> ruleKinds) {
    ImmutableSet<Label> sourceTargets = getSourceFileOwners(sourcePath);
    if (sourceTargets == null || sourceTargets.isEmpty()) {
      return false;
    }

    ImmutableSet<Label> consumingTargetLabels = getSourceFileOwners(consumingSourcePath);
    if (consumingTargetLabels == null || consumingTargetLabels.isEmpty()) {
      return false;
    }

    ImmutableMap<Label, ProjectTarget> targetMap = targetMap();

    // Do a BFS up the dependency graph, looking both at the labels and the set of rule kinds
    // we've found so far at any given point.
    Queue<TargetSearchNode> toVisit =
        Queues.newArrayDeque(
            sourceTargets.stream()
                .map((sourceTarget) -> new TargetSearchNode(sourceTarget, false))
                .collect(Collectors.toList()));
    Set<TargetSearchNode> visited = Sets.newHashSet();

    while (!toVisit.isEmpty()) {
      TargetSearchNode current = toVisit.remove();
      if (visited.add(current)) {
        Label currentLabel = current.targetLabel;
        String currentLabelKind = targetMap.get(currentLabel).kind();

        boolean hasDesiredRule = current.hasDesiredRule || ruleKinds.contains(currentLabelKind);

        if (hasDesiredRule && consumingTargetLabels.contains(currentLabel)) {
          // We've found one of the consuming targets and the path here contained one of
          // the desired rule types, so we can terminate.
          return true;
        } else {
          // Continue searching. Even if this is one of the consuming target labels, it's
          // possible that further up the dependency graph we'll run into a different one
          // of the consuming targets - and potentially have found one of the rules we
          // need along the way.
          for (Label nextTargetLabel : depsGraph().rdeps(currentLabel)) {
            toVisit.add(new TargetSearchNode(nextTargetLabel, hasDesiredRule));
          }
        }
      }
    }

    // We never found any of the desired rules.
    return false;
  }

  // TODO: b/397649793 - Remove this method when fixed.
  public boolean dependsOnAnyOf_DO_NOT_USE_BROKEN(Label projectTarget, ImmutableSet<Label> deps) {
    ImmutableList<Label> projectTargetSingleton = ImmutableList.of(projectTarget);
    final var queue = new ArrayDeque<Label>(projectTargetSingleton);
    final var seen = new HashSet<>(projectTargetSingleton);
    while (!queue.isEmpty()) {
      final var target = queue.remove();
      if (deps.contains(target)) {
        return true;
      }
      final var targetInfo = targetMap().get(target);
      if (targetInfo == null) {
        continue;
      }
      queue.addAll(targetInfo.deps().stream().filter(seen::add).toList());
    }
    return false;
  }

  private record TargetSearchNode(Label targetLabel, boolean hasDesiredRule) {}

  public ImmutableSet<Path> getTargetSources(Label target, SourceType... types) {
    return Optional.ofNullable(targetMap().get(target)).stream()
        .map(ProjectTarget::sourceLabels)
        .flatMap(m -> stream(types).map(m::get))
        .flatMap(Set::stream)
        .filter(sourceFileLabels()::contains) // filter out generated sources
        .map(Label::toFilePath)
        .collect(toImmutableSet());
  }

  public ImmutableSet<ProjectTarget> targetsForKind(String kind) {
    return targetMap().values().stream()
        .filter(t -> t.kind().equals(kind))
        .collect(toImmutableSet());
  }

  @Override
  public final String toString() {
    // The default autovalue toString() implementation can result in a very large string which
    // chokes the debugger.
    return getClass().getSimpleName() + "@" + Integer.toHexString(System.identityHashCode(this));
  }

  public static Builder builder() {
    return new AutoValue_BuildGraphData.Builder();
  }

  @VisibleForTesting
  public static final BuildGraphData EMPTY = builder().projectDeps(ImmutableSet.of()).build();

  /** Builder for {@link BuildGraphData}. */
  @AutoValue.Builder
  public abstract static class Builder {

    public abstract ImmutableSet.Builder<Label> sourceFileLabelsBuilder();

    public abstract ImmutableMap.Builder<Label, ProjectTarget> targetMapBuilder();

    public abstract Builder projectDeps(Set<Label> value);

    public abstract TargetTree.Builder allTargetsBuilder();

    abstract BuildGraphData autoBuild();

    public final BuildGraphData build() {
      BuildGraphData result = autoBuild();
      // these are memoized, but we choose to pay the cost of building it now so that it's done at
      // sync time rather than later on.
      ImmutableSetMultimap<Label, Label> unused = result.sourceOwners();
      return result;
    }
  }

  /** Represents a location on a file. */
  public static class Location {

    private static final Pattern PATTERN = Pattern.compile("(.*):(\\d+):(\\d+)");

    public final Path file; // Relative to workspace root
    public final int row;
    public final int column;

    /**
     * @param location A location as provided by bazel, i.e. {@code path/to/file:lineno:columnno}
     */
    public Location(String location) {
      Matcher matcher = PATTERN.matcher(location);
      Preconditions.checkArgument(matcher.matches(), "Location not recognized: %s", location);
      file = Path.of(matcher.group(1));
      Preconditions.checkState(
          !file.startsWith("/"),
          "Filename starts with /: ensure that "
              + "`--relative_locations=true` was specified in the query invocation.");
      row = Integer.parseInt(matcher.group(2));
      column = Integer.parseInt(matcher.group(3));
    }
  }

  @Memoized
  public ImmutableSetMultimap<Label, Label> sourceOwners() {
    return targetMap().values().stream()
        .flatMap(
            t -> t.sourceLabels().values().stream().map(src -> new SimpleEntry<>(src, t.label())))
        .collect(toImmutableSetMultimap(e -> e.getKey(), e -> e.getValue()));
  }

  public ImmutableSet<Label> getTransitiveExternalDependencies(Label target) {
    return transitiveExternalDeps().get(target);
  }

  public ImmutableSet<Label> getSourceFileOwners(Path path) {
    return sourceFileToLabel(path).map(this::getSourceFileOwners).orElse(ImmutableSet.of());
  }

  public ImmutableSet<Label> getSourceFileOwners(Label label) {
    return sourceOwners().get(label);
  }

  /**
   * @deprecated Choosing a target based on the number of deps it has is not a good strategy, as we
   *     could end up selecting one that doesn't build in the current config. Allow the user to
   *     choose, or require the projects source -> target mapping to be unambiguous instead.
   */
  @Deprecated
  @Nullable
  public Label selectLabelWithLeastDeps(Collection<Label> candidates) {
    return candidates.stream()
        .min(Comparator.comparingInt(label -> targetMap().get(label).deps().size()))
        .orElse(null);
  }

  @VisibleForTesting
  @Nullable
  Set<Label> getFileDependencies(Path path) {
    ImmutableSet<Label> targets = getSourceFileOwners(path);
    if (targets == null) {
      return null;
    }
    return targets.stream()
        .map(this::getTransitiveExternalDependencies)
        .flatMap(Set::stream)
        .collect(toImmutableSet());
  }

  /** A set of all the targets that show up in java rules 'src' attributes */
  @Memoized
  public ImmutableSet<Label> javaSources() {
    return sourcesByRuleKindAndType(RuleKinds::isJava, SourceType.REGULAR);
  }

  /** Returns a list of all the java source files of the project, relative to the workspace root. */
  public List<Path> getJavaSourceFiles() {
    return pathListFromSourceFileLabelsOnly(javaSources());
  }

  /**
   * Returns a list of all the proto source files of the project, relative to the workspace root.
   */
  @Memoized
  public List<Path> getProtoSourceFiles() {
    return getSourceFilesByRuleKindAndType(RuleKinds::isProtoSource, SourceType.REGULAR);
  }

  /** Returns a list of all the cc source files of the project, relative to the workspace root. */
  @Memoized
  public List<Path> getCcSourceFiles() {
    return getSourceFilesByRuleKindAndType(RuleKinds::isCc, SourceType.REGULAR);
  }

  public List<Path> getSourceFilesByRuleKindAndType(
      Predicate<String> ruleKindPredicate, SourceType... sourceTypes) {
    return pathListFromSourceFileLabelsOnly(sourcesByRuleKindAndType(ruleKindPredicate, sourceTypes));
  }

  private ImmutableSet<Label> sourcesByRuleKindAndType(
      Predicate<String> ruleKindPredicate, SourceType... sourceTypes) {
    return targetMap().values().stream()
        .filter(t -> ruleKindPredicate.test(t.kind()))
        .map(ProjectTarget::sourceLabels)
        .flatMap(srcs -> stream(sourceTypes).map(srcs::get))
        .flatMap(Set::stream)
        .collect(toImmutableSet());
  }

  private List<Path> pathListFromSourceFileLabelsOnly(Collection<Label> labels) {
    return labels.stream().filter(sourceFileLabels()::contains).map(Label::toFilePath).collect(toImmutableList());
  }

  /**
   * Returns a list of regular (java/kt) source files owned by an Android target, relative to the
   * workspace root.
   */
  public List<Path> getAndroidSourceFiles() {
    return getSourceFilesByRuleKindAndType(RuleKinds::isAndroid, SourceType.REGULAR);
  }

  public List<Path> getAndroidResourceFiles() {
    return getSourceFilesByRuleKindAndType(RuleKinds::isAndroid, SourceType.ANDROID_RESOURCES);
  }

  /** Returns a list of custom_package fields that used by current project. */
  public ImmutableSet<String> getAllCustomPackages() {
    return targetMap().values().stream()
        .map(ProjectTarget::customPackage)
        .flatMap(Optional::stream)
        .collect(toImmutableSet());
  }

  public ImmutableSet<DependencyTrackingBehavior> getDependencyTrackingBehaviors(Label target) {
    final var targetInfo = targetMap().get(target);
    if (targetInfo == null) {
      return ImmutableSet.of();
    }
    return getDependencyTrackingBehaviors(targetInfo);
  }

  private ImmutableSet<DependencyTrackingBehavior> getDependencyTrackingBehaviors(ProjectTarget target) {
    return target.languages().stream()
      .map(l -> l.dependencyTrackingBehavior)
      .collect(toImmutableSet());
  }

  private boolean getDependencyTrackingIncludeExternalDependencies(ProjectTarget target) {
    return target.languages().stream()
      .map(l -> l.dependencyTrackingBehavior)
      .anyMatch(it -> it.shouldIncludeExternalDependencies);
  }

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
  public TargetsToBuild getProjectTargets(Context<?> context, Path workspaceRelativePath) {
    if (workspaceRelativePath.endsWith("BUILD")) {
      Path packagePath = workspaceRelativePath.getParent();
      return TargetsToBuild.targetGroup(allTargets().get(packagePath));
    } else {
      TargetTree targets = allTargets().getSubpackages(workspaceRelativePath);
      if (!targets.isEmpty()) {
        // this will only be non-empty for directories
        return TargetsToBuild.targetGroup(targets);
      }
    }
    // Now a build file or a directory containing packages.
    Optional<Label> fileLabel = sourceFileToLabel(workspaceRelativePath);
    if (fileLabel.isPresent()) {
      ImmutableSet<Label> targetOwner = getSourceFileOwners(workspaceRelativePath);
      if (!targetOwner.isEmpty()) {
        return TargetsToBuild.forSourceFile(targetOwner, workspaceRelativePath);
      }
    } else {
      context.output(
          PrintOutput.error("Can't find any supported targets for %s", workspaceRelativePath));
      context.output(
          PrintOutput.error(
              "If this is a newly added supported rule, please re-sync your project."));
      context.setHasWarnings();
    }
    return TargetsToBuild.NONE;
  }

  /**
   * Returns the set of {@link ProjectTarget#languages() target languages} for a set of project
   * targets.
   */
  public ImmutableSet<QuerySyncLanguage> getTargetLanguages(ImmutableSet<Label> targets) {
    return targets.stream()
        .map(targetMap()::get)
        .map(ProjectTarget::languages)
        .reduce((a, b) -> Sets.union(a, b).immutableCopy())
        .orElse(ImmutableSet.of());
  }

  /**
   * Calculates the {@link RequestedTargets} for a project target.
   *
   * @return Requested targets. The {@link RequestedTargets#buildTargets} will match the parameter
   *     given; the {@link RequestedTargets#expectedDependencyTargets} will be determined by the
   *     {@link #getDependencyTrackingBehaviors(Label)} of the targets given.
   */
  public Optional<RequestedTargets> computeRequestedTargets(Collection<Label> projectTargets) {
    final var externalDeps = new LinkedHashSet<Label>();
    final var seen = new HashSet<>(projectTargets);
    final var queue = new ArrayDeque<Label>(projectTargets);
    while (!queue.isEmpty()) {
      final var target = queue.remove();
      final var targetInfo = targetMap().get(target);
      if (targetInfo == null) {
        // External dependency.
        externalDeps.add(target);
        continue;
      }
      final var dependencyTracking = getDependencyTrackingIncludeExternalDependencies(targetInfo);
      if (dependencyTracking) {
        queue.addAll(targetInfo.deps().stream().filter(seen::add).toList());
      }
    }
    return Optional.of(new RequestedTargets(ImmutableSet.copyOf(projectTargets), ImmutableSet.copyOf(externalDeps)));
  }
}
