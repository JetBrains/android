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
package com.google.idea.blaze.base.dependencies;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.devtools.intellij.ideinfo.IntellijIdeInfo.Dependency.DependencyType;
import com.google.idea.blaze.base.ideinfo.Dependency;
import com.google.idea.blaze.base.ideinfo.TargetIdeInfo;
import com.google.idea.blaze.base.ideinfo.TargetKey;
import com.google.idea.blaze.base.model.BlazeProjectData;
import com.google.idea.blaze.base.model.primitives.Label;
import com.google.idea.blaze.base.sync.data.BlazeProjectDataManager;
import com.intellij.openapi.project.Project;
import java.util.Objects;
import javax.annotation.Nullable;

/** Utility class to find the dependencies of Blaze targets. */
public class DependencyFinder {

  /**
   * Returns the list of compile-time dependencies for the given target. Returns {@code null} if the
   * target cannot be found.
   */
  @Nullable
  public static ImmutableList<TargetInfo> getCompileTimeDependencyTargets(
      Project project, Label target) {
    ImmutableMap<TargetKey, TargetIdeInfo> targetMap = getTargetMap(project);
    TargetIdeInfo ideInfo = targetMap.get(TargetKey.forPlainTarget(target));
    if (ideInfo == null) {
      return null;
    }
    return ideInfo.getDependencies().stream()
        .filter(dependency -> DependencyType.COMPILE_TIME.equals(dependency.getDependencyType()))
        .map(dependency -> createTargetInfo(dependency, targetMap))
        .filter(Objects::nonNull)
        .collect(ImmutableList.toImmutableList());
  }

  private static ImmutableMap<TargetKey, TargetIdeInfo> getTargetMap(Project project) {
    BlazeProjectData projectData =
        BlazeProjectDataManager.getInstance(project).getBlazeProjectData();
    return projectData != null ? projectData.getTargetMap().map() : ImmutableMap.of();
  }

  @Nullable
  private static TargetInfo createTargetInfo(
      Dependency dependency, ImmutableMap<TargetKey, TargetIdeInfo> targetMap) {
    TargetKey key = dependency.getTargetKey();
    TargetIdeInfo ideInfo = targetMap.get(key);
    return ideInfo != null ? ideInfo.toTargetInfo() : null;
  }

  private DependencyFinder() {}
}
