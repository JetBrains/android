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
package com.google.idea.blaze.base.run.targetfinder;

import com.google.common.util.concurrent.Futures;
import com.google.idea.blaze.base.dependencies.TargetInfo;
import com.google.idea.blaze.base.ideinfo.TargetMap;
import com.google.idea.blaze.base.model.BlazeProjectData;
import com.google.idea.blaze.base.model.primitives.Label;
import com.google.idea.blaze.base.sync.data.BlazeProjectDataManager;
import com.google.idea.blaze.common.BuildTarget;
import com.intellij.openapi.project.Project;
import java.util.concurrent.Future;

/** Uses the project's {@link TargetMap} to locate targets matching a given label. */
class ProjectTargetFinder implements TargetFinder {

  @Override
  public Future<TargetInfo> findTarget(Project project, Label label) {
    BlazeProjectData projectData =
        BlazeProjectDataManager.getInstance(project).getBlazeProjectData();
    TargetInfo ret = null;
    if (projectData != null) {
      BuildTarget buildTarget = projectData.getBuildTarget(label);
      ret = buildTarget != null ? TargetInfo.builder(label, buildTarget.kind()).build() : null;
    }
    return Futures.immediateFuture(ret);
  }
}
