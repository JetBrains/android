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
package com.google.idea.blaze.base.qsync;

import com.google.idea.blaze.base.scope.BlazeContext;
import com.google.idea.blaze.common.vcs.VcsState;
import com.google.idea.blaze.exception.BuildException;
import com.google.idea.blaze.qsync.project.PostQuerySyncData;
import com.google.idea.blaze.qsync.project.ProjectDefinition;
import java.io.IOException;
import java.util.Optional;

/**
 * A query sync service that knows how to issue and run project structure queries over the bazel
 * project.
 */
public interface ProjectQuerier {

  PostQuerySyncData fullQuery(ProjectDefinition projectDef, BlazeContext context)
      throws IOException, BuildException;

  PostQuerySyncData update(
      ProjectDefinition currentProjectDef, PostQuerySyncData previousState, BlazeContext context)
      throws IOException, BuildException;

  // TODO(b/308807019): Move vcs calculation out of ProjectQuerier
  Optional<VcsState> getVcsState(BlazeContext context);
}
