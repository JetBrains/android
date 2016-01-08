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

import com.android.tools.idea.gradle.dsl.model.GradleBuildModel;
import com.android.tools.idea.gradle.dsl.model.dependencies.DependenciesModel;
import com.android.tools.idea.gradle.dsl.model.dependencies.ModuleDependencyModel;
import com.google.common.base.Function;
import com.google.common.cache.CacheBuilder;
import com.google.common.cache.CacheLoader;
import com.google.common.collect.Sets;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.NotNull;

import java.util.Collections;
import java.util.Set;

import static com.android.SdkConstants.FN_BUILD_GRADLE;
import static com.android.tools.idea.gradle.dsl.model.GradleBuildModel.parseBuildFile;

/**
 * Parses list of dependencies from the build.gradle
 */
public class GradleProjectDependencyParser {
  @NotNull
  public static Function<VirtualFile, Iterable<String>> newInstance(@NotNull final Project project) {
    return CacheBuilder.newBuilder().build(new CacheLoader<VirtualFile, Iterable<String>>() {
      @Override
      public Iterable<String> load(@NotNull VirtualFile key) throws Exception {
        return parse(key, project);
      }
    });
  }

  @NotNull
  private static Set<String> parse(@NotNull VirtualFile moduleRoot, @NotNull Project project) {
    VirtualFile buildFile = moduleRoot.findChild(FN_BUILD_GRADLE);
    if (buildFile == null) {
      return Collections.emptySet();
    }
    else {
      Set<String> result = Sets.newHashSet();
      GradleBuildModel buildModel = parseBuildFile(buildFile, project);
      DependenciesModel dependenciesModel = buildModel.dependencies();
      if (dependenciesModel != null) {
        for (ModuleDependencyModel dependency : dependenciesModel.modules()) {
          String modulePath = dependency.path();
          result.add(modulePath);
        }
      }
      return result;
    }
  }
}
