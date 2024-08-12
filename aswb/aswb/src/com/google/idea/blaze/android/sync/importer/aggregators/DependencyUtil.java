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
package com.google.idea.blaze.android.sync.importer.aggregators;

import static java.util.stream.Collectors.toList;

import com.google.common.collect.Lists;
import com.google.devtools.intellij.ideinfo.IntellijIdeInfo.Dependency.DependencyType;
import com.google.idea.blaze.base.ideinfo.AndroidIdeInfo;
import com.google.idea.blaze.base.ideinfo.Dependency;
import com.google.idea.blaze.base.ideinfo.TargetIdeInfo;
import com.google.idea.blaze.base.ideinfo.TargetKey;
import java.util.List;
import org.jetbrains.annotations.NotNull;

/** Util class provide helper function to get dependencies of target. */
public class DependencyUtil {
  @NotNull
  public static List<TargetKey> getResourceDependencies(TargetIdeInfo target) {
    List<TargetKey> regularDependencies = getCompileDependencies(target);

    AndroidIdeInfo androidIdeInfo = target.getAndroidIdeInfo();
    if (androidIdeInfo != null && androidIdeInfo.getLegacyResources() != null) {
      List<TargetKey> result = Lists.newArrayList(regularDependencies);
      result.add(TargetKey.forPlainTarget(androidIdeInfo.getLegacyResources()));
      return result;
    }
    return regularDependencies;
  }

  @NotNull
  public static List<TargetKey> getCompileDependencies(TargetIdeInfo target) {
    return target.getDependencies().stream()
        .filter(dep -> dep.getDependencyType() == DependencyType.COMPILE_TIME)
        .map(Dependency::getTargetKey)
        .collect(toList());
  }
}
