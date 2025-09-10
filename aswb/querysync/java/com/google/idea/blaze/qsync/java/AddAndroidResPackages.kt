/*
 * Copyright 2023 The Bazel Authors. All rights reserved.
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
package com.google.idea.blaze.qsync.java

import com.google.idea.blaze.common.Context
import com.google.idea.blaze.exception.BuildException
import com.google.idea.blaze.qsync.deps.ArtifactTracker
import com.google.idea.blaze.qsync.deps.ProjectProtoUpdate
import com.google.idea.blaze.qsync.deps.ProjectProtoUpdateOperation
import kotlin.jvm.optionals.getOrNull

/**
 * Updates the project proto with the android resources packages extracted by the aspect in a
 * dependencies build.
 */
class AddAndroidResPackages : ProjectProtoUpdateOperation {
  @Throws(BuildException::class)
  override fun update(
    update: ProjectProtoUpdate,
    artifactState: ArtifactTracker.State,
    context: Context<*>
  ) {
    update
      .workspaceModule()
      .addAllAndroidSourcePackages(
        artifactState.targets()
          .mapNotNull {
            it.javaInfo()
              .getOrNull()
              ?.androidResourcesPackage()
              ?.takeUnless(String::isEmpty)
          }
          .distinct()
      )
  }
}
