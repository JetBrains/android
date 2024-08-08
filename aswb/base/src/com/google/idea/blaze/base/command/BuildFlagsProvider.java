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
package com.google.idea.blaze.base.command;

import com.google.idea.blaze.base.projectview.ProjectViewSet;
import com.google.idea.blaze.base.scope.BlazeContext;
import com.intellij.openapi.extensions.ExtensionPointName;
import com.intellij.openapi.project.Project;
import java.util.List;

/** Provides additional flags for bazel/blaze commands. */
public interface BuildFlagsProvider {

  ExtensionPointName<BuildFlagsProvider> EP_NAME =
      ExtensionPointName.create("com.google.idea.blaze.BuildFlagsProvider");

  /** Flags to add to blaze/bazel invocations of the given type. */
  void addBuildFlags(
      Project project,
      ProjectViewSet projectViewSet,
      BlazeCommandName command,
      BlazeInvocationContext invocationContext,
      List<String> flags);

  /** Flags to add to blaze/bazel sync invocations of the given type. */
  default void addSyncFlags(
      Project project,
      ProjectViewSet projectViewSet,
      BlazeCommandName command,
      BlazeContext context,
      BlazeInvocationContext invocationContext,
      List<String> flags) {}
}
