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
package com.google.idea.blaze.java.sync;

import com.google.common.collect.ArrayListMultimap;
import com.google.common.collect.Lists;
import com.google.common.collect.Multimap;
import com.google.common.collect.Sets;
import com.google.idea.blaze.base.ideinfo.ArtifactLocation;
import com.google.idea.blaze.base.ideinfo.TargetKey;
import com.google.idea.blaze.base.scope.BlazeContext;
import com.google.idea.blaze.base.scope.output.PerformanceWarning;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.Set;

/** Detects and reports duplicate sources */
public class DuplicateSourceDetector {
  Multimap<ArtifactLocation, TargetKey> artifacts = ArrayListMultimap.create();

  public void add(TargetKey targetKey, ArtifactLocation artifactLocation) {
    artifacts.put(artifactLocation, targetKey);
  }

  static class Duplicate {
    final ArtifactLocation artifactLocation;
    final Collection<TargetKey> targets;

    public Duplicate(ArtifactLocation artifactLocation, Collection<TargetKey> targets) {
      this.artifactLocation = artifactLocation;
      this.targets = targets;
    }
  }

  public void reportDuplicates(BlazeContext context) {
    List<Duplicate> duplicates = Lists.newArrayList();
    for (ArtifactLocation key : artifacts.keySet()) {
      Collection<TargetKey> labels = artifacts.get(key);
      if (labels.size() > 1) {

        // Workaround for aspect bug. Can be removed after the next blaze release, as of May 27 2016
        Set<TargetKey> labelSet = Sets.newHashSet(labels);
        if (labelSet.size() > 1) {
          duplicates.add(new Duplicate(key, labelSet));
        }
      }
    }

    if (duplicates.isEmpty()) {
      return;
    }

    duplicates.sort(Comparator.comparing(lhs -> lhs.artifactLocation.getRelativePath()));

    context.output(new PerformanceWarning("Duplicate sources detected:"));
    for (Duplicate duplicate : duplicates) {
      ArtifactLocation artifactLocation = duplicate.artifactLocation;
      context.output(new PerformanceWarning("  Source: " + artifactLocation.getRelativePath()));
      context.output(new PerformanceWarning("  Consumed by rules:"));
      for (TargetKey targetKey : duplicate.targets) {
        context.output(new PerformanceWarning("    " + targetKey.getLabel()));
      }
      context.output(new PerformanceWarning("")); // Newline
    }
  }
}
