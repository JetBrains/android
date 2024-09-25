/*
 * Copyright 2018 The Bazel Authors. All rights reserved.
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
package com.google.idea.blaze.android.sync.importer;

import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Sets;
import com.google.idea.blaze.android.sync.importer.problems.GeneratedResourceRetentionFilter;
import com.google.idea.blaze.base.ideinfo.ArtifactLocation;
import java.util.Set;
import java.util.function.Predicate;

/**
 * Predicate that returns true for artifacts whose relative paths are present in the allowlist. Also
 * records all the {@link ArtifactLocation} objects that have been used as part of the test, which
 * can be used to determine allowlist entries that are no longer needed.
 *
 * <p>Note that any artifacts which pass the {@link GeneratedResourceRetentionFilter} are simply
 * retained without checking them against the allowlist.
 */
public class AllowlistFilter implements Predicate<ArtifactLocation> {
  final Set<ArtifactLocation> testedAgainstAllowlist = Sets.newHashSet();
  private final ImmutableSet<String> allowedPaths;
  private final Predicate<ArtifactLocation> retentionFilter;

  public AllowlistFilter(Set<String> allowedPaths, Predicate<ArtifactLocation> retentionFilter) {
    this.allowedPaths = ImmutableSet.copyOf(allowedPaths);
    this.retentionFilter = retentionFilter;
  }

  @Override
  public boolean test(ArtifactLocation location) {
    if (retentionFilter.test(location)) {
      return true;
    }
    testedAgainstAllowlist.add(location);
    return allowedPaths.contains(location.getRelativePath());
  }
}
