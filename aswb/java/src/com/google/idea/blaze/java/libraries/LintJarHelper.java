/*
 * Copyright 2022 The Bazel Authors. All rights reserved.
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
package com.google.idea.blaze.java.libraries;

import static com.google.common.collect.ImmutableList.toImmutableList;

import com.google.common.collect.ImmutableList;
import com.google.idea.blaze.base.model.BlazeProjectData;
import com.google.idea.blaze.base.sync.workspace.ArtifactLocationDecoder;
import com.google.idea.blaze.common.artifact.BlazeArtifact;
import com.google.idea.blaze.java.sync.model.BlazeJavaSyncData;

/** Utility class for collecting lint jars from {@link BlazeProjectData} */
public final class LintJarHelper {
  public static ImmutableList<BlazeArtifact> collectLintJarsArtifacts(
      BlazeProjectData blazeProjectData) {
    BlazeJavaSyncData syncData = blazeProjectData.getSyncState().get(BlazeJavaSyncData.class);
    ArtifactLocationDecoder artifactLocationDecoder = blazeProjectData.getArtifactLocationDecoder();

    if (syncData == null) {
      return ImmutableList.of();
    }
    return syncData.getImportResult().pluginProcessorJars.stream()
        .map(artifactLocationDecoder::resolveOutput)
        .collect(toImmutableList());
  }

  private LintJarHelper() {}
}
