/*
 * Copyright 2019 The Bazel Authors. All rights reserved.
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
package com.google.idea.blaze.base.sync.projectview;

import static com.google.common.collect.ImmutableList.toImmutableList;

import com.google.common.collect.ImmutableList;
import com.google.idea.blaze.base.model.primitives.Label;
import com.google.idea.blaze.base.model.primitives.TargetExpression;
import com.google.idea.blaze.base.model.primitives.WildcardTargetPattern;
import com.google.idea.blaze.base.model.primitives.WorkspacePath;
import com.google.idea.blaze.base.sync.projectview.ImportRoots.ProjectDirectoriesHelper;
import java.util.ArrayList;
import java.util.List;
import javax.annotation.Nullable;

/**
 * Identifies targets/sources covered by an ordered list of {@link TargetExpression}.
 *
 * <p>Handles a mixture of included and excluded target expressions.
 */
public final class TargetExpressionList {

  public static TargetExpressionList create(List<TargetExpression> targets) {
    return new TargetExpressionList(
        targets.stream().map(TargetData::new).collect(toImmutableList()), null);
  }

  static TargetExpressionList createWithTargetsDerivedFromDirectories(
      List<TargetExpression> targets, ProjectDirectoriesHelper directories) {
    return new TargetExpressionList(
        targets.stream().map(TargetData::new).collect(toImmutableList()), directories);
  }

  /**
   * The list of the project targets, in the reverse order to which they're passed to blaze, since
   * later target expressions override earlier ones.
   */
  private final ImmutableList<TargetData> reversedTargets;

  /** Non-null if we're auto-including targets derived from the project directories. */
  @Nullable private final ProjectDirectoriesHelper directories;

  private TargetExpressionList(
      ImmutableList<TargetData> projectTargets, @Nullable ProjectDirectoriesHelper directories) {
    // reverse list, removing trivially-excluded targets
    List<TargetData> excluded = new ArrayList<>();
    ImmutableList.Builder<TargetData> builder = ImmutableList.builder();
    for (TargetData target : projectTargets.reverse()) {
      if (target.isExcluded()) {
        excluded.add(target);
        builder.add(target);
        continue;
      }
      boolean drop = excluded.stream().anyMatch(excl -> excl.coversTargetData(target));
      if (!drop) {
        builder.add(target);
      }
    }
    this.reversedTargets = builder.build();
    this.directories = directories;
  }

  /** Returns the original list of targets with trivially-excluded targets removed. */
  public ImmutableList<TargetExpression> getTargets() {
    return reversedTargets.reverse().stream()
        .map(t -> t.originalExpression)
        .collect(toImmutableList());
  }

  /** Returns true if the entire package is covered by the target expressions. */
  public boolean includesPackage(WorkspacePath packagePath) {
    // the last target expression to cover this label overrides all previous expressions
    for (TargetData target : reversedTargets) {
      if (target.coversPackage(packagePath)) {
        return !target.isExcluded();
      }
    }
    return directories != null && directories.containsWorkspacePath(packagePath);
  }

  /** Returns true if any target in the package is covered by these target expressions. */
  public boolean includesAnyTargetInPackage(WorkspacePath packagePath) {
    // first check if the entire package is included/excluded
    for (TargetData target : reversedTargets) {
      if (target.coversPackage(packagePath)) {
        return !target.isExcluded();
      }
    }
    if (directories != null && directories.containsWorkspacePath(packagePath)) {
      return true;
    }
    // fall back to looking for any unexcluded expression including a target in this package
    for (TargetData target : reversedTargets) {
      if (!target.isExcluded() && target.inPackage(packagePath)) {
        return true;
      }
    }
    return false;
  }

  /** Returns true if the individual target is covered by this list. */
  public boolean includesTarget(Label label) {
    // the last target expression to cover this label overrides all previous expressions
    for (TargetData target : reversedTargets) {
      if (target.coversTarget(label)) {
        return !target.isExcluded();
      }
    }
    return directories != null && directories.containsWorkspacePath(label.blazePackage());
  }

  /** A single {@link TargetExpression} and associated information. */
  private static class TargetData {
    private final TargetExpression originalExpression;
    private final TargetExpression unexcludedExpression;
    @Nullable private final WildcardTargetPattern wildcardPattern;

    TargetData(TargetExpression expression) {
      this.originalExpression = expression;
      this.unexcludedExpression =
          expression.isExcluded()
              ? TargetExpression.fromStringSafe(expression.toString().substring(1))
              : expression;
      this.wildcardPattern = WildcardTargetPattern.fromExpression(expression);
    }

    boolean isExcluded() {
      return originalExpression.isExcluded();
    }

    boolean coversTarget(Label label) {
      return label.equals(unexcludedExpression) || coversPackage(label.blazePackage());
    }

    /** Returns true if the entire package is covered by this expression. */
    boolean coversPackage(WorkspacePath path) {
      return wildcardPattern != null && wildcardPattern.coversPackage(path);
    }

    boolean coversTargetData(TargetData data) {
      if (data.wildcardPattern == null) {
        return data.unexcludedExpression instanceof Label
            && coversTarget(((Label) data.unexcludedExpression));
      }
      if (wildcardPattern == null) {
        return false;
      }
      return data.wildcardPattern.isRecursive()
          ? wildcardPattern.isRecursive()
              && wildcardPattern.coversPackage(data.wildcardPattern.getBasePackage())
          : wildcardPattern.coversPackage(data.wildcardPattern.getBasePackage());
    }

    boolean inPackage(WorkspacePath path) {
      if (coversPackage(path)) {
        return true;
      }
      return unexcludedExpression instanceof Label
          && ((Label) unexcludedExpression).blazePackage().equals(path);
    }
  }
}
