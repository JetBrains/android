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
package com.google.idea.blaze.base.run.testmap;

import com.google.common.collect.ArrayListMultimap;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMultimap;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Multimap;
import com.google.common.collect.Queues;
import com.google.common.collect.Sets;
import com.google.idea.blaze.base.ideinfo.TargetIdeInfo;
import com.google.idea.blaze.base.ideinfo.TargetKey;
import com.google.idea.blaze.base.ideinfo.TargetMap;
import com.google.idea.blaze.base.model.BlazeProjectData;
import com.google.idea.blaze.base.sync.data.BlazeProjectDataManager;
import com.google.idea.blaze.base.sync.workspace.ArtifactLocationDecoder;
import com.google.idea.blaze.base.targetmaps.ReverseDependencyMap;
import com.intellij.openapi.project.Project;
import java.io.File;
import java.util.Collection;
import java.util.Objects;
import java.util.Queue;
import java.util.Set;
import java.util.function.Predicate;

/** Filters a {@link TargetMap} according to a given filter. */
public class FilteredTargetMap {

  private final Project project;
  private final Multimap<File, TargetKey> rootsMap;
  private final TargetMap targetMap;
  private final Predicate<TargetIdeInfo> filter;

  public FilteredTargetMap(
      Project project,
      ArtifactLocationDecoder decoder,
      TargetMap targetMap,
      Predicate<TargetIdeInfo> filter) {
    this.project = project;
    this.rootsMap = createRootsMap(decoder, targetMap.targets());
    this.targetMap = targetMap;
    this.filter = filter;
  }

  public ImmutableSet<TargetIdeInfo> targetsForSourceFile(File sourceFile) {
    return targetsForSourceFiles(ImmutableList.of(sourceFile));
  }

  public ImmutableSet<TargetIdeInfo> targetsForSourceFiles(Collection<File> sourceFiles) {
    BlazeProjectData blazeProjectData =
        BlazeProjectDataManager.getInstance(project).getBlazeProjectData();
    if (blazeProjectData != null) {
      return targetsForSourceFilesImpl(ReverseDependencyMap.get(project), sourceFiles);
    }
    return ImmutableSet.of();
  }

  private ImmutableSet<TargetIdeInfo> targetsForSourceFilesImpl(
      ImmutableMultimap<TargetKey, TargetKey> rdepsMap, Collection<File> sourceFiles) {
    ImmutableSet.Builder<TargetIdeInfo> result = ImmutableSet.builder();
    Set<TargetKey> roots =
        sourceFiles.stream()
            .flatMap(f -> rootsMap.get(f).stream())
            .collect(ImmutableSet.toImmutableSet());

    Queue<TargetKey> todo = Queues.newArrayDeque();
    todo.addAll(roots);
    Set<TargetKey> seen = Sets.newHashSet();
    while (!todo.isEmpty()) {
      TargetKey targetKey = todo.remove();
      if (!seen.add(targetKey)) {
        continue;
      }

      TargetIdeInfo target = targetMap.get(targetKey);
      if (filter.test(target)) {
        result.add(target);
      }
      todo.addAll(rdepsMap.get(targetKey));
    }
    return result.build();
  }

  private static Multimap<File, TargetKey> createRootsMap(
      ArtifactLocationDecoder decoder, Collection<TargetIdeInfo> targets) {
    Multimap<File, TargetKey> result = ArrayListMultimap.create();
    for (TargetIdeInfo target : targets) {
      target.getSources().stream()
          .map(decoder::resolveSource)
          .filter(Objects::nonNull)
          .forEach(f -> result.put(f, target.getKey()));
    }
    return result;
  }
}
