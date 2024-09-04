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
package com.google.idea.blaze.base.command.buildresult;

import com.google.errorprone.annotations.MustBeClosed;
import com.google.idea.blaze.base.bazel.BuildSystem.BuildInvoker;
import com.intellij.openapi.extensions.ExtensionPointName;
import com.intellij.openapi.project.Project;
import java.util.Optional;

/**
 * Determines which {@link BuildResultHelper} to use for the current project.
 *
 * @deprecated Use {@link BuildInvoker#createBuildResultProvider()} instead which will create a
 *     result helper appropriate for that build invoker.
 */
@Deprecated
public interface BuildResultHelperProvider {

  ExtensionPointName<BuildResultHelperProvider> EP_NAME =
      ExtensionPointName.create("com.google.idea.blaze.BuildResultHelperProvider");

  /**
   * Constructs a BuildResultHelper that supports a local BEP and artifacts. This is required
   * because parts of Blaze Plugin implicitly depended on a {@link BuildResultHelper} corresponding
   * to local builds.
   *
   * @deprecated All consumers should be migrated to use {@link
   *     com.google.idea.blaze.base.bazel.BuildSystem#getBuildInvoker(Project)} and {handle local or
   *     {@link BuildInvoker#createBuildResultProvider()}.
   */
  @Deprecated
  Optional<BuildResultHelper> doCreateForLocalBuild(Project project);

  /**
   * Constructs a new build result helper for local builds.
   *
   * @deprecated Use {@link BuildInvoker#createBuildResultProvider()} instead which will create a
   *     result helper appropriate for that build invoker.
   */
  @Deprecated
  @MustBeClosed
  static BuildResultHelper createForLocalBuild(Project project) {
    for (BuildResultHelperProvider extension : EP_NAME.getExtensions()) {
      Optional<BuildResultHelper> helper = extension.doCreateForLocalBuild(project);
      if (helper.isPresent()) {
        return helper.get();
      }
    }
    return new BuildResultHelperBep();
  }
}
