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

import com.google.idea.blaze.base.scope.BlazeContext;
import com.google.idea.blaze.base.sync.projectview.ImportRoots;
import com.google.idea.blaze.base.sync.workspace.WorkspacePathResolver;
import com.intellij.openapi.extensions.ExtensionPointName;
import com.intellij.openapi.project.Project;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import javax.annotation.Nullable;

/**
 * Maps directories to the appropriate blaze target(s) under those directories, recursively.
 *
 * <p>Here 'appropriate' means for the purposes of syncing source files in the given directories.
 */
public interface DirectoryToTargetProvider {

  ExtensionPointName<DirectoryToTargetProvider> EP_NAME =
      ExtensionPointName.create("com.google.idea.blaze.DirectoryToTargetProvider");

  static boolean hasProvider() {
    return EP_NAME.getExtensions().length != 0;
  }

  /**
   * Synchronously query the blaze targets building the directories specified by the given {@link
   * ImportRoots}. Returns null if no provider was able to query the blaze targets.
   */
  @Nullable
  static List<TargetInfo> expandDirectoryTargets(
      Project project,
      ImportRoots directories,
      WorkspacePathResolver pathResolver,
      BlazeContext context) {
    return Arrays.stream(EP_NAME.getExtensions())
        .map(p -> p.doExpandDirectoryTargets(project, directories, pathResolver, context))
        .filter(Objects::nonNull)
        .findFirst()
        .orElse(null);
  }

  /**
   * Synchronously query the blaze targets building the directories specified by the given {@link
   * ImportRoots}. Returns null if this provider was unable to query the blaze targets.
   */
  @Nullable
  List<TargetInfo> doExpandDirectoryTargets(
      Project project,
      ImportRoots directories,
      WorkspacePathResolver pathResolver,
      BlazeContext context);
}
