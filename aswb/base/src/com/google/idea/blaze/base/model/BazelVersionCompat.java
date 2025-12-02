/*
 * Copyright 2025 The Bazel Authors. All rights reserved.
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
package com.google.idea.blaze.base.model;

public class BazelVersionCompat {

  @FunctionalInterface
  public interface VersionPredicate {
    boolean isAtLeast(int major, int minor, int bugfix);
  }

  private final VersionPredicate isAtLeastVersion;

  public BazelVersionCompat(VersionPredicate isAtLeastVersion) {
    this.isAtLeastVersion = isAtLeastVersion;
  }

  public String getAspectPrefix() {
    if (isAtLeastVersion.isAtLeast(6, 0, 0) && !isAtLeastVersion.isAtLeast(8, 0, 0)) {
      return "@@";
    }
    return "@";
  }

  public String getRepositoryOverrideFlag() {
    return isAtLeastVersion.isAtLeast(8, 0, 0)
        ? "--inject_repository"
        : "--override_repository";
  }

  public String makeInjectRepositoryFlag(String repository, String path) {
    return String.format("%s=%s=%s", getRepositoryOverrideFlag(), repository, path);
  }

  public String makeAspectFromInjectedRepositoryFlag(String repository, String aspect) {
    return String.format("--aspects=%s%s//:%s", getAspectPrefix(), repository, aspect);
  }
}
