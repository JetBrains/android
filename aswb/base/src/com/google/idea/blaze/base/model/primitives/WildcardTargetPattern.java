/*
 * Copyright 2017 The Bazel Authors. All rights reserved.
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
package com.google.idea.blaze.base.model.primitives;

import com.google.common.collect.ImmutableList;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.util.text.StringUtil;
import javax.annotation.Nullable;

/** A blaze wildcard target pattern. */
public class WildcardTargetPattern {

  private static final String ALL_PACKAGES_RECURSIVE_SUFFIX = "/...";
  private static final ImmutableList<String> ALL_TARGETS_IN_SUFFIXES =
      ImmutableList.of("*", "all-targets");
  private static final String ALL_RULES_IN_SUFFIX = "all";

  /**
   * Strip any wildcard suffix from the given target pattern, returning a non-wildcard pattern. This
   * is used to validate general target expressions.
   */
  public static String stripWildcardSuffix(String pattern) {
    if (pattern.endsWith(":all")) {
      pattern = pattern.substring(0, pattern.length() - ":all".length());
    } else if (pattern.endsWith(":*")) {
      pattern = pattern.substring(0, pattern.length() - ":*".length());
    } else if (pattern.endsWith(":all-targets")) {
      pattern = pattern.substring(0, pattern.length() - ":all-targets".length());
    }
    return pattern.equals("//...")
        ? ""
        : StringUtil.trimEnd(pattern, ALL_PACKAGES_RECURSIVE_SUFFIX);
  }

  /** Returns null if the target is not a valid wildcard target pattern. */
  @Nullable
  public static WildcardTargetPattern fromExpression(TargetExpression target) {
    String pattern = target.toString();
    int colonIndex = pattern.lastIndexOf(':');
    String packagePart = colonIndex < 0 ? pattern : pattern.substring(0, colonIndex);
    String targetPart = colonIndex < 0 ? "" : pattern.substring(colonIndex + 1);

    if (packagePart.startsWith("-")) {
      packagePart = packagePart.substring(1);
    }
    packagePart = StringUtil.trimStart(packagePart, "//");

    if (packagePart.endsWith(ALL_PACKAGES_RECURSIVE_SUFFIX)) {
      WorkspacePath basePackageDir =
          WorkspacePath.createIfValid(
              StringUtil.trimEnd(packagePart, ALL_PACKAGES_RECURSIVE_SUFFIX));
      if (basePackageDir == null) {
        return null;
      }
      if (targetPart.isEmpty() || targetPart.equals(ALL_RULES_IN_SUFFIX)) {
        return new WildcardTargetPattern(target, basePackageDir, true, true);
      }
      if (ALL_TARGETS_IN_SUFFIXES.contains(targetPart)) {
        return new WildcardTargetPattern(target, basePackageDir, true, false);
      }
      return null; // ignore invalid patterns -- blaze will give us a better error later.
    }

    WorkspacePath packageDir = WorkspacePath.createIfValid(packagePart);
    if (packageDir == null) {
      return null;
    }
    if (targetPart.equals(ALL_RULES_IN_SUFFIX)) {
      return new WildcardTargetPattern(target, packageDir, false, true);
    }
    if (ALL_TARGETS_IN_SUFFIXES.contains(targetPart)) {
      return new WildcardTargetPattern(target, packageDir, false, false);
    }
    // not a wildcard target pattern
    return null;
  }

  public final TargetExpression originalPattern;
  private final WorkspacePath packageDir;
  private final boolean recursive;
  private final boolean rulesOnly;

  private WildcardTargetPattern(
      TargetExpression originalPattern,
      WorkspacePath packageDir,
      boolean recursive,
      boolean rulesOnly) {
    this.originalPattern = originalPattern;
    this.packageDir = packageDir;
    this.recursive = recursive;
    this.rulesOnly = rulesOnly;
  }

  /** The base blaze package this target pattern refers to */
  public WorkspacePath getBasePackage() {
    return packageDir;
  }

  /** Whether the target pattern includes all packages below the base package. */
  public boolean isRecursive() {
    return recursive;
  }

  /** Whether the target pattern includes all targets, or only rules */
  public boolean rulesOnly() {
    return rulesOnly;
  }

  public boolean coversPackage(WorkspacePath packagePath) {
    if (!recursive) {
      return packagePath.equals(packageDir);
    }
    return FileUtil.isAncestor(packageDir.relativePath(), packagePath.relativePath(), false);
  }

  /** Is this an excluded target pattern (i.e. starts with '-')? */
  public boolean isExcluded() {
    return originalPattern.isExcluded();
  }

  @Override
  public String toString() {
    return originalPattern.toString();
  }

  @Override
  public boolean equals(Object obj) {
    return obj instanceof WildcardTargetPattern
        && originalPattern.equals(((WildcardTargetPattern) obj).originalPattern);
  }

  @Override
  public int hashCode() {
    return originalPattern.hashCode();
  }
}
