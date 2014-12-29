/*
 * Copyright (C) 2014 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.android.tools.idea.gradle.project;

import com.android.SdkConstants;
import com.android.tools.idea.gradle.parser.Dependency;
import com.android.tools.idea.gradle.parser.GradleBuildFile;
import com.google.common.base.Function;
import com.google.common.cache.CacheBuilder;
import com.google.common.cache.CacheLoader;
import com.google.common.collect.Iterables;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.containers.HashSet;
import org.jetbrains.annotations.NotNull;

import java.util.Collections;
import java.util.Set;

/**
 * Parses list of dependencies from the build.gradle
 */
public class GradleProjectDependencyParser {
  @NotNull
  private static Set<String> parse(VirtualFile moduleRoot, Project project) {
    VirtualFile buildGradle = moduleRoot.findChild(SdkConstants.FN_BUILD_GRADLE);
    if (buildGradle == null) {
      return Collections.emptySet();
    }
    else {
      Set<String> result = new HashSet<String>();
      GradleBuildFile buildFile = new GradleBuildFile(buildGradle, project);
      for (Dependency dependency : Iterables.filter(buildFile.getDependencies(), Dependency.class)) {
        if (dependency.type == Dependency.Type.MODULE) {
          String moduleName = dependency.getValueAsString();
          result.add(moduleName);
        }
      }
      return result;
    }
  }

  public static Function<VirtualFile, Iterable<String>> newInstance(@NotNull final Project project) {
    return CacheBuilder.newBuilder().build(new CacheLoader<VirtualFile, Iterable<String>>() {
      @Override
      public Iterable<String> load(@NotNull VirtualFile key) throws Exception {
        return parse(key, project);
      }
    });
  }
}
