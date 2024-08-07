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
package com.google.idea.blaze.base.model;

import com.google.common.collect.ImmutableList;
import com.google.idea.blaze.base.command.info.BlazeInfo;
import com.google.idea.blaze.base.dependencies.TargetInfo;
import com.google.idea.blaze.base.ideinfo.TargetMap;
import com.google.idea.blaze.base.model.primitives.Label;
import com.google.idea.blaze.base.sync.projectview.WorkspaceLanguageSettings;
import com.google.idea.blaze.base.sync.workspace.ArtifactLocationDecoder;
import com.google.idea.blaze.base.sync.workspace.WorkspacePathResolver;
import com.google.idea.blaze.common.BuildTarget;
import javax.annotation.Nullable;

/** Interface to persistent project data. */
public interface BlazeProjectData {

  @Nullable
  BuildTarget getBuildTarget(Label label);

  WorkspacePathResolver getWorkspacePathResolver();

  WorkspaceLanguageSettings getWorkspaceLanguageSettings();

  ImmutableList<TargetInfo> targets();

  // TODO: Many of the following methods are aspect-sync specific, and should probably not appear in
  //  this interface.

  TargetMap getTargetMap();

  BlazeInfo getBlazeInfo();

  BlazeVersionData getBlazeVersionData();

  ArtifactLocationDecoder getArtifactLocationDecoder();

  RemoteOutputArtifacts getRemoteOutputs();

  SyncState getSyncState();

  boolean isQuerySync();
}
