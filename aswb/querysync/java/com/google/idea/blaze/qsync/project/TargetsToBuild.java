/*
 * Copyright 2023 The Bazel Authors. All rights reserved.
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

import static com.google.common.collect.ImmutableSet.toImmutableSet;
import static java.util.function.Predicate.not;

import com.google.auto.value.AutoValue;
import com.google.common.collect.ImmutableSet;
import com.google.idea.blaze.common.Label;
import java.nio.file.Path;
import java.util.Collection;
import java.util.Collections;
import java.util.Optional;
import java.util.Set;

/** The result of resolving a source file or directory to build targets. */
@AutoValue
public abstract class TargetsToBuild {

  /** The type of this target set, determines the semantics of how it should be used. */
  public enum Type {
    /**
     * A group of targets, all of which should be built. This is used for directories and build
     * files.
     */
    TARGET_GROUP,
    /**
     * Target(s) relating to a source file; just one of the included targets needs to be built. This
     * is used for regular source files, and the associated {@link #targets()} represent all build
     * rules that use that file as source.
     */
    SOURCE_FILE
  }

  public static final TargetsToBuild NONE =
      new AutoValue_TargetsToBuild(Type.TARGET_GROUP, ImmutableSet.of(), Optional.empty());

  public abstract Type type();

  public abstract ImmutableSet<Label> targets();

  /**
   * Workspace-relative path of the source file the targets are build for. Only defined for
   * instances of type SOURCE_FILE.
   */
  public abstract Optional<Path> sourceFilePath();

  public boolean isEmpty() {
    return targets().isEmpty();
  }

  /**
   * Indicates if this set of targets to build is ambiguous.
   *
   * @return {code true} if the targets derive from a source file, and that source file is built by
   *     more than one target.
   */
  public boolean isAmbiguous() {
    return type() == Type.SOURCE_FILE && targets().size() > 1;
  }

  public Optional<ImmutableSet<Label>> getUnambiguousTargets() {
    return isAmbiguous() ? Optional.empty() : Optional.of(targets());
  }

  /** Returns true if {@code labels} overlaps with any of the targets to build */
  public boolean overlapsWith(ImmutableSet<Label> labels) {
    return !Collections.disjoint(targets(), labels);
  }

  public static TargetsToBuild targetGroup(Collection<Label> targets) {
    return new AutoValue_TargetsToBuild(
        Type.TARGET_GROUP, ImmutableSet.copyOf(targets), Optional.empty());
  }

  public static TargetsToBuild forSourceFile(
      Collection<Label> targets, Path workspaceRelativePath) {
    return new AutoValue_TargetsToBuild(
        Type.SOURCE_FILE, ImmutableSet.copyOf(targets), Optional.of(workspaceRelativePath));
  }

  public static ImmutableSet<Label> getAllUnambiguous(Collection<TargetsToBuild> targetsSet) {
    return targetsSet.stream()
        .filter(not(TargetsToBuild::isAmbiguous))
        .map(TargetsToBuild::targets)
        .flatMap(Set::stream)
        .collect(toImmutableSet());
  }

  public static ImmutableSet<TargetsToBuild> getAllAmbiguous(
      Collection<TargetsToBuild> targetsSet) {
    return targetsSet.stream().filter(TargetsToBuild::isAmbiguous).collect(toImmutableSet());
  }
}
